package com.icp.common.audit;

import com.icp.common.security.SecurityUtils;
import com.icp.common.security.UserPrincipal;
import com.icp.system.entity.SysOperLog;
import com.icp.system.mapper.SysOperLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 审计切面：写 sys_oper_log。表不存在或写库失败时降级为告警，绝不阻断业务请求。
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    private final SysOperLogMapper operLogMapper;

    public AuditLogAspect(SysOperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            error = ex;
            throw ex;
        } finally {
            try {
                save(joinPoint, auditLog, System.currentTimeMillis() - start, error);
            } catch (Exception ex) {
                log.warn("audit log skipped: {}", ex.getMessage());
            }
        }
    }

    private void save(ProceedingJoinPoint joinPoint, AuditLog auditLog, long costMs, Throwable error) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();

        SysOperLog operLog = new SysOperLog();
        operLog.setTraceId(MDC.get("traceId"));
        operLog.setTitle(auditLog.action().isEmpty() ? joinPoint.getSignature().getName() : auditLog.action());
        operLog.setBusinessType(auditLog.businessType());
        operLog.setMethod(joinPoint.getSignature().toShortString());
        operLog.setRequestMethod(request.getMethod());
        operLog.setRequestUri(request.getRequestURI());
        operLog.setRequestParam(truncate(buildParams(joinPoint), 2000));
        int httpStatus = response == null ? 200 : response.getStatus();
        boolean failed = error != null || httpStatus >= 500;
        operLog.setStatus(failed ? 1 : 0);
        if (error != null) {
            operLog.setErrorMsg(truncate(error.getMessage(), 2000));
        }
        UserPrincipal principal = SecurityUtils.currentUserOrNull();
        if (principal != null) {
            operLog.setOperUserId(principal.getUserId());
            operLog.setOperName(principal.getUsername());
        }
        operLog.setOperIp(clientIp(request));
        operLog.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
        operLog.setCostMs((int) Math.min(costMs, Integer.MAX_VALUE));
        operLog.setOperTime(LocalDateTime.now());
        try {
            operLogMapper.insert(operLog);
        } catch (Exception ex) {
            log.warn("write sys_oper_log failed: {}", ex.getMessage());
        }
    }

    private String buildParams(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (Object arg : args) {
            if (arg == null || arg instanceof jakarta.servlet.ServletRequest
                    || arg instanceof jakarta.servlet.ServletResponse) {
                continue;
            }
            builder.append(arg.getClass().getSimpleName()).append(".toString()");
            builder.append(',');
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}

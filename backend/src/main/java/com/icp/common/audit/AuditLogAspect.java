package com.icp.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icp.common.security.SecurityUtils;
import com.icp.common.security.UserPrincipal;
import com.icp.system.entity.SysOperLog;
import com.icp.system.mapper.SysOperLogMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 操作审计切面：把请求参数（脱敏后）与结果摘要写入 sys_oper_log。
 * 表不存在或写库失败时降级为告警，绝不阻断业务请求。
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);
    private static final int MAX_PARAM_LENGTH = 2000;
    private static final int MAX_RESULT_LENGTH = 1000;
    private static final Set<String> MASK_KEYS = Set.of(
            "password", "oldpassword", "newpassword", "confirmpassword",
            "secret", "token", "accesstoken", "refreshtoken");

    private final SysOperLogMapper operLogMapper;
    private final ObjectMapper objectMapper;

    public AuditLogAspect(SysOperLogMapper operLogMapper, ObjectMapper objectMapper) {
        this.operLogMapper = operLogMapper;
        this.objectMapper = objectMapper;
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
                save(joinPoint, auditLog, System.currentTimeMillis() - start, error, result);
            } catch (Exception ex) {
                log.warn("audit log skipped: {}", ex.getMessage());
            }
        }
    }

    private void save(ProceedingJoinPoint joinPoint, AuditLog auditLog, long costMs,
                      Throwable error, Object result) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();

        SysOperLog operLog = new SysOperLog();
        operLog.setTraceId(MDC.get("traceId"));
        String auditAction = auditLog.action().isEmpty() ? joinPoint.getSignature().getName() : auditLog.action();
        String auditModule = auditLog.module();
        operLog.setTitle(auditModule == null || auditModule.isBlank() ? auditAction : auditModule + "-" + auditAction);
        operLog.setBusinessType(auditLog.businessType());
        operLog.setMethod(joinPoint.getSignature().toShortString());
        operLog.setRequestMethod(request.getMethod());
        operLog.setRequestUri(request.getRequestURI());
        operLog.setRequestParam(truncate(buildPayload(joinPoint, result), MAX_PARAM_LENGTH));
        int httpStatus = response == null ? 200 : response.getStatus();
        boolean failed = error != null || httpStatus >= 500;
        operLog.setStatus(failed ? 1 : 0);
        if (error != null) {
            operLog.setErrorMsg(truncate(error.getMessage(), MAX_PARAM_LENGTH));
        }
        UserPrincipal principal = SecurityUtils.currentUserOrNull();
        if (principal != null) {
            operLog.setOperUserId(principal.getUserId());
            operLog.setOperName(principal.getUsername());
            operLog.setDeptId(principal.getDeptId());
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

    private String buildPayload(ProceedingJoinPoint joinPoint, Object result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("params", arguments(joinPoint));
        if (result != null) {
            payload.put("result", summarize(result));
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<Object> arguments(ProceedingJoinPoint joinPoint) {
        List<Object> values = new ArrayList<>();
        Object[] args = joinPoint.getArgs();
        if (args == null) {
            return values;
        }
        for (Object arg : args) {
            if (arg == null || arg instanceof ServletRequest || arg instanceof ServletResponse) {
                continue;
            }
            values.add(mask(arg));
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private Object mask(Object arg) {
        try {
            Object converted = objectMapper.convertValue(arg, Object.class);
            if (converted instanceof Map<?, ?> map) {
                Map<String, Object> masked = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    if (MASK_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                        masked.put(key, "******");
                    } else {
                        masked.put(key, entry.getValue());
                    }
                }
                return masked;
            }
            return converted;
        } catch (Exception ex) {
            return arg.getClass().getSimpleName();
        }
    }

    private Object summarize(Object result) {
        try {
            Object converted = objectMapper.convertValue(result, Object.class);
            String json = objectMapper.writeValueAsString(converted);
            if (json.length() > MAX_RESULT_LENGTH) {
                return json.substring(0, MAX_RESULT_LENGTH) + "...";
            }
            return converted;
        } catch (Exception ex) {
            return result.getClass().getSimpleName();
        }
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

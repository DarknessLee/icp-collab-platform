package com.icp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icp.common.api.PageResult;
import com.icp.common.security.SecurityUtils;
import com.icp.system.entity.SysOperLog;
import com.icp.system.mapper.SysOperLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 变更审计：写 sys_oper_log（title=CHANGE / business_type=表名 / request_param={action,bizId,before,after}），
 * 不新增表，符合 M2a 契约；同时提供变更查询。
 */
@Service
public class ChangeLogService {

    public static final String TITLE_CHANGE = "CHANGE";
    private static final Logger log = LoggerFactory.getLogger(ChangeLogService.class);
    private static final int MAX_PARAM_LENGTH = 4000;
    private static final Set<String> MASK_KEYS = Set.of(
            "password", "oldpassword", "newpassword", "confirmpassword",
            "secret", "token", "accesstoken", "refreshtoken");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SysOperLogMapper operLogMapper;
    private final ObjectMapper objectMapper;

    public ChangeLogService(SysOperLogMapper operLogMapper, ObjectMapper objectMapper) {
        this.operLogMapper = operLogMapper;
        this.objectMapper = objectMapper;
    }

    public void record(String table, Long bizId, Object before, Object after) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("action", TITLE_CHANGE);
            payload.put("bizId", bizId);
            payload.put("before", mask(before));
            payload.put("after", mask(after));

            SysOperLog operLog = new SysOperLog();
            operLog.setTitle(TITLE_CHANGE);
            operLog.setBusinessType(table);
            operLog.setMethod("AUDIT");
            operLog.setRequestMethod("AUDIT");
            operLog.setRequestUri("/internal/change-audit");
            operLog.setStatus(0);
            operLog.setOperTime(LocalDateTime.now());
            operLog.setTraceId(org.slf4j.MDC.get("traceId"));
            operLog.setOperUserId(SecurityUtils.currentUserIdOrNull());
            operLog.setOperName(SecurityUtils.currentUserOrNull() == null ? "system"
                    : SecurityUtils.currentUserOrNull().getUsername());
            fillRequestInfo(operLog);
            String json = objectMapper.writeValueAsString(payload);
            operLog.setRequestParam(truncate(json, MAX_PARAM_LENGTH));
            operLogMapper.insert(operLog);
        } catch (Exception ex) {
            // 审计失败不能阻断业务
            log.warn("change audit failed, table={}, bizId={}: {}", table, bizId, ex.getMessage());
        }
    }

    public PageResult<SysOperLog> page(String bizType, Long bizId, String startDate, String endDate,
                                       long pageNum, long pageSize) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<SysOperLog>()
                .eq(SysOperLog::getTitle, TITLE_CHANGE)
                .orderByDesc(SysOperLog::getId);
        if (StringUtils.hasText(bizType)) {
            wrapper.eq(SysOperLog::getBusinessType, bizType.trim());
        }
        if (bizId != null) {
            wrapper.apply("JSON_UNQUOTE(JSON_EXTRACT(request_param, '$.bizId')) = {0}",
                    String.valueOf(bizId));
        }
        applyTimeRange(wrapper, startDate, endDate);
        Page<SysOperLog> page = operLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    private void applyTimeRange(LambdaQueryWrapper<SysOperLog> wrapper, String startDate, String endDate) {
        if (StringUtils.hasText(startDate)) {
            LocalDate start = parseDate(startDate);
            if (start != null) {
                wrapper.ge(SysOperLog::getOperTime, start.atStartOfDay());
            }
        }
        if (StringUtils.hasText(endDate)) {
            LocalDate end = parseDate(endDate);
            if (end != null) {
                wrapper.lt(SysOperLog::getOperTime, end.plusDays(1).atStartOfDay());
            }
        }
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim(), DATE_FMT);
        } catch (Exception ex) {
            return null;
        }
    }

    private void fillRequestInfo(SysOperLog operLog) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        operLog.setOperIp(ip);
        operLog.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
        if (SecurityUtils.currentUserOrNull() != null) {
            Long deptId = SecurityUtils.currentUserOrNull().getDeptId();
            operLog.setDeptId(deptId);
        }
    }

    @SuppressWarnings("unchecked")
    private Object mask(Object value) {
        if (value == null) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.convertValue(value, Map.class);
            Map<String, Object> masked = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
                if (MASK_KEYS.contains(key)) {
                    masked.put(entry.getKey(), "******");
                } else {
                    masked.put(entry.getKey(), entry.getValue());
                }
            }
            return masked;
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}

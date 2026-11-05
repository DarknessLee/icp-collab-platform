package com.icp.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.icp.collab.entity.CollTask;
import com.icp.collab.mapper.CollTaskMapper;
import com.icp.common.api.ErrorCode;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.icp.entity.IcpDefect;
import com.icp.icp.entity.IcpPolicy;
import com.icp.icp.mapper.IcpDefectMapper;
import com.icp.icp.mapper.IcpPolicyMapper;
import com.icp.report.support.ReportScope;
import com.icp.system.service.ConfigService;
import com.icp.workflow.entity.WfBusinessLink;
import com.icp.workflow.mapper.WfBusinessLinkMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * M2e 预警中心：四类预警的查询与计数（D39 实时聚合；消息生成由定时扫描负责，见 JobExecutionService）。
 * 口径（docs/03 §7.1 第 3 条）：
 * 任务逾期 / 制度到期 / 高风险缺陷 / 流程超时。
 * 备注：实际字典 risk_level 1=高、defect.severity 1=重大 → 高风险缺陷取 severity=1。
 */
@Service
public class AlertService {

    public static final String TYPE_TASK_OVERDUE = "task_overdue";
    public static final String TYPE_POLICY_EXPIRING = "policy_expiring";
    public static final String TYPE_DEFECT_HIGH_RISK = "defect_high_risk";
    public static final String TYPE_PROCESS_OVERDUE = "process_overdue";
    private static final Set<String> TYPES = Set.of(TYPE_TASK_OVERDUE, TYPE_POLICY_EXPIRING,
            TYPE_DEFECT_HIGH_RISK, TYPE_PROCESS_OVERDUE);
    private static final String CONFIG_TIMEOUT_HOURS = "workflow.timeout.hours";
    private static final int DEFAULT_TIMEOUT_HOURS = 48;
    private static final int POLICY_EXPIRE_DAYS = 30;

    private final ReportScope scope;
    private final ConfigService configService;
    private final CollTaskMapper taskMapper;
    private final IcpPolicyMapper policyMapper;
    private final IcpDefectMapper defectMapper;
    private final WfBusinessLinkMapper linkMapper;

    public AlertService(ReportScope scope,
                        ConfigService configService,
                        CollTaskMapper taskMapper,
                        IcpPolicyMapper policyMapper,
                        IcpDefectMapper defectMapper,
                        WfBusinessLinkMapper linkMapper) {
        this.scope = scope;
        this.configService = configService;
        this.taskMapper = taskMapper;
        this.policyMapper = policyMapper;
        this.defectMapper = defectMapper;
        this.linkMapper = linkMapper;
    }

    public Map<String, Object> count() {
        Map<String, Long> counts = collectCounts(scope.current());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskOverdue", counts.getOrDefault(TYPE_TASK_OVERDUE, 0L));
        data.put("policyExpiring", counts.getOrDefault(TYPE_POLICY_EXPIRING, 0L));
        data.put("defectHighRisk", counts.getOrDefault(TYPE_DEFECT_HIGH_RISK, 0L));
        data.put("processOverdue", counts.getOrDefault(TYPE_PROCESS_OVERDUE, 0L));
        data.put("total", counts.values().stream().mapToLong(Long::longValue).sum());
        return data;
    }

    public Map<String, Object> page(String type, long pageNum, long pageSize) {
        if (type != null && !type.isBlank() && !TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "type 取值不合法，仅支持 task_overdue/policy_expiring/defect_high_risk/process_overdue");
        }
        DataScopeInfo info = scope.current();
        List<Map<String, Object>> items = new ArrayList<>();
        if (type == null || type.isBlank() || TYPE_TASK_OVERDUE.equals(type)) {
            items.addAll(taskAlerts(info));
        }
        if (type == null || type.isBlank() || TYPE_POLICY_EXPIRING.equals(type)) {
            items.addAll(policyAlerts(info));
        }
        if (type == null || type.isBlank() || TYPE_DEFECT_HIGH_RISK.equals(type)) {
            items.addAll(defectAlerts(info));
        }
        if (type == null || type.isBlank() || TYPE_PROCESS_OVERDUE.equals(type)) {
            items.addAll(processAlerts(info));
        }
        items.sort(Comparator
                .comparingInt((Map<String, Object> row) -> "high".equals(row.get("level")) ? 0 : 1)
                .thenComparing(row -> String.valueOf(row.get("dueTime"))));

        long total = items.size();
        long page = Math.max(pageNum, 1);
        long size = Math.min(Math.max(pageSize, 1), 100);
        int from = (int) Math.min((page - 1) * size, total);
        int to = (int) Math.min(from + size, total);
        List<Map<String, Object>> pageItems = from >= total ? List.of() : items.subList(from, to);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", pageItems);
        data.put("total", total);
        data.put("pageNum", page);
        data.put("pageSize", size);
        return data;
    }

    private Map<String, Long> collectCounts(DataScopeInfo info) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(TYPE_TASK_OVERDUE, (long) taskAlerts(info).size());
        counts.put(TYPE_POLICY_EXPIRING, (long) policyAlerts(info).size());
        counts.put(TYPE_DEFECT_HIGH_RISK, (long) defectAlerts(info).size());
        counts.put(TYPE_PROCESS_OVERDUE, (long) processAlerts(info).size());
        return counts;
    }

    private List<Map<String, Object>> taskAlerts(DataScopeInfo info) {
        LocalDate today = LocalDate.now();
        List<CollTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<CollTask>()
                        .in(CollTask::getStatus, 0, 1)
                        .isNotNull(CollTask::getPlanEnd)
                        .lt(CollTask::getPlanEnd, today)).stream()
                .filter(row -> scope.visibleTask(info, row.getDeptId(), row.getAssigneeId(), row.getCreatorId()))
                .collect(Collectors.toList());
        List<Map<String, Object>> result = new ArrayList<>();
        for (CollTask task : tasks) {
            long overdueDays = Math.max(Duration.between(task.getPlanEnd().atStartOfDay(),
                    LocalDate.now().atStartOfDay()).toDays(), 0L);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", task.getId());
            item.put("type", TYPE_TASK_OVERDUE);
            item.put("level", overdueDays >= 3 ? "high" : "medium");
            item.put("title", "任务逾期：" + task.getTaskTitle());
            item.put("bizType", "task");
            item.put("bizId", task.getId());
            item.put("dueTime", task.getPlanEnd());
            item.put("overdueDays", overdueDays);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> policyAlerts(DataScopeInfo info) {
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(POLICY_EXPIRE_DAYS);
        List<IcpPolicy> policies = policyMapper.selectList(new LambdaQueryWrapper<IcpPolicy>()
                        .eq(IcpPolicy::getStatus, 2)
                        .isNotNull(IcpPolicy::getExpireDate)
                        .ge(IcpPolicy::getExpireDate, today)
                        .le(IcpPolicy::getExpireDate, deadline)).stream()
                .filter(row -> scope.visibleByDeptUser(info, row.getOwnerDeptId(), row.getOwnerUserId()))
                .collect(Collectors.toList());
        List<Map<String, Object>> result = new ArrayList<>();
        for (IcpPolicy policy : policies) {
            long daysLeft = Math.max(Duration.between(today.atStartOfDay(),
                    policy.getExpireDate().atStartOfDay()).toDays(), 0L);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", policy.getId());
            item.put("type", TYPE_POLICY_EXPIRING);
            item.put("level", daysLeft <= 7 ? "high" : "medium");
            item.put("title", "制度到期：" + policy.getPolicyName());
            item.put("bizType", "policy");
            item.put("bizId", policy.getId());
            item.put("dueTime", policy.getExpireDate());
            item.put("daysLeft", daysLeft);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> defectAlerts(DataScopeInfo info) {
        List<IcpDefect> defects = defectMapper.selectList(new LambdaQueryWrapper<IcpDefect>()
                        .eq(IcpDefect::getSeverity, 1)
                        .ne(IcpDefect::getStatus, 3)).stream()
                .filter(row -> scope.visibleByDeptUser(info, row.getDeptId(), row.getOwnerUserId()))
                .collect(Collectors.toList());
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (IcpDefect defect : defects) {
            long overdueDays = defect.getFindDate() == null ? 0L
                    : Math.max(Duration.between(defect.getFindDate().atStartOfDay(),
                    today.atStartOfDay()).toDays(), 0L);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", defect.getId());
            item.put("type", TYPE_DEFECT_HIGH_RISK);
            item.put("level", "high");
            item.put("title", "高风险缺陷：" + defect.getDefectName());
            item.put("bizType", "defect");
            item.put("bizId", defect.getId());
            item.put("dueTime", defect.getFindDate());
            item.put("overdueDays", overdueDays);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> processAlerts(DataScopeInfo info) {
        LocalDateTime now = LocalDateTime.now();
        int timeoutHours = configService.getInt(CONFIG_TIMEOUT_HOURS, DEFAULT_TIMEOUT_HOURS);
        List<WfBusinessLink> links = linkMapper.selectList(new LambdaQueryWrapper<WfBusinessLink>()
                        .eq(WfBusinessLink::getStatus, 0)
                        .isNotNull(WfBusinessLink::getStartTime)).stream()
                .filter(link -> scope.visibleLink(info, link.getInitiatorId()))
                .filter(link -> link.getStartTime().plusHours(timeoutHours).isBefore(now))
                .collect(Collectors.toList());
        List<Map<String, Object>> result = new ArrayList<>();
        for (WfBusinessLink link : links) {
            LocalDateTime due = link.getStartTime().plusHours(timeoutHours);
            long overdueHours = Math.max(Duration.between(due, now).toHours(), 0L);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", link.getId());
            item.put("type", TYPE_PROCESS_OVERDUE);
            item.put("level", overdueHours >= 24 ? "high" : "medium");
            item.put("title", "流程超时：" + link.getTitle());
            item.put("bizType", "process");
            item.put("bizId", link.getBusinessId());
            item.put("processInstanceId", link.getProcessInstanceId());
            item.put("dueTime", due.toLocalDate());
            item.put("overdueHours", overdueHours);
            result.add(item);
        }
        return result;
    }
}

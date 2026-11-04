package com.icp.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.icp.collab.entity.CollTask;
import com.icp.collab.mapper.CollTaskMapper;
import com.icp.common.cache.CacheService;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.common.api.ErrorCode;
import com.icp.icp.entity.IcpDefect;
import com.icp.icp.entity.IcpMatrix;
import com.icp.icp.entity.IcpPolicy;
import com.icp.icp.entity.IcpRectify;
import com.icp.icp.entity.IcpRisk;
import com.icp.icp.mapper.IcpDefectMapper;
import com.icp.icp.mapper.IcpMatrixMapper;
import com.icp.icp.mapper.IcpPolicyMapper;
import com.icp.icp.mapper.IcpRectifyMapper;
import com.icp.icp.mapper.IcpRiskMapper;
import com.icp.collab.service.MessageService;
import com.icp.report.support.ReportScope;
import com.icp.system.service.ConfigService;
import com.icp.workflow.entity.WfBusinessLink;
import com.icp.workflow.mapper.WfBusinessLinkMapper;
import com.icp.workflow.service.WorkflowTaskService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * M2e 看板：摘要 + 趋势（D39：实时 SQL 聚合；summary 允许 60s Caffeine 缓存）。
 */
@Service
public class DashboardService {

    private static final String CACHE_PREFIX = "report:summary:";
    private static final long SUMMARY_TTL_SECONDS = 60L;
    private static final String CONFIG_TIMEOUT_HOURS = "workflow.timeout.hours";
    private static final int DEFAULT_TIMEOUT_HOURS = 48;
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ReportScope scope;
    private final CacheService cacheService;
    private final ConfigService configService;
    private final WorkflowTaskService workflowTaskService;
    private final MessageService messageService;
    private final IcpPolicyMapper policyMapper;
    private final IcpRiskMapper riskMapper;
    private final IcpMatrixMapper matrixMapper;
    private final IcpDefectMapper defectMapper;
    private final IcpRectifyMapper rectifyMapper;
    private final CollTaskMapper taskMapper;
    private final WfBusinessLinkMapper linkMapper;

    public DashboardService(ReportScope scope,
                            CacheService cacheService,
                            ConfigService configService,
                            WorkflowTaskService workflowTaskService,
                            MessageService messageService,
                            IcpPolicyMapper policyMapper,
                            IcpRiskMapper riskMapper,
                            IcpMatrixMapper matrixMapper,
                            IcpDefectMapper defectMapper,
                            IcpRectifyMapper rectifyMapper,
                            CollTaskMapper taskMapper,
                            WfBusinessLinkMapper linkMapper) {
        this.scope = scope;
        this.cacheService = cacheService;
        this.configService = configService;
        this.workflowTaskService = workflowTaskService;
        this.messageService = messageService;
        this.policyMapper = policyMapper;
        this.riskMapper = riskMapper;
        this.matrixMapper = matrixMapper;
        this.defectMapper = defectMapper;
        this.rectifyMapper = rectifyMapper;
        this.taskMapper = taskMapper;
        this.linkMapper = linkMapper;
    }

    public Map<String, Object> summary() {
        DataScopeInfo info = scope.current();
        String cacheKey = CACHE_PREFIX + scope.cacheKey(info);
        Optional<Object> cached = cacheService.get(cacheKey);
        if (cached.isPresent() && cached.get() instanceof Map<?, ?> cachedMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existing = (Map<String, Object>) cachedMap;
            return existing;
        }
        Map<String, Object> data = buildSummary(info);
        cacheService.put(cacheKey, data, SUMMARY_TTL_SECONDS);
        return data;
    }

    private Map<String, Object> buildSummary(DataScopeInfo info) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        List<IcpPolicy> policies = policyMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(row -> scope.visibleByDeptUser(info, row.getOwnerDeptId(), row.getOwnerUserId()))
                .collect(Collectors.toList());
        long policyTotal = policies.size();
        long policyEffective = policies.stream().filter(row -> Integer.valueOf(2).equals(row.getStatus())).count();

        List<IcpRisk> risks = riskMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(row -> scope.visibleByDeptUser(info, row.getOwnerDeptId(), row.getOwnerUserId()))
                .collect(Collectors.toList());
        long riskTotal = risks.size();
        long riskHigh = risks.stream().filter(row -> Integer.valueOf(1).equals(row.getRiskLevel())).count();
        Set<Long> riskIds = risks.stream().map(IcpRisk::getId).collect(Collectors.toSet());
        List<IcpMatrix> matrices = matrixMapper.selectList(new LambdaQueryWrapper<IcpMatrix>()
                .eq(IcpMatrix::getStatus, 1));
        long covered = matrices.stream().map(IcpMatrix::getRiskId)
                .filter(riskIds::contains).distinct().count();
        double coverageRate = riskTotal == 0 ? 0D : round2(covered * 100.0 / riskTotal);

        List<IcpDefect> defects = defectMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(row -> scope.visibleByDeptUser(info, row.getDeptId(), row.getOwnerUserId()))
                .collect(Collectors.toList());
        long defectTotal = defects.size();
        long defectUnclosed = defects.stream()
                .filter(row -> !Integer.valueOf(3).equals(row.getStatus())).count();

        List<IcpRectify> rectifies = rectifyMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(row -> scope.visibleByDeptUser(info, row.getDeptId(), row.getResponsibleUserId()))
                .collect(Collectors.toList());
        long rectifyTotal = rectifies.size();
        long rectifyApproved = rectifies.stream().filter(row -> Integer.valueOf(2).equals(row.getStatus())).count();
        double rectifyRate = rectifyTotal == 0 ? 0D : round2(rectifyApproved * 100.0 / rectifyTotal);

        long todo = workflowTaskService.todo(1, 1, null, null).getTotal();
        int timeoutHours = configService.getInt(CONFIG_TIMEOUT_HOURS, DEFAULT_TIMEOUT_HOURS);
        List<WfBusinessLink> runningLinks = linkMapper.selectList(new LambdaQueryWrapper<WfBusinessLink>()
                .eq(WfBusinessLink::getStatus, 0));
        long workflowOverdue = runningLinks.stream()
                .filter(link -> scope.visibleLink(info, link.getInitiatorId()))
                .filter(link -> link.getStartTime() != null
                        && link.getStartTime().plusHours(timeoutHours).isBefore(now))
                .count();

        List<CollTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(row -> scope.visibleTask(info, row.getDeptId(), row.getAssigneeId(), row.getCreatorId()))
                .collect(Collectors.toList());
        long taskOverdue = tasks.stream()
                .filter(row -> row.getStatus() != null && (row.getStatus() == 0 || row.getStatus() == 1))
                .filter(row -> row.getPlanEnd() != null && row.getPlanEnd().isBefore(today))
                .count();

        long unread = 0L;
        Object unreadValue = messageService.unreadCount().get("unread");
        if (unreadValue instanceof Number number) {
            unread = number.longValue();
        }

        Map<String, Object> policiesNode = new LinkedHashMap<>();
        policiesNode.put("total", policyTotal);
        policiesNode.put("effective", policyEffective);
        Map<String, Object> risksNode = new LinkedHashMap<>();
        risksNode.put("total", riskTotal);
        risksNode.put("high", riskHigh);
        Map<String, Object> coverageNode = new LinkedHashMap<>();
        coverageNode.put("totalRisks", riskTotal);
        coverageNode.put("coveredRisks", covered);
        coverageNode.put("coverageRate", coverageRate);
        Map<String, Object> defectsNode = new LinkedHashMap<>();
        defectsNode.put("total", defectTotal);
        defectsNode.put("unclosed", defectUnclosed);
        Map<String, Object> rectifyNode = new LinkedHashMap<>();
        rectifyNode.put("total", rectifyTotal);
        rectifyNode.put("approved", rectifyApproved);
        rectifyNode.put("rate", rectifyRate);
        Map<String, Object> workflowNode = new LinkedHashMap<>();
        workflowNode.put("todo", todo);
        workflowNode.put("overdue", workflowOverdue);
        Map<String, Object> tasksNode = new LinkedHashMap<>();
        tasksNode.put("overdue", taskOverdue);
        Map<String, Object> messagesNode = new LinkedHashMap<>();
        messagesNode.put("unread", unread);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("policies", policiesNode);
        data.put("risks", risksNode);
        data.put("coverage", coverageNode);
        data.put("defects", defectsNode);
        data.put("rectify", rectifyNode);
        data.put("workflow", workflowNode);
        data.put("tasks", tasksNode);
        data.put("messages", messagesNode);
        return data;
    }

    /** 近 N 个月趋势：新增制度/新增缺陷/整改完成/流程平均耗时。 */
    public Map<String, Object> trend(int months) {
        if (months < 1 || months > 12) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "months 取值不合法，应为 1-12");
        }
        DataScopeInfo info = scope.current();
        YearMonth end = YearMonth.now();
        YearMonth start = end.minusMonths(months - 1L);

        List<IcpPolicy> policies = policyMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(row -> scope.visibleByDeptUser(info, row.getOwnerDeptId(), row.getOwnerUserId()))
                .collect(Collectors.toList());
        List<IcpDefect> defects = defectMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(row -> scope.visibleByDeptUser(info, row.getDeptId(), row.getOwnerUserId()))
                .collect(Collectors.toList());
        List<IcpRectify> rectifies = rectifyMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(row -> scope.visibleByDeptUser(info, row.getDeptId(), row.getResponsibleUserId()))
                .collect(Collectors.toList());
        List<WfBusinessLink> completedLinks = linkMapper.selectList(new LambdaQueryWrapper<WfBusinessLink>()
                .eq(WfBusinessLink::getStatus, 1)).stream()
                .filter(link -> scope.visibleLink(info, link.getInitiatorId()))
                .collect(Collectors.toList());

        List<String> labels = new ArrayList<>();
        List<Long> newPolicies = new ArrayList<>();
        List<Long> newDefects = new ArrayList<>();
        List<Long> rectifyClosed = new ArrayList<>();
        List<Double> avgProcessHours = new ArrayList<>();

        for (int i = 0; i < months; i++) {
            YearMonth month = start.plusMonths(i);
            labels.add(month.format(MONTH_FORMAT));

            long policyCount = policies.stream()
                    .filter(row -> row.getCreateTime() != null
                            && YearMonth.from(row.getCreateTime()).equals(month))
                    .count();
            newPolicies.add(policyCount);

            long defectCount = defects.stream()
                    .filter(row -> row.getCreateTime() != null
                            && YearMonth.from(row.getCreateTime()).equals(month))
                    .count();
            newDefects.add(defectCount);

            long closedCount = rectifies.stream()
                    .filter(row -> isRectifyClosedInMonth(row, month))
                    .count();
            rectifyClosed.add(closedCount);

            List<WfBusinessLink> monthLinks = completedLinks.stream()
                    .filter(link -> link.getEndTime() != null
                            && YearMonth.from(link.getEndTime()).equals(month))
                    .toList();
            if (monthLinks.isEmpty()) {
                avgProcessHours.add(0D);
            } else {
                double sum = 0D;
                for (WfBusinessLink link : monthLinks) {
                    sum += hoursBetween(link.getStartTime(), link.getEndTime());
                }
                avgProcessHours.add(round2(sum / monthLinks.size()));
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("labels", labels);
        data.put("newPolicies", newPolicies);
        data.put("newDefects", newDefects);
        data.put("rectifyClosed", rectifyClosed);
        data.put("avgProcessHours", avgProcessHours);
        return data;
    }

    private boolean isRectifyClosedInMonth(IcpRectify row, YearMonth month) {
        if (!Integer.valueOf(2).equals(row.getStatus())) {
            return false;
        }
        if (row.getReviewTime() != null) {
            return YearMonth.from(row.getReviewTime()).equals(month);
        }
        return row.getFinishDate() != null && YearMonth.from(row.getFinishDate()).equals(month);
    }

    static double hoursBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0D;
        }
        return Duration.between(start, end).toMinutes() / 60.0;
    }

    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

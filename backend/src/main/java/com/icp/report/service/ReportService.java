package com.icp.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.icp.collab.entity.CollTask;
import com.icp.collab.mapper.CollTaskMapper;
import com.icp.common.api.ErrorCode;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.icp.entity.IcpDefect;
import com.icp.icp.entity.IcpMatrix;
import com.icp.icp.entity.IcpRectify;
import com.icp.icp.entity.IcpRisk;
import com.icp.icp.mapper.IcpDefectMapper;
import com.icp.icp.mapper.IcpMatrixMapper;
import com.icp.icp.mapper.IcpRectifyMapper;
import com.icp.icp.mapper.IcpRiskMapper;
import com.icp.icp.support.IcpLookupService;
import com.icp.report.support.ReportScope;
import com.icp.workflow.entity.WfBusinessLink;
import com.icp.workflow.mapper.WfBusinessLinkMapper;
import com.icp.workflow.support.WorkflowConstants;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * M2e 报表：内控覆盖率 / 风险分布 / 整改完成率 / 流程平均耗时 / 缺陷统计（实时聚合，D39）。
 */
@Service
public class ReportService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Map<Integer, String> RISK_LEVEL_NAMES = Map.of(1, "高", 2, "中", 3, "低");
    private static final Set<String> DIMS = Set.of("level", "category", "dept");

    private final ReportScope scope;
    private final IcpLookupService lookupService;
    private final IcpRiskMapper riskMapper;
    private final IcpMatrixMapper matrixMapper;
    private final IcpRectifyMapper rectifyMapper;
    private final IcpDefectMapper defectMapper;
    private final WfBusinessLinkMapper linkMapper;
    private final CollTaskMapper taskMapper;

    public ReportService(ReportScope scope,
                         IcpLookupService lookupService,
                         IcpRiskMapper riskMapper,
                         IcpMatrixMapper matrixMapper,
                         IcpRectifyMapper rectifyMapper,
                         IcpDefectMapper defectMapper,
                         WfBusinessLinkMapper linkMapper,
                         CollTaskMapper taskMapper) {
        this.scope = scope;
        this.lookupService = lookupService;
        this.riskMapper = riskMapper;
        this.matrixMapper = matrixMapper;
        this.rectifyMapper = rectifyMapper;
        this.defectMapper = defectMapper;
        this.linkMapper = linkMapper;
        this.taskMapper = taskMapper;
    }

    /** 任务统计（M2e 补充 W05）：按状态 0/1/2/3 计数，附带逾期数。 */
    public Map<String, Object> taskStatistics() {
        DataScopeInfo info = scope.current();
        List<CollTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(row -> scope.visibleTask(info, row.getDeptId(), row.getAssigneeId(), row.getCreatorId()))
                .collect(Collectors.toList());
        java.time.LocalDate today = java.time.LocalDate.now();
        List<Map<String, Object>> items = new ArrayList<>();
        long total = 0;
        long overdue = 0;
        for (int status = 0; status <= 3; status++) {
            final int statusValue = status;
            long count = tasks.stream()
                    .filter(row -> Integer.valueOf(statusValue).equals(row.getStatus()))
                    .count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", status);
            item.put("count", count);
            items.add(item);
            total += count;
        }
        overdue = tasks.stream()
                .filter(row -> row.getStatus() != null && (row.getStatus() == 0 || row.getStatus() == 1))
                .filter(row -> row.getPlanEnd() != null && row.getPlanEnd().isBefore(today))
                .count();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", total);
        data.put("overdue", overdue);
        return data;
    }

    /** 内控覆盖率：covered = 风险至少有 1 条 status=1 的控制矩阵（docs/03 §7.1 第 3 条）。 */
    public Map<String, Object> icpCoverage(Long deptId, Integer year) {
        DataScopeInfo info = scope.current();
        Long deptFilter = scope.effectiveDeptFilter(info, deptId);
        List<IcpRisk> risks = riskMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(row -> scope.visibleByDeptUser(info, row.getOwnerDeptId(), row.getOwnerUserId()))
                .filter(row -> deptFilter == null || deptFilter.equals(row.getOwnerDeptId()))
                .filter(row -> matchesYear(row.getCreateTime(), year))
                .collect(Collectors.toList());
        Set<Long> riskIds = risks.stream().map(IcpRisk::getId).collect(Collectors.toSet());
        Set<Long> coveredIds = matrixMapper.selectList(new LambdaQueryWrapper<IcpMatrix>()
                        .eq(IcpMatrix::getStatus, 1)).stream()
                .map(IcpMatrix::getRiskId)
                .filter(riskIds::contains)
                .collect(Collectors.toSet());

        Map<Long, List<IcpRisk>> byDept = risks.stream()
                .collect(Collectors.groupingBy(row -> row.getOwnerDeptId() == null ? 0L : row.getOwnerDeptId(),
                        TreeMap::new, Collectors.toList()));
        Map<Long, String> deptNames = lookupService.deptNames(new ArrayList<>(byDept.keySet()));
        List<Map<String, Object>> byDeptList = new ArrayList<>();
        for (Map.Entry<Long, List<IcpRisk>> entry : byDept.entrySet()) {
            long groupTotal = entry.getValue().size();
            long groupCovered = entry.getValue().stream().map(IcpRisk::getId)
                    .filter(coveredIds::contains).distinct().count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("deptId", entry.getKey());
            item.put("deptName", nameOf(deptNames, entry.getKey()));
            item.put("total", groupTotal);
            item.put("covered", groupCovered);
            item.put("rate", rate(groupCovered, groupTotal));
            byDeptList.add(item);
        }

        Map<Long, List<IcpRisk>> byCategory = risks.stream()
                .filter(row -> row.getCategoryId() != null)
                .collect(Collectors.groupingBy(IcpRisk::getCategoryId, TreeMap::new, Collectors.toList()));
        Map<Long, String> categoryNames = lookupService.riskCategoryNames(new ArrayList<>(byCategory.keySet()));
        List<Map<String, Object>> byCategoryList = new ArrayList<>();
        for (Map.Entry<Long, List<IcpRisk>> entry : byCategory.entrySet()) {
            long groupTotal = entry.getValue().size();
            long groupCovered = entry.getValue().stream().map(IcpRisk::getId)
                    .filter(coveredIds::contains).distinct().count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("categoryId", entry.getKey());
            item.put("categoryName", nameOf(categoryNames, entry.getKey()));
            item.put("total", groupTotal);
            item.put("covered", groupCovered);
            item.put("rate", rate(groupCovered, groupTotal));
            byCategoryList.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalRisks", risks.size());
        data.put("coveredRisks", coveredIds.size());
        data.put("coverageRate", rate(coveredIds.size(), risks.size()));
        data.put("byDept", byDeptList);
        data.put("byCategory", byCategoryList);
        return data;
    }

    /** 风险分布：dim = level / category / dept，items 合计 = total。 */
    public Map<String, Object> riskDistribution(String dim) {
        if (dim == null || !DIMS.contains(dim)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "dim 取值不合法，仅支持 level/category/dept");
        }
        DataScopeInfo info = scope.current();
        List<IcpRisk> risks = riskMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(row -> scope.visibleByDeptUser(info, row.getOwnerDeptId(), row.getOwnerUserId()))
                .collect(Collectors.toList());
        List<Map<String, Object>> items = new ArrayList<>();
        if ("level".equals(dim)) {
            for (Map.Entry<Integer, String> level : new TreeMap<>(RISK_LEVEL_NAMES).entrySet()) {
                long value = risks.stream().filter(row -> level.getKey().equals(row.getRiskLevel())).count();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", level.getValue());
                item.put("value", value);
                items.add(item);
            }
        } else if ("category".equals(dim)) {
            Map<Long, Long> counts = risks.stream()
                    .filter(row -> row.getCategoryId() != null)
                    .collect(Collectors.groupingBy(IcpRisk::getCategoryId, TreeMap::new, Collectors.counting()));
            Map<Long, String> names = lookupService.riskCategoryNames(new ArrayList<>(counts.keySet()));
            for (Map.Entry<Long, Long> entry : counts.entrySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", nameOf(names, entry.getKey()));
                item.put("value", entry.getValue());
                items.add(item);
            }
            items.sort(Comparator.comparingLong(row -> -((Number) row.get("value")).longValue()));
        } else {
            Map<Long, Long> counts = risks.stream()
                    .collect(Collectors.groupingBy(row -> row.getOwnerDeptId() == null ? 0L : row.getOwnerDeptId(),
                            TreeMap::new, Collectors.counting()));
            Map<Long, String> names = lookupService.deptNames(new ArrayList<>(counts.keySet()));
            for (Map.Entry<Long, Long> entry : counts.entrySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", nameOf(names, entry.getKey()));
                item.put("value", entry.getValue());
                items.add(item);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dim", dim);
        data.put("items", items);
        data.put("total", risks.size());
        return data;
    }

    /** 整改完成率：approved = status=2 已通过 / 总数；按部门与月度拆分。 */
    public Map<String, Object> rectifyRate(Integer year, Long deptId) {
        DataScopeInfo info = scope.current();
        Long deptFilter = scope.effectiveDeptFilter(info, deptId);
        List<IcpRectify> rectifies = rectifyMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(row -> scope.visibleByDeptUser(info, row.getDeptId(), row.getResponsibleUserId()))
                .filter(row -> deptFilter == null || deptFilter.equals(row.getDeptId()))
                .collect(Collectors.toList());
        int targetYear = year == null ? YearMonth.now().getYear() : year;
        List<IcpRectify> yearRectifies = rectifies.stream()
                .filter(row -> {
                    YearMonth month = closeMonthOf(row);
                    return month != null && month.getYear() == targetYear;
                })
                .collect(Collectors.toList());

        Map<Long, List<IcpRectify>> byDept = yearRectifies.stream()
                .collect(Collectors.groupingBy(row -> row.getDeptId() == null ? 0L : row.getDeptId(),
                        TreeMap::new, Collectors.toList()));
        Map<Long, String> deptNames = lookupService.deptNames(new ArrayList<>(byDept.keySet()));
        List<Map<String, Object>> byDeptList = new ArrayList<>();
        for (Map.Entry<Long, List<IcpRectify>> entry : byDept.entrySet()) {
            long groupTotal = entry.getValue().size();
            long groupApproved = entry.getValue().stream()
                    .filter(row -> Integer.valueOf(2).equals(row.getStatus())).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("deptId", entry.getKey());
            item.put("deptName", nameOf(deptNames, entry.getKey()));
            item.put("total", groupTotal);
            item.put("approved", groupApproved);
            item.put("rate", rate(groupApproved, groupTotal));
            byDeptList.add(item);
        }

        List<Map<String, Object>> byMonth = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            YearMonth target = YearMonth.of(targetYear, month);
            List<IcpRectify> monthRows = yearRectifies.stream()
                    .filter(row -> target.equals(closeMonthOf(row)))
                    .collect(Collectors.toList());
            long monthTotal = monthRows.size();
            long monthApproved = monthRows.stream()
                    .filter(row -> Integer.valueOf(2).equals(row.getStatus())).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", target.format(MONTH_FORMAT));
            item.put("total", monthTotal);
            item.put("approved", monthApproved);
            item.put("rate", rate(monthApproved, monthTotal));
            byMonth.add(item);
        }

        long approved = yearRectifies.stream().filter(row -> Integer.valueOf(2).equals(row.getStatus())).count();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", yearRectifies.size());
        data.put("approved", approved);
        data.put("rate", rate(approved, yearRectifies.size()));
        data.put("byDept", byDeptList);
        data.put("byMonth", byMonth);
        return data;
    }

    /** 流程平均耗时：仅已完成实例（status=1，耗时=end_time-start_time）。 */
    public Map<String, Object> processDuration(String processKey, int months) {
        DataScopeInfo info = scope.current();
        LocalDateTime threshold = LocalDateTime.now().minusMonths(months);
        List<WfBusinessLink> links = linkMapper.selectList(new LambdaQueryWrapper<WfBusinessLink>()
                        .eq(WfBusinessLink::getStatus, WorkflowConstants.LINK_APPROVED)).stream()
                .filter(link -> scope.visibleLink(info, link.getInitiatorId()))
                .filter(link -> link.getEndTime() != null && !link.getEndTime().isBefore(threshold))
                .filter(link -> processKey == null || processKey.isBlank()
                        || processKey.equals(link.getProcessKey()))
                .collect(Collectors.toList());

        List<String> keys = new ArrayList<>();
        if (processKey != null && !processKey.isBlank()) {
            keys.add(processKey);
        } else {
            keys.addAll(WorkflowConstants.PROCESS_KEYS);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (String key : keys) {
            List<Double> durations = links.stream()
                    .filter(link -> key.equals(link.getProcessKey()))
                    .map(link -> DashboardService.hoursBetween(link.getStartTime(), link.getEndTime()))
                    .sorted()
                    .collect(Collectors.toList());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("processKey", key);
            item.put("processName", WorkflowConstants.PROCESS_NAMES.getOrDefault(key, key));
            item.put("total", durations.size());
            item.put("avgHours", durations.isEmpty() ? 0D
                    : DashboardService.round2(durations.stream().mapToDouble(Double::doubleValue).average().orElse(0D)));
            item.put("p95Hours", p95(durations));
            items.add(item);
        }

        YearMonth end = YearMonth.now();
        List<Map<String, Object>> byMonth = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth month = end.minusMonths(i);
            List<Double> durations = links.stream()
                    .filter(link -> link.getEndTime() != null && YearMonth.from(link.getEndTime()).equals(month))
                    .map(link -> DashboardService.hoursBetween(link.getStartTime(), link.getEndTime()))
                    .collect(Collectors.toList());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", month.format(MONTH_FORMAT));
            item.put("count", durations.size());
            item.put("avgHours", durations.isEmpty() ? 0D
                    : DashboardService.round2(durations.stream().mapToDouble(Double::doubleValue).average().orElse(0D)));
            byMonth.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("byMonth", byMonth);
        return data;
    }

    /** 缺陷统计：按（severity,status）计数，支持过滤。 */
    public Map<String, Object> defects(Integer severity, Integer status) {
        DataScopeInfo info = scope.current();
        List<IcpDefect> rows = defectMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(row -> scope.visibleByDeptUser(info, row.getDeptId(), row.getOwnerUserId()))
                .filter(row -> severity == null || severity.equals(row.getSeverity()))
                .filter(row -> status == null || status.equals(row.getStatus()))
                .collect(Collectors.toList());
        Map<String, Map<String, Object>> grouped = new TreeMap<>();
        for (IcpDefect row : rows) {
            int sev = row.getSeverity() == null ? 0 : row.getSeverity();
            int st = row.getStatus() == null ? 0 : row.getStatus();
            String key = String.format("%02d-%02d", sev, st);
            Map<String, Object> item = grouped.computeIfAbsent(key, ignored -> {
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("severity", sev);
                created.put("status", st);
                created.put("count", 0L);
                return created;
            });
            item.put("count", ((Number) item.get("count")).longValue() + 1L);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", new ArrayList<>(grouped.values()));
        data.put("total", rows.size());
        return data;
    }

    private static double p95(List<Double> sortedDurations) {
        if (sortedDurations.isEmpty()) {
            return 0D;
        }
        int index = (int) Math.ceil(sortedDurations.size() * 0.95) - 1;
        if (index < 0) {
            index = 0;
        }
        if (index >= sortedDurations.size()) {
            index = sortedDurations.size() - 1;
        }
        return DashboardService.round2(sortedDurations.get(index));
    }

    private static YearMonth closeMonthOf(IcpRectify row) {
        if (row.getReviewTime() != null) {
            return YearMonth.from(row.getReviewTime());
        }
        if (row.getFinishDate() != null) {
            return YearMonth.from(row.getFinishDate());
        }
        return row.getCreateTime() == null ? null : YearMonth.from(row.getCreateTime());
    }

    private static boolean matchesYear(LocalDateTime time, Integer year) {
        if (year == null) {
            return true;
        }
        return time != null && time.getYear() == year;
    }

    private static double rate(long part, long total) {
        return total == 0 ? 0D : DashboardService.round2(part * 100.0 / total);
    }

    private static String nameOf(Map<Long, String> names, Long id) {
        String name = names.get(id);
        return name == null || name.isBlank() ? "未分配" : name;
    }

    private static boolean nonNull(Object value) {
        return Objects.nonNull(value);
    }
}

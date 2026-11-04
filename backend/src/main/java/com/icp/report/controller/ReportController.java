package com.icp.report.controller;

import com.icp.common.api.ApiResponse;
import com.icp.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * M2e 报表中心 5 个报表（实时 SQL 聚合，不建汇总表）。权限沿用既有 report:* 菜单按钮，不新增种子。
 */
@Tag(name = "报表中心")
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "内控覆盖率（部门/分类维度）")
    @GetMapping("/icp-coverage")
    @PreAuthorize("hasAuthority('report:icp:view')")
    public ApiResponse<Map<String, Object>> icpCoverage(@RequestParam(required = false) Long deptId,
                                                        @RequestParam(required = false) Integer year) {
        return ApiResponse.ok(reportService.icpCoverage(deptId, year));
    }

    @Operation(summary = "风险分布（level/category/dept）")
    @GetMapping("/risk-distribution")
    @PreAuthorize("hasAuthority('report:risk:view')")
    public ApiResponse<Map<String, Object>> riskDistribution(
            @RequestParam(defaultValue = "level") String dim) {
        return ApiResponse.ok(reportService.riskDistribution(dim));
    }

    @Operation(summary = "整改完成率（按部门/月度）")
    @GetMapping("/rectify-rate")
    @PreAuthorize("hasAuthority('report:rectify:view')")
    public ApiResponse<Map<String, Object>> rectifyRate(@RequestParam(required = false) Integer year,
                                                        @RequestParam(required = false) Long deptId) {
        return ApiResponse.ok(reportService.rectifyRate(year, deptId));
    }

    @Operation(summary = "流程平均耗时（按流程/月）")
    @GetMapping("/process-duration")
    @PreAuthorize("hasAuthority('report:process:view')")
    public ApiResponse<Map<String, Object>> processDuration(@RequestParam(required = false) String processKey,
                                                            @RequestParam(defaultValue = "6") int months) {
        return ApiResponse.ok(reportService.processDuration(processKey, months));
    }

    @Operation(summary = "任务统计（状态分布 + 逾期数，M2e 补充 W05）")
    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('report:process:view')")
    public ApiResponse<Map<String, Object>> tasks() {
        return ApiResponse.ok(reportService.taskStatistics());
    }

    @Operation(summary = "缺陷统计（severity/status 过滤）")
    @GetMapping("/defects")
    @PreAuthorize("hasAuthority('report:rectify:view')")
    public ApiResponse<Map<String, Object>> defects(@RequestParam(required = false) Integer severity,
                                                    @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(reportService.defects(severity, status));
    }
}

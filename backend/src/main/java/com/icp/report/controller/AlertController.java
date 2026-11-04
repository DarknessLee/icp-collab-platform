package com.icp.report.controller;

import com.icp.common.api.ApiResponse;
import com.icp.report.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * M2e 预警中心：超时任务、30 天内到期制度、高风险未关闭缺陷、超时流程（查询时实时计算）。
 */
@Tag(name = "预警中心")
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @Operation(summary = "预警分页列表（type 过滤）")
    @GetMapping
    @PreAuthorize("hasAuthority('report:alert:view')")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.ok(alertService.page(type, pageNum, pageSize));
    }

    @Operation(summary = "预警数量汇总（用于导航角标）")
    @GetMapping("/count")
    @PreAuthorize("hasAuthority('report:alert:view')")
    public ApiResponse<Map<String, Object>> count() {
        return ApiResponse.ok(alertService.count());
    }
}

package com.icp.report.controller;

import com.icp.common.api.ApiResponse;
import com.icp.report.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * M2e 工作台首页卡片与趋势。EMPLOYEE 无 report:* 权限，但拥有工作台菜单 61/62，
 * 因此本控制器用 hasAnyAuthority 放行，数据范围由 service 层按 @DataScope 收窄（员工仅本人相关）。
 */
@Tag(name = "工作台看板")
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "首页卡片汇总（60s 缓存，key 含用户与数据范围）")
    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('report:dashboard:view','dashboard:workbench:view','dashboard:todo:view')")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(dashboardService.summary());
    }

    @Operation(summary = "近 N 个月趋势（months 1-12，默认 6）")
    @GetMapping("/trend")
    @PreAuthorize("hasAnyAuthority('report:dashboard:view','dashboard:workbench:view','dashboard:todo:view')")
    public ApiResponse<Map<String, Object>> trend(@RequestParam(defaultValue = "6") int months) {
        return ApiResponse.ok(dashboardService.trend(months));
    }
}

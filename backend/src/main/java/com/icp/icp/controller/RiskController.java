package com.icp.icp.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.icp.dto.IcpRequests;
import com.icp.icp.entity.IcpRisk;
import com.icp.icp.service.RiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "风险库")
@RestController
@RequestMapping("/api/v1/risks")
public class RiskController {

    private final RiskService service;

    public RiskController(RiskService service) {
        this.service = service;
    }

    @Operation(summary = "风险分页")
    @GetMapping
    @PreAuthorize("hasAuthority('icp:risk:list')")
    public ApiResponse<PageResult<IcpRisk>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer riskLevel,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(service.page(pageNum, pageSize, keyword, categoryId, riskLevel, status));
    }

    @Operation(summary = "风险详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:risk:get')")
    public ApiResponse<IcpRisk> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "新增风险")
    @PostMapping
    @PreAuthorize("hasAuthority('icp:risk:add')")
    @AuditLog(module = "内控管理", action = "新增风险", businessType = "icp_risk")
    public ApiResponse<IcpRisk> create(@RequestBody IcpRisk request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "修改风险")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:risk:edit')")
    @AuditLog(module = "内控管理", action = "修改风险", businessType = "icp_risk")
    public ApiResponse<IcpRisk> update(@PathVariable Long id, @RequestBody IcpRisk request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Operation(summary = "删除风险")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:risk:delete')")
    @AuditLog(module = "内控管理", action = "删除风险", businessType = "icp_risk")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "风险启停")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('icp:risk:status')")
    @AuditLog(module = "内控管理", action = "风险启停", businessType = "icp_risk")
    public ApiResponse<IcpRisk> status(@PathVariable Long id, @RequestBody IcpRequests.Status request) {
        return ApiResponse.ok(service.changeStatus(id, request == null ? null : request.status()));
    }
}

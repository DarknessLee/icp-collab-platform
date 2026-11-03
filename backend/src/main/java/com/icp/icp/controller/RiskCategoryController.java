package com.icp.icp.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.audit.AuditLog;
import com.icp.icp.entity.IcpRiskCategory;
import com.icp.icp.service.RiskCategoryService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "风险分类")
@RestController
@RequestMapping("/api/v1/risk-categories")
public class RiskCategoryController {

    private final RiskCategoryService service;

    public RiskCategoryController(RiskCategoryService service) {
        this.service = service;
    }

    @Operation(summary = "风险分类树")
    @GetMapping("/tree")
    @PreAuthorize("hasAnyAuthority('icp:risk-category:list','icp:risk:list')")
    public ApiResponse<List<IcpRiskCategory>> tree() {
        return ApiResponse.ok(service.tree());
    }

    @Operation(summary = "新增风险分类")
    @PostMapping
    @PreAuthorize("hasAuthority('icp:risk-category:add')")
    @AuditLog(module = "内控管理", action = "新增风险分类", businessType = "icp_risk_category")
    public ApiResponse<IcpRiskCategory> create(@RequestBody IcpRiskCategory request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "修改风险分类")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:risk-category:edit')")
    @AuditLog(module = "内控管理", action = "修改风险分类", businessType = "icp_risk_category")
    public ApiResponse<IcpRiskCategory> update(@PathVariable Long id, @RequestBody IcpRiskCategory request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Operation(summary = "删除风险分类")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:risk-category:delete')")
    @AuditLog(module = "内控管理", action = "删除风险分类", businessType = "icp_risk_category")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}

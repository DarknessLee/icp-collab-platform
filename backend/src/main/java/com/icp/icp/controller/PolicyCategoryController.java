package com.icp.icp.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.audit.AuditLog;
import com.icp.icp.entity.IcpPolicyCategory;
import com.icp.icp.service.PolicyCategoryService;
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

@Tag(name = "制度分类")
@RestController
@RequestMapping("/api/v1/policy-categories")
public class PolicyCategoryController {

    private final PolicyCategoryService service;

    public PolicyCategoryController(PolicyCategoryService service) {
        this.service = service;
    }

    @Operation(summary = "制度分类树")
    @GetMapping("/tree")
    @PreAuthorize("hasAnyAuthority('icp:policy-category:list','icp:policy:list')")
    public ApiResponse<List<IcpPolicyCategory>> tree() {
        return ApiResponse.ok(service.tree());
    }

    @Operation(summary = "新增制度分类")
    @PostMapping
    @PreAuthorize("hasAuthority('icp:policy-category:add')")
    @AuditLog(module = "内控管理", action = "新增制度分类", businessType = "icp_policy_category")
    public ApiResponse<IcpPolicyCategory> create(@RequestBody IcpPolicyCategory request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "修改制度分类")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:policy-category:edit')")
    @AuditLog(module = "内控管理", action = "修改制度分类", businessType = "icp_policy_category")
    public ApiResponse<IcpPolicyCategory> update(@PathVariable Long id, @RequestBody IcpPolicyCategory request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Operation(summary = "删除制度分类")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:policy-category:delete')")
    @AuditLog(module = "内控管理", action = "删除制度分类", businessType = "icp_policy_category")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}

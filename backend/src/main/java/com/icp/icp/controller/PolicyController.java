package com.icp.icp.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.icp.dto.IcpRequests;
import com.icp.icp.entity.IcpPolicy;
import com.icp.icp.service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "制度库")
@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyService service;

    public PolicyController(PolicyService service) {
        this.service = service;
    }

    @Operation(summary = "制度分页")
    @GetMapping
    @PreAuthorize("hasAuthority('icp:policy:list')")
    public ApiResponse<PageResult<IcpPolicy>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveTo) {
        return ApiResponse.ok(service.page(pageNum, pageSize, keyword, categoryId, status, effectiveFrom, effectiveTo));
    }

    @Operation(summary = "有效制度下拉")
    @GetMapping("/effective")
    @PreAuthorize("hasAuthority('icp:policy:list')")
    public ApiResponse<List<IcpPolicy>> effective() {
        return ApiResponse.ok(service.effectivePolicies());
    }

    @Operation(summary = "制度详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:policy:get')")
    public ApiResponse<IcpPolicy> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "新增制度")
    @PostMapping
    @PreAuthorize("hasAuthority('icp:policy:add')")
    @AuditLog(module = "内控管理", action = "新增制度", businessType = "icp_policy")
    public ApiResponse<IcpPolicy> create(@RequestBody IcpPolicy request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "修改制度")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:policy:edit')")
    @AuditLog(module = "内控管理", action = "修改制度", businessType = "icp_policy")
    public ApiResponse<IcpPolicy> update(@PathVariable Long id, @RequestBody IcpPolicy request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Operation(summary = "删除制度")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:policy:delete')")
    @AuditLog(module = "内控管理", action = "删除制度", businessType = "icp_policy")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "制度送审（M2b 不接流程，pendingWorkflow=true）")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('icp:policy:submit')")
    @AuditLog(module = "内控管理", action = "制度送审", businessType = "icp_policy")
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long id) {
        return ApiResponse.ok(service.submit(id));
    }

    @Operation(summary = "制度发布")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('icp:policy:publish')")
    @AuditLog(module = "内控管理", action = "制度发布", businessType = "icp_policy")
    public ApiResponse<Map<String, Object>> publish(@PathVariable Long id,
                                                    @RequestBody(required = false) IcpRequests.Publish request) {
        return ApiResponse.ok(service.publish(id, request));
    }

    @Operation(summary = "制度废止")
    @PostMapping("/{id}/abolish")
    @PreAuthorize("hasAuthority('icp:policy:abolish')")
    @AuditLog(module = "内控管理", action = "制度废止", businessType = "icp_policy")
    public ApiResponse<Map<String, Object>> abolish(@PathVariable Long id) {
        return ApiResponse.ok(service.abolish(id));
    }
}

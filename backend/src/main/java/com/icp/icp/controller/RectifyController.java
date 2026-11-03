package com.icp.icp.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.icp.dto.IcpRequests;
import com.icp.icp.entity.IcpRectify;
import com.icp.icp.service.RectifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "整改跟踪")
@RestController
@RequestMapping("/api/v1/rectifies")
public class RectifyController {

    private final RectifyService service;

    public RectifyController(RectifyService service) {
        this.service = service;
    }

    @Operation(summary = "整改分页")
    @GetMapping
    @PreAuthorize("hasAuthority('icp:rectify:list')")
    public ApiResponse<PageResult<IcpRectify>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long defectId,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNum, pageSize, defectId, deptId, status, keyword));
    }

    @Operation(summary = "整改详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:rectify:get')")
    public ApiResponse<IcpRectify> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "修改整改")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:rectify:edit')")
    @AuditLog(module = "内控管理", action = "修改整改", businessType = "icp_rectify")
    public ApiResponse<IcpRectify> update(@PathVariable Long id, @RequestBody IcpRectify request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Operation(summary = "提交整改")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('icp:rectify:submit')")
    @AuditLog(module = "内控管理", action = "提交整改", businessType = "icp_rectify")
    public ApiResponse<IcpRectify> submit(@PathVariable Long id,
                                          @RequestBody(required = false) IcpRequests.RectifySubmit request) {
        return ApiResponse.ok(service.submit(id, request));
    }

    @Operation(summary = "审核通过整改")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('icp:rectify:approve')")
    @AuditLog(module = "内控管理", action = "审核通过整改", businessType = "icp_rectify")
    public ApiResponse<IcpRectify> approve(@PathVariable Long id,
                                           @RequestBody(required = false) IcpRequests.RectifyReview request) {
        return ApiResponse.ok(service.approve(id, request));
    }

    @Operation(summary = "驳回整改")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('icp:rectify:reject')")
    @AuditLog(module = "内控管理", action = "驳回整改", businessType = "icp_rectify")
    public ApiResponse<IcpRectify> reject(@PathVariable Long id,
                                          @RequestBody(required = false) IcpRequests.RectifyReview request) {
        return ApiResponse.ok(service.reject(id, request));
    }

    @Operation(summary = "关闭整改")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('icp:rectify:close')")
    @AuditLog(module = "内控管理", action = "关闭整改", businessType = "icp_rectify")
    public ApiResponse<IcpRectify> close(@PathVariable Long id) {
        return ApiResponse.ok(service.close(id));
    }
}

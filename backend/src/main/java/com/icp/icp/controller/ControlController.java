package com.icp.icp.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.icp.entity.IcpControl;
import com.icp.icp.service.ControlService;
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

@Tag(name = "控制点库")
@RestController
@RequestMapping("/api/v1/controls")
public class ControlController {

    private final ControlService service;

    public ControlController(ControlService service) {
        this.service = service;
    }

    @Operation(summary = "控制点分页")
    @GetMapping
    @PreAuthorize("hasAuthority('icp:control:list')")
    public ApiResponse<PageResult<IcpControl>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer controlType,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(service.page(pageNum, pageSize, keyword, controlType, status));
    }

    @Operation(summary = "控制点详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:control:get')")
    public ApiResponse<IcpControl> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "新增控制点")
    @PostMapping
    @PreAuthorize("hasAuthority('icp:control:add')")
    @AuditLog(module = "内控管理", action = "新增控制点", businessType = "icp_control")
    public ApiResponse<IcpControl> create(@RequestBody IcpControl request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "修改控制点")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:control:edit')")
    @AuditLog(module = "内控管理", action = "修改控制点", businessType = "icp_control")
    public ApiResponse<IcpControl> update(@PathVariable Long id, @RequestBody IcpControl request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Operation(summary = "删除控制点")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:control:delete')")
    @AuditLog(module = "内控管理", action = "删除控制点", businessType = "icp_control")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}

package com.icp.icp.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.icp.dto.IcpRequests;
import com.icp.icp.entity.IcpDefect;
import com.icp.icp.service.DefectService;
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

import java.util.Map;

@Tag(name = "缺陷管理")
@RestController
@RequestMapping("/api/v1/defects")
public class DefectController {

    private final DefectService service;

    public DefectController(DefectService service) {
        this.service = service;
    }

    @Operation(summary = "缺陷分页")
    @GetMapping
    @PreAuthorize("hasAuthority('icp:defect:list')")
    public ApiResponse<PageResult<IcpDefect>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Integer severity,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer sourceType,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNum, pageSize, severity, deptId, status, sourceType, keyword));
    }

    @Operation(summary = "缺陷详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:defect:get')")
    public ApiResponse<IcpDefect> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "新增缺陷")
    @PostMapping
    @PreAuthorize("hasAuthority('icp:defect:add')")
    @AuditLog(module = "内控管理", action = "新增缺陷", businessType = "icp_defect")
    public ApiResponse<IcpDefect> create(@RequestBody IcpDefect request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "修改缺陷")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:defect:edit')")
    @AuditLog(module = "内控管理", action = "修改缺陷", businessType = "icp_defect")
    public ApiResponse<IcpDefect> update(@PathVariable Long id, @RequestBody IcpDefect request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Operation(summary = "发起整改")
    @PostMapping("/{id}/rectify")
    @PreAuthorize("hasAuthority('icp:defect:rectify')")
    @AuditLog(module = "内控管理", action = "发起整改", businessType = "icp_defect")
    public ApiResponse<Map<String, Object>> rectify(@PathVariable Long id,
                                                    @RequestBody IcpRequests.DefectRectify request) {
        return ApiResponse.ok(service.rectify(id, request));
    }

    @Operation(summary = "关闭缺陷")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('icp:defect:close')")
    @AuditLog(module = "内控管理", action = "关闭缺陷", businessType = "icp_defect")
    public ApiResponse<IcpDefect> close(@PathVariable Long id) {
        return ApiResponse.ok(service.close(id));
    }
}

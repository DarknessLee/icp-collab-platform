package com.icp.icp.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.icp.entity.IcpMatrix;
import com.icp.icp.service.MatrixService;
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

import java.util.Map;

@Tag(name = "风险控制矩阵")
@RestController
@RequestMapping("/api/v1/matrices")
public class MatrixController {

    private final MatrixService service;

    public MatrixController(MatrixService service) {
        this.service = service;
    }

    @Operation(summary = "矩阵分页")
    @GetMapping
    @PreAuthorize("hasAuthority('icp:matrix:list')")
    public ApiResponse<PageResult<IcpMatrix>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long riskId,
            @RequestParam(required = false) Long controlId,
            @RequestParam(required = false) Integer coverage,
            @RequestParam(required = false) String processKey) {
        return ApiResponse.ok(service.page(pageNum, pageSize, riskId, controlId, coverage, processKey));
    }

    @Operation(summary = "矩阵覆盖率")
    @GetMapping("/coverage")
    @PreAuthorize("hasAuthority('icp:matrix:coverage')")
    public ApiResponse<Map<String, Object>> coverage() {
        return ApiResponse.ok(service.coverage());
    }

    @Operation(summary = "矩阵详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:matrix:get')")
    public ApiResponse<IcpMatrix> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "新增矩阵关系")
    @PostMapping
    @PreAuthorize("hasAuthority('icp:matrix:add')")
    @AuditLog(module = "内控管理", action = "新增矩阵关系", businessType = "icp_matrix")
    public ApiResponse<IcpMatrix> create(@RequestBody IcpMatrix request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "修改矩阵关系")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:matrix:edit')")
    @AuditLog(module = "内控管理", action = "修改矩阵关系", businessType = "icp_matrix")
    public ApiResponse<IcpMatrix> update(@PathVariable Long id, @RequestBody IcpMatrix request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Operation(summary = "删除矩阵关系")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:matrix:delete')")
    @AuditLog(module = "内控管理", action = "删除矩阵关系", businessType = "icp_matrix")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}

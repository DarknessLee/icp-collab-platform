package com.icp.system.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.audit.AuditLog;
import com.icp.system.entity.SysDept;
import com.icp.system.service.DeptService;
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

@Tag(name = "部门管理")
@RestController
@RequestMapping("/api/v1/depts")
public class DeptController {

    private final DeptService deptService;

    public DeptController(DeptService deptService) {
        this.deptService = deptService;
    }

    @Operation(summary = "部门树")
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:dept:list')")
    public ApiResponse<List<SysDept>> tree() {
        return ApiResponse.ok(deptService.tree());
    }

    @Operation(summary = "部门列表")
    @GetMapping
    @PreAuthorize("hasAuthority('system:dept:list')")
    public ApiResponse<List<SysDept>> list() {
        return ApiResponse.ok(deptService.list());
    }

    @Operation(summary = "部门详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:list')")
    public ApiResponse<SysDept> detail(@PathVariable Long id) {
        return ApiResponse.ok(deptService.get(id));
    }

    @Operation(summary = "新增部门")
    @PostMapping
    @PreAuthorize("hasAuthority('system:dept:add')")
    @AuditLog(module = "系统管理", action = "新增部门", businessType = "sys_dept")
    public ApiResponse<SysDept> create(@RequestBody SysDept dept) {
        return ApiResponse.ok(deptService.create(dept));
    }

    @Operation(summary = "修改部门")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:edit')")
    @AuditLog(module = "系统管理", action = "修改部门", businessType = "sys_dept")
    public ApiResponse<SysDept> update(@PathVariable Long id, @RequestBody SysDept dept) {
        return ApiResponse.ok(deptService.update(id, dept));
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:delete')")
    @AuditLog(module = "系统管理", action = "删除部门", businessType = "sys_dept")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return ApiResponse.ok();
    }
}

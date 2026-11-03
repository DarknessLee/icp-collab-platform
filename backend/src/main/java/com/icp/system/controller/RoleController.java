package com.icp.system.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.system.dto.Requests;
import com.icp.system.entity.SysRole;
import com.icp.system.service.RoleService;
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

import java.util.List;

/**
 * 角色管理。种子权限无 role:add/edit/delete，写操作复用 system:role:list；
 * 菜单授权使用 system:role:menu。
 */
@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @Operation(summary = "角色分页列表")
    @GetMapping
    @PreAuthorize("hasAuthority('system:role:list')")
    public ApiResponse<PageResult<SysRole>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(roleService.page(Math.max(pageNum, 1), clamp(pageSize), keyword, status));
    }

    @Operation(summary = "角色详情（含菜单 id）")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:list')")
    public ApiResponse<SysRole> detail(@PathVariable Long id) {
        return ApiResponse.ok(roleService.get(id));
    }

    @Operation(summary = "新增角色")
    @PostMapping
    @PreAuthorize("hasAuthority('system:role:add')")
    @AuditLog(module = "系统管理", action = "新增角色", businessType = "sys_role")
    public ApiResponse<SysRole> create(@RequestBody SysRole role) {
        return ApiResponse.ok(roleService.create(role));
    }

    @Operation(summary = "修改角色")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:edit')")
    @AuditLog(module = "系统管理", action = "修改角色", businessType = "sys_role")
    public ApiResponse<SysRole> update(@PathVariable Long id, @RequestBody SysRole role) {
        return ApiResponse.ok(roleService.update(id, role));
    }

    @Operation(summary = "删除角色（内置角色不可删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    @AuditLog(module = "系统管理", action = "删除角色", businessType = "sys_role")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "角色已授权菜单 id 列表")
    @GetMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('system:role:list')")
    public ApiResponse<List<Long>> menus(@PathVariable Long id) {
        return ApiResponse.ok(roleService.menuIdsOf(id));
    }

    @Operation(summary = "整树覆盖角色菜单授权（自动补齐祖先目录）")
    @PutMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('system:role:menu')")
    @AuditLog(module = "系统管理", action = "授权菜单", businessType = "sys_role_menu")
    public ApiResponse<Void> updateMenus(@PathVariable Long id, @RequestBody Requests.RoleMenus request) {
        roleService.updateMenus(id, request.menuIds());
        return ApiResponse.ok();
    }

    private long clamp(long pageSize) {
        return Math.min(Math.max(pageSize, 1), 100);
    }
}

package com.icp.system.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.system.dto.Requests;
import com.icp.system.entity.SysUser;
import com.icp.system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

/**
 * 用户管理。种子权限只有 system:user:add/edit/reset，删除与角色分配复用 system:user:edit
 * （docs/05 无独立 delete/role 权限，已在交付说明中标注）。
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "用户分页列表（数据权限过滤）")
    @GetMapping
    @PreAuthorize("hasAuthority('system:user:list')")
    public ApiResponse<PageResult<SysUser>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long deptId) {
        return ApiResponse.ok(userService.page(Math.max(pageNum, 1), clamp(pageSize), keyword, status, deptId));
    }

    @Operation(summary = "当前用户资料")
    @GetMapping("/me")
    public ApiResponse<SysUser> me() {
        return ApiResponse.ok(userService.profile());
    }

    @Operation(summary = "修改当前用户资料")
    @PutMapping("/me")
    @AuditLog(module = "个人中心", action = "修改个人资料", businessType = "sys_user")
    public ApiResponse<SysUser> updateMe(@RequestBody Requests.Profile request) {
        return ApiResponse.ok(userService.updateProfile(request));
    }

    @Operation(summary = "用户详情（越权返回 40300）")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:list')")
    public ApiResponse<SysUser> detail(@PathVariable Long id) {
        return ApiResponse.ok(userService.detail(id));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    @PreAuthorize("hasAuthority('system:user:add')")
    @AuditLog(module = "系统管理", action = "新增用户", businessType = "sys_user")
    public ApiResponse<SysUser> create(@RequestBody SysUser user) {
        return ApiResponse.ok(userService.create(user));
    }

    @Operation(summary = "修改用户")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @AuditLog(module = "系统管理", action = "修改用户", businessType = "sys_user")
    public ApiResponse<SysUser> update(@PathVariable Long id, @RequestBody SysUser user) {
        return ApiResponse.ok(userService.update(id, user));
    }

    @Operation(summary = "删除用户（逻辑删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    @AuditLog(module = "系统管理", action = "删除用户", businessType = "sys_user")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "修改用户状态 1正常/0停用/2锁定")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:user:status')")
    @AuditLog(module = "系统管理", action = "修改用户状态", businessType = "sys_user")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody Requests.Status request) {
        userService.updateStatus(id, request.status());
        return ApiResponse.ok();
    }

    @Operation(summary = "重置用户密码")
    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:user:reset')")
    @AuditLog(module = "系统管理", action = "重置密码", businessType = "sys_user")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody Requests.ResetPassword request) {
        userService.resetPassword(id, request.password());
        return ApiResponse.ok();
    }

    @Operation(summary = "分配用户角色")
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @AuditLog(module = "系统管理", action = "分配角色", businessType = "sys_user_role")
    public ApiResponse<Void> updateRoles(@PathVariable Long id, @RequestBody Requests.UserRoles request) {
        userService.updateRoles(id, request.roleIds());
        return ApiResponse.ok();
    }

    private long clamp(long pageSize) {
        return Math.min(Math.max(pageSize, 1), 100);
    }
}

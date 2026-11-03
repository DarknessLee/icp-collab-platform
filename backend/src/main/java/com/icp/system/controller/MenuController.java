package com.icp.system.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.audit.AuditLog;
import com.icp.system.entity.SysMenu;
import com.icp.system.service.MenuService;
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
 * 菜单管理。种子权限无 menu:add/edit/delete，写操作复用 system:menu:list。
 */
@Tag(name = "菜单管理")
@RestController
@RequestMapping("/api/v1/menus")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @Operation(summary = "菜单树（M/C/F 嵌套）")
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:menu:list')")
    public ApiResponse<List<SysMenu>> tree() {
        return ApiResponse.ok(menuService.tree());
    }

    @Operation(summary = "菜单扁平列表")
    @GetMapping
    @PreAuthorize("hasAuthority('system:menu:list')")
    public ApiResponse<List<SysMenu>> list(@RequestParam(required = false) Long parentId,
                                           @RequestParam(required = false) String menuType,
                                           @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(menuService.list(parentId, menuType, keyword));
    }

    @Operation(summary = "新增菜单")
    @PostMapping
    @PreAuthorize("hasAuthority('system:menu:add')")
    @AuditLog(module = "系统管理", action = "新增菜单", businessType = "sys_menu")
    public ApiResponse<SysMenu> create(@RequestBody SysMenu menu) {
        return ApiResponse.ok(menuService.create(menu));
    }

    @Operation(summary = "修改菜单")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:edit')")
    @AuditLog(module = "系统管理", action = "修改菜单", businessType = "sys_menu")
    public ApiResponse<SysMenu> update(@PathVariable Long id, @RequestBody SysMenu menu) {
        return ApiResponse.ok(menuService.update(id, menu));
    }

    @Operation(summary = "删除菜单（有子节点或角色引用时拒绝）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:delete')")
    @AuditLog(module = "系统管理", action = "删除菜单", businessType = "sys_menu")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return ApiResponse.ok();
    }
}

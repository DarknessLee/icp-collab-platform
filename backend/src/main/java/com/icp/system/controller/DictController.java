package com.icp.system.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.system.entity.SysDictData;
import com.icp.system.entity.SysDictType;
import com.icp.system.service.DictService;
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
 * 字典管理。权限使用 system:dict:add/edit/delete/list（V2 种子已提供）。
 */
@Tag(name = "字典管理")
@RestController
@RequestMapping("/api/v1/dicts")
public class DictController {

    private final DictService dictService;

    public DictController(DictService dictService) {
        this.dictService = dictService;
    }

    @Operation(summary = "字典类型分页列表")
    @GetMapping("/types")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public ApiResponse<PageResult<SysDictType>> pageTypes(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "100") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(dictService.pageTypes(Math.max(pageNum, 1), clamp(pageSize), keyword, status));
    }

    @Operation(summary = "按类型取字典数据项（下拉用）")
    @GetMapping("/{type}/items")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public ApiResponse<List<SysDictData>> items(@PathVariable String type,
                                                @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(dictService.items(type, status));
    }

    @Operation(summary = "新增字典类型")
    @PostMapping("/types")
    @PreAuthorize("hasAuthority('system:dict:add')")
    @AuditLog(module = "系统管理", action = "新增字典类型", businessType = "sys_dict_type")
    public ApiResponse<SysDictType> createType(@RequestBody SysDictType type) {
        return ApiResponse.ok(dictService.createType(type));
    }

    @Operation(summary = "修改字典类型")
    @PutMapping("/types/{id}")
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @AuditLog(module = "系统管理", action = "修改字典类型", businessType = "sys_dict_type")
    public ApiResponse<SysDictType> updateType(@PathVariable Long id, @RequestBody SysDictType type) {
        return ApiResponse.ok(dictService.updateType(id, type));
    }

    @Operation(summary = "删除字典数据类型")
    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasAuthority('system:dict:delete')")
    @AuditLog(module = "系统管理", action = "删除字典类型", businessType = "sys_dict_type")
    public ApiResponse<Void> deleteType(@PathVariable Long id) {
        dictService.deleteType(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "新增字典数据项")
    @PostMapping("/items")
    @PreAuthorize("hasAuthority('system:dict:add')")
    @AuditLog(module = "系统管理", action = "新增字典数据", businessType = "sys_dict_data")
    public ApiResponse<SysDictData> createItem(@RequestBody SysDictData item) {
        return ApiResponse.ok(dictService.createItem(item));
    }

    @Operation(summary = "修改字典数据项")
    @PutMapping("/items/{id}")
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @AuditLog(module = "系统管理", action = "修改字典数据", businessType = "sys_dict_data")
    public ApiResponse<SysDictData> updateItem(@PathVariable Long id, @RequestBody SysDictData item) {
        return ApiResponse.ok(dictService.updateItem(id, item));
    }

    @Operation(summary = "删除字典数据项")
    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasAuthority('system:dict:delete')")
    @AuditLog(module = "系统管理", action = "删除字典数据", businessType = "sys_dict_data")
    public ApiResponse<Void> deleteItem(@PathVariable Long id) {
        dictService.deleteItem(id);
        return ApiResponse.ok();
    }

    private long clamp(long pageSize) {
        return Math.min(Math.max(pageSize, 1), 100);
    }
}

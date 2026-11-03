package com.icp.system.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.system.entity.SysPost;
import com.icp.system.service.PostService;
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

/**
 * 岗位管理。种子权限只预置了 system:post:list（docs/05），M2a 写操作暂用该权限保护，
 * 待 M2b 补 system:post:add/edit/delete 后切换（已在交付说明中标注差异）。
 */
@Tag(name = "岗位管理")
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @Operation(summary = "岗位分页列表")
    @GetMapping
    @PreAuthorize("hasAuthority('system:post:list')")
    public ApiResponse<PageResult<SysPost>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(postService.page(Math.max(pageNum, 1), clamp(pageSize), keyword, status));
    }

    @Operation(summary = "岗位详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:post:list')")
    public ApiResponse<SysPost> detail(@PathVariable Long id) {
        return ApiResponse.ok(postService.get(id));
    }

    @Operation(summary = "新增岗位")
    @PostMapping
    @PreAuthorize("hasAuthority('system:post:add')")
    @AuditLog(module = "系统管理", action = "新增岗位", businessType = "sys_post")
    public ApiResponse<SysPost> create(@RequestBody SysPost post) {
        return ApiResponse.ok(postService.create(post));
    }

    @Operation(summary = "修改岗位")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:post:edit')")
    @AuditLog(module = "系统管理", action = "修改岗位", businessType = "sys_post")
    public ApiResponse<SysPost> update(@PathVariable Long id, @RequestBody SysPost post) {
        return ApiResponse.ok(postService.update(id, post));
    }

    @Operation(summary = "删除岗位")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:post:delete')")
    @AuditLog(module = "系统管理", action = "删除岗位", businessType = "sys_post")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return ApiResponse.ok();
    }

    private long clamp(long pageSize) {
        return Math.min(Math.max(pageSize, 1), 100);
    }
}

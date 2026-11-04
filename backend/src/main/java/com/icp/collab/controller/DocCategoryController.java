package com.icp.collab.controller;

import com.icp.collab.entity.CollDocCategory;
import com.icp.collab.service.DocCategoryService;
import com.icp.common.api.ApiResponse;
import com.icp.common.audit.AuditLog;
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

@Tag(name = "协同-文档分类")
@RestController
@RequestMapping("/api/v1/doc-categories")
public class DocCategoryController {

    private final DocCategoryService service;

    public DocCategoryController(DocCategoryService service) {
        this.service = service;
    }

    @Operation(summary = "文档分类树")
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('collab:doc-category:list')")
    public ApiResponse<List<CollDocCategory>> tree() {
        return ApiResponse.ok(service.tree());
    }

    @Operation(summary = "文档分类平铺列表")
    @GetMapping
    @PreAuthorize("hasAuthority('collab:doc-category:list')")
    public ApiResponse<List<CollDocCategory>> list() {
        return ApiResponse.ok(service.list());
    }

    @Operation(summary = "文档分类详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:doc-category:list')")
    public ApiResponse<CollDocCategory> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "新增文档分类")
    @PostMapping
    @PreAuthorize("hasAuthority('collab:doc-category:add')")
    @AuditLog(module = "协同运营", action = "新增文档分类", businessType = "coll_doc_category")
    public ApiResponse<CollDocCategory> create(@RequestBody CollDocCategory request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "编辑文档分类")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:doc-category:edit')")
    @AuditLog(module = "协同运营", action = "编辑文档分类", businessType = "coll_doc_category")
    public ApiResponse<CollDocCategory> update(@PathVariable Long id, @RequestBody CollDocCategory request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Operation(summary = "删除文档分类（有子分类/文档时拒绝）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:doc-category:delete')")
    @AuditLog(module = "协同运营", action = "删除文档分类", businessType = "coll_doc_category")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}

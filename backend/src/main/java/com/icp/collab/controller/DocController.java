package com.icp.collab.controller;

import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollDoc;
import com.icp.collab.entity.CollDocPerm;
import com.icp.collab.service.DocService;
import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Tag(name = "协同-文档中心")
@RestController
@RequestMapping("/api/v1/docs")
public class DocController {

    private final DocService service;

    public DocController(DocService service) {
        this.service = service;
    }

    @Operation(summary = "文档分页（按授权可见性过滤）")
    @GetMapping
    @PreAuthorize("hasAuthority('collab:doc:list')")
    public ApiResponse<PageResult<CollDoc>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer docType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNum, pageSize, categoryId, docType, status, keyword));
    }

    @Operation(summary = "文档详情（含当前用户查看/下载权限）")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:doc:get')")
    public ApiResponse<CollDoc> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "新增文档（草稿）")
    @PostMapping
    @PreAuthorize("hasAuthority('collab:doc:add')")
    @AuditLog(module = "协同运营", action = "新增文档", businessType = "coll_doc")
    public ApiResponse<CollDoc> create(@RequestBody CollabRequests.DocCreate request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "编辑文档（附件变更时次版本 +1）")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:doc:edit')")
    @AuditLog(module = "协同运营", action = "编辑文档", businessType = "coll_doc")
    public ApiResponse<CollDoc> update(@PathVariable Long id, @RequestBody CollabRequests.DocUpdate request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Operation(summary = "发布文档（0→1）")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('collab:doc:publish')")
    @AuditLog(module = "协同运营", action = "发布文档", businessType = "coll_doc")
    public ApiResponse<Map<String, Object>> publish(@PathVariable Long id) {
        return ApiResponse.ok(service.publish(id));
    }

    @Operation(summary = "归档文档（1→2）")
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('collab:doc:archive')")
    @AuditLog(module = "协同运营", action = "归档文档", businessType = "coll_doc")
    public ApiResponse<Map<String, Object>> archive(@PathVariable Long id) {
        return ApiResponse.ok(service.archive(id));
    }

    @Operation(summary = "删除文档（逻辑删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:doc:delete')")
    @AuditLog(module = "协同运营", action = "删除文档", businessType = "coll_doc")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "查询文档授权（perm_type 1查看/2下载/3编辑）")
    @GetMapping("/{id}/perms")
    @PreAuthorize("hasAuthority('collab:doc:perm')")
    public ApiResponse<List<CollDocPerm>> perms(@PathVariable Long id) {
        return ApiResponse.ok(service.perms(id));
    }

    @Operation(summary = "全量覆盖文档授权（target_type 0全员/1角色/2用户/3部门）")
    @PutMapping("/{id}/perms")
    @PreAuthorize("hasAuthority('collab:doc:perm')")
    @AuditLog(module = "协同运营", action = "配置文档授权", businessType = "coll_doc_perm")
    public ApiResponse<List<CollDocPerm>> replacePerms(@PathVariable Long id,
                                                       @RequestBody CollabRequests.DocPermPut request) {
        return ApiResponse.ok(service.replacePerms(id, request == null ? List.of() : request.perms()));
    }

    @Operation(summary = "下载文档（按查看/下载授权或已通过借阅校验）")
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('collab:doc:download')")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        DocService.DocFile file = service.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                        + encode(file.fileName()))
                .contentType(mediaType(file.contentType()))
                .contentLength(file.bytes().length)
                .body(file.bytes());
    }

    private String encode(String name) {
        return URLEncoder.encode(name == null ? "document" : name, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private MediaType mediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType == null
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType);
        } catch (RuntimeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}

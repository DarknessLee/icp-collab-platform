package com.icp.collab.controller;

import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollNotice;
import com.icp.collab.service.NoticeService;
import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "协同-公告通知")
@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {

    private final NoticeService service;

    public NoticeController(NoticeService service) {
        this.service = service;
    }

    @Operation(summary = "公告分页（已发布且在范围内，管理员可见全部）")
    @GetMapping
    @PreAuthorize("hasAuthority('collab:notice:list')")
    public ApiResponse<PageResult<CollNotice>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Integer noticeType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNum, pageSize, noticeType, status, keyword));
    }

    @Operation(summary = "公告详情（返回 scope/scopeDeptIds/scopeRoles 与本人已读状态）")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:notice:get')")
    public ApiResponse<CollNotice> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "新增公告（scope: 1全员/2部门/3角色）")
    @PostMapping
    @PreAuthorize("hasAuthority('collab:notice:add')")
    @AuditLog(module = "协同运营", action = "新增公告", businessType = "coll_notice")
    public ApiResponse<CollNotice> create(@RequestBody CollabRequests.NoticeCreate request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "编辑公告（仅草稿）")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:notice:edit')")
    @AuditLog(module = "协同运营", action = "编辑公告", businessType = "coll_notice")
    public ApiResponse<CollNotice> update(@PathVariable Long id, @RequestBody CollabRequests.NoticeUpdate request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Operation(summary = "发布公告（0→1）")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('collab:notice:publish')")
    @AuditLog(module = "协同运营", action = "发布公告", businessType = "coll_notice")
    public ApiResponse<Map<String, Object>> publish(@PathVariable Long id) {
        return ApiResponse.ok(service.publish(id));
    }

    @Operation(summary = "关闭公告（1→2）")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('collab:notice:close')")
    @AuditLog(module = "协同运营", action = "关闭公告", businessType = "coll_notice")
    public ApiResponse<Map<String, Object>> close(@PathVariable Long id) {
        return ApiResponse.ok(service.close(id));
    }

    @Operation(summary = "删除公告（逻辑删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:notice:delete')")
    @AuditLog(module = "协同运营", action = "删除公告", businessType = "coll_notice")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "标记公告已读（幂等）")
    @PostMapping("/{id}/read")
    @PreAuthorize("hasAuthority('collab:notice:read')")
    public ApiResponse<Map<String, Object>> read(@PathVariable Long id) {
        return ApiResponse.ok(service.read(id));
    }

    @Operation(summary = "未读公告数")
    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('collab:notice:read')")
    public ApiResponse<Map<String, Object>> unreadCount() {
        return ApiResponse.ok(service.unreadCount());
    }
}

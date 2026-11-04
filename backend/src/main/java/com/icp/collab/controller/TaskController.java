package com.icp.collab.controller;

import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollTask;
import com.icp.collab.entity.CollTaskComment;
import com.icp.collab.service.TaskService;
import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "协同-任务中心")
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @Operation(summary = "任务分页（数据权限：全员/本部门/本人）")
    @GetMapping
    @PreAuthorize("hasAuthority('collab:task:list')")
    public ApiResponse<PageResult<CollTask>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Integer priority,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate planEnd,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNum, pageSize, assigneeId, priority, status, planEnd, keyword));
    }

    @Operation(summary = "我的任务（scope=mine|created）")
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('collab:task:list')")
    public ApiResponse<PageResult<CollTask>> my(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.my(scope, pageNum, pageSize, status, keyword));
    }

    @Operation(summary = "逾期任务")
    @GetMapping("/overdue")
    @PreAuthorize("hasAuthority('collab:task:list')")
    public ApiResponse<PageResult<CollTask>> overdue(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.ok(service.overdue(pageNum, pageSize));
    }

    @Operation(summary = "任务详情（含评论与子任务）")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:task:get')")
    public ApiResponse<CollTask> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "新增任务")
    @PostMapping
    @PreAuthorize("hasAuthority('collab:task:add')")
    @AuditLog(module = "协同运营", action = "新增任务", businessType = "coll_task")
    public ApiResponse<CollTask> create(@RequestBody CollabRequests.TaskCreate request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "修改任务")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:task:edit')")
    @AuditLog(module = "协同运营", action = "修改任务", businessType = "coll_task")
    public ApiResponse<CollTask> update(@PathVariable Long id, @RequestBody CollabRequests.TaskUpdate request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Operation(summary = "删除任务（仅创建人或管理员，逻辑删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:task:delete')")
    @AuditLog(module = "协同运营", action = "删除任务", businessType = "coll_task")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "开始任务（0→1）")
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('collab:task:edit')")
    @AuditLog(module = "协同运营", action = "开始任务", businessType = "coll_task")
    public ApiResponse<Map<String, Object>> start(@PathVariable Long id) {
        return ApiResponse.ok(service.start(id));
    }

    @Operation(summary = "完成任务（0/1→2）")
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('collab:task:complete')")
    @AuditLog(module = "协同运营", action = "完成任务", businessType = "coll_task")
    public ApiResponse<Map<String, Object>> complete(@PathVariable Long id) {
        return ApiResponse.ok(service.complete(id));
    }

    @Operation(summary = "取消任务（0/1→3）")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('collab:task:cancel')")
    @AuditLog(module = "协同运营", action = "取消任务", businessType = "coll_task")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id) {
        return ApiResponse.ok(service.cancel(id));
    }

    @Operation(summary = "任务评论列表")
    @GetMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('collab:task:list')")
    public ApiResponse<List<CollTaskComment>> comments(@PathVariable Long id) {
        return ApiResponse.ok(service.comments(id));
    }

    @Operation(summary = "新增任务评论")
    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('collab:task:comment')")
    @AuditLog(module = "协同运营", action = "任务评论", businessType = "coll_task_comment")
    public ApiResponse<CollTaskComment> addComment(@PathVariable Long id,
                                                   @RequestBody CollabRequests.CommentAdd request) {
        return ApiResponse.ok(service.addComment(id, request.content()));
    }

    @Operation(summary = "催办任务（生成站内消息）")
    @PostMapping("/{id}/remind")
    @PreAuthorize("hasAuthority('collab:task:remind')")
    @AuditLog(module = "协同运营", action = "任务催办", businessType = "coll_task")
    public ApiResponse<Map<String, Object>> remind(@PathVariable Long id) {
        return ApiResponse.ok(service.remind(id));
    }
}

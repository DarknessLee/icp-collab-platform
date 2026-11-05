package com.icp.collab.controller;

import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollMeeting;
import com.icp.collab.service.MeetingService;
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

@Tag(name = "协同-会议纪要")
@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final MeetingService service;

    public MeetingController(MeetingService service) {
        this.service = service;
    }

    @Operation(summary = "会议分页（按数据权限过滤）")
    @GetMapping
    @PreAuthorize("hasAuthority('collab:meeting:list')")
    public ApiResponse<PageResult<CollMeeting>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String meetingType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ApiResponse.ok(service.page(pageNum, pageSize, keyword, meetingType, status, dateFrom, dateTo));
    }

    @Operation(summary = "会议详情（含纪要正文）")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:meeting:list')")
    public ApiResponse<CollMeeting> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "新增会议")
    @PostMapping
    @PreAuthorize("hasAuthority('collab:meeting:add')")
    @AuditLog(module = "会议纪要", action = "新增会议", businessType = "coll_meeting")
    public ApiResponse<CollMeeting> create(@RequestBody CollabRequests.MeetingCreate request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "编辑会议")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:meeting:edit')")
    @AuditLog(module = "会议纪要", action = "编辑会议", businessType = "coll_meeting")
    public ApiResponse<CollMeeting> update(@PathVariable Long id,
                                           @RequestBody CollabRequests.MeetingUpdate request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Operation(summary = "删除会议（逻辑删除，仅组织者或管理员）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:meeting:remove')")
    @AuditLog(module = "会议纪要", action = "删除会议", businessType = "coll_meeting")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "录入会议纪要（状态置为已召开）")
    @PutMapping("/{id}/minutes")
    @PreAuthorize("hasAuthority('collab:meeting:minutes')")
    @AuditLog(module = "会议纪要", action = "录入会议纪要", businessType = "coll_meeting")
    public ApiResponse<CollMeeting> writeMinutes(@PathVariable Long id,
                                                 @RequestBody CollabRequests.MeetingMinutes request) {
        return ApiResponse.ok(service.writeMinutes(id, request));
    }
}

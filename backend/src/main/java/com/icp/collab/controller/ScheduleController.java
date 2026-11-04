package com.icp.collab.controller;

import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollSchedule;
import com.icp.collab.service.ScheduleService;
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

@Tag(name = "协同-日程会议")
@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    @Operation(summary = "日程分页（本人/参与人/本部门）")
    @GetMapping
    @PreAuthorize("hasAuthority('collab:schedule:list')")
    public ApiResponse<PageResult<CollSchedule>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer scheduleType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNum, pageSize, month, scheduleType, status, keyword));
    }

    @Operation(summary = "日历视图（month=yyyy-MM）")
    @GetMapping("/calendar")
    @PreAuthorize("hasAuthority('collab:schedule:list')")
    public ApiResponse<Map<String, Object>> calendar(@RequestParam(required = false) String month) {
        return ApiResponse.ok(service.calendar(month));
    }

    @Operation(summary = "日程详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:schedule:get')")
    public ApiResponse<CollSchedule> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "新增日程/会议/提醒")
    @PostMapping
    @PreAuthorize("hasAuthority('collab:schedule:add')")
    @AuditLog(module = "协同运营", action = "新增日程", businessType = "coll_schedule")
    public ApiResponse<CollSchedule> create(@RequestBody CollabRequests.ScheduleCreate request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "编辑日程")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:schedule:edit')")
    @AuditLog(module = "协同运营", action = "编辑日程", businessType = "coll_schedule")
    public ApiResponse<CollSchedule> update(@PathVariable Long id,
                                            @RequestBody CollabRequests.ScheduleUpdate request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Operation(summary = "删除日程（逻辑删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('collab:schedule:delete')")
    @AuditLog(module = "协同运营", action = "删除日程", businessType = "coll_schedule")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "取消日程（0/1→3已取消）")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('collab:schedule:cancel')")
    @AuditLog(module = "协同运营", action = "取消日程", businessType = "coll_schedule")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable Long id) {
        return ApiResponse.ok(service.cancel(id));
    }
}

package com.icp.system.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.system.dto.Requests;
import com.icp.system.entity.SysJob;
import com.icp.system.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 定时任务管理。种子权限无 system:job:*，读复用 system:config:list，写复用 system:config:edit；
 * 执行接口本期仅占位（任务描述要求返回 code=0 而非 501）。
 */
@Tag(name = "定时任务")
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @Operation(summary = "任务分页列表")
    @GetMapping
    @PreAuthorize("hasAuthority('system:job:list')")
    public ApiResponse<PageResult<SysJob>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        long size = Math.min(Math.max(pageSize, 1), 100);
        return ApiResponse.ok(jobService.page(Math.max(pageNum, 1), size, keyword, status));
    }

    @Operation(summary = "修改任务状态 0 停止 / 1 运行")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:job:edit')")
    @AuditLog(module = "系统管理", action = "修改任务状态", businessType = "sys_job")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody Requests.Status request) {
        jobService.updateStatus(id, request.status());
        return ApiResponse.ok();
    }

    @Operation(summary = "执行任务（M2a 占位）")
    @PostMapping("/{id}/run")
    @PreAuthorize("hasAuthority('system:job:run')")
    @AuditLog(module = "系统管理", action = "执行任务", businessType = "sys_job")
    public ApiResponse<Map<String, Object>> run(@PathVariable Long id) {
        SysJob job = jobService.run(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId", job.getId());
        data.put("jobName", job.getJobName());
        data.put("note", JobService.NOT_SCHEDULED_NOTE);
        return ApiResponse.ok(data);
    }
}

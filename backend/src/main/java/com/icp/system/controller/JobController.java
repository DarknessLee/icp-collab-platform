package com.icp.system.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.report.service.JobExecutionService;
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

import java.util.Map;

/**
 * 定时任务管理。种子权限沿用 M2a 的 system:job:list / system:job:edit / system:job:run（不新增按钮）；
 * M2e 起执行接口为真实执行（D40），列表返回进程内 lastRunTime / lastResult。
 */
@Tag(name = "定时任务")
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;
    private final JobExecutionService jobExecutionService;

    public JobController(JobService jobService, JobExecutionService jobExecutionService) {
        this.jobService = jobService;
        this.jobExecutionService = jobExecutionService;
    }

    @Operation(summary = "任务分页列表（含进程内最近执行结果）")
    @GetMapping
    @PreAuthorize("hasAuthority('system:job:list')")
    public ApiResponse<PageResult<Map<String, Object>>> page(
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

    @Operation(summary = "执行任务（真实执行，status=0 时跳过）")
    @PostMapping("/{id}/run")
    @PreAuthorize("hasAuthority('system:job:run')")
    @AuditLog(module = "系统管理", action = "执行任务", businessType = "sys_job")
    public ApiResponse<Map<String, Object>> run(@PathVariable Long id) {
        SysJob job = jobService.get(id);
        Map<String, Object> result = jobExecutionService.run(job, true);
        jobService.recordRun(id, result);
        return ApiResponse.ok(result);
    }
}

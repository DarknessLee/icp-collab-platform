package com.icp.icp.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.icp.dto.IcpRequests;
import com.icp.icp.entity.IcpEvaluationTask;
import com.icp.icp.entity.IcpEvaluationWorkpaper;
import com.icp.icp.service.EvaluationTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "内控评价任务")
@RestController
@RequestMapping("/api/v1/evaluation-tasks")
public class EvaluationTaskController {

    private final EvaluationTaskService service;

    public EvaluationTaskController(EvaluationTaskService service) {
        this.service = service;
    }

    @Operation(summary = "评价任务分页")
    @GetMapping
    @PreAuthorize("hasAuthority('icp:evaluation-task:list')")
    public ApiResponse<PageResult<IcpEvaluationTask>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) Long evaluatorId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNum, pageSize, planId, evaluatorId, status, keyword));
    }

    @Operation(summary = "评价任务详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:evaluation-task:get')")
    public ApiResponse<IcpEvaluationTask> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "修改评价任务")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:evaluation-task:edit')")
    @AuditLog(module = "内控管理", action = "修改评价任务", businessType = "icp_evaluation_task")
    public ApiResponse<IcpEvaluationTask> update(@PathVariable Long id, @RequestBody IcpEvaluationTask request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Operation(summary = "开始评价任务")
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('icp:evaluation-task:start')")
    @AuditLog(module = "内控管理", action = "开始评价任务", businessType = "icp_evaluation_task")
    public ApiResponse<IcpEvaluationTask> start(@PathVariable Long id) {
        return ApiResponse.ok(service.start(id));
    }

    @Operation(summary = "提交评价任务")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('icp:evaluation-task:submit')")
    @AuditLog(module = "内控管理", action = "提交评价任务", businessType = "icp_evaluation_task")
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long id,
                                                   @RequestBody IcpRequests.TaskSubmit request) {
        return ApiResponse.ok(service.submit(id, request));
    }

    @Operation(summary = "评价底稿列表")
    @GetMapping("/{id}/workpapers")
    @PreAuthorize("hasAuthority('icp:workpaper:list')")
    public ApiResponse<List<IcpEvaluationWorkpaper>> workpapers(@PathVariable Long id) {
        return ApiResponse.ok(service.workpapers(id));
    }

    @Operation(summary = "新增评价底稿")
    @PostMapping("/{id}/workpapers")
    @PreAuthorize("hasAuthority('icp:workpaper:add')")
    @AuditLog(module = "内控管理", action = "新增评价底稿", businessType = "icp_evaluation_workpaper")
    public ApiResponse<IcpEvaluationWorkpaper> addWorkpaper(@PathVariable Long id,
                                                            @RequestBody IcpRequests.Workpaper request) {
        return ApiResponse.ok(service.addWorkpaper(id, request));
    }
}

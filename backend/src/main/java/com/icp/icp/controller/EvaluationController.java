package com.icp.icp.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.icp.entity.IcpEvaluation;
import com.icp.icp.service.EvaluationService;
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

@Tag(name = "内控评价计划")
@RestController
@RequestMapping("/api/v1/evaluations")
public class EvaluationController {

    private final EvaluationService service;

    public EvaluationController(EvaluationService service) {
        this.service = service;
    }

    @Operation(summary = "评价计划分页")
    @GetMapping
    @PreAuthorize("hasAuthority('icp:evaluation:list')")
    public ApiResponse<PageResult<IcpEvaluation>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer planYear,
            @RequestParam(required = false) Integer planType) {
        return ApiResponse.ok(service.page(pageNum, pageSize, keyword, status, planYear, planType));
    }

    @Operation(summary = "评价计划详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:evaluation:get')")
    public ApiResponse<IcpEvaluation> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "新增评价计划")
    @PostMapping
    @PreAuthorize("hasAuthority('icp:evaluation:add')")
    @AuditLog(module = "内控管理", action = "新增评价计划", businessType = "icp_evaluation")
    public ApiResponse<IcpEvaluation> create(@RequestBody IcpEvaluation request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "修改评价计划")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:evaluation:edit')")
    @AuditLog(module = "内控管理", action = "修改评价计划", businessType = "icp_evaluation")
    public ApiResponse<IcpEvaluation> update(@PathVariable Long id, @RequestBody IcpEvaluation request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Operation(summary = "删除评价计划")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('icp:evaluation:delete')")
    @AuditLog(module = "内控管理", action = "删除评价计划", businessType = "icp_evaluation")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "评价计划送审（M2b 不接流程，pendingWorkflow=true）")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('icp:evaluation:submit')")
    @AuditLog(module = "内控管理", action = "评价计划送审", businessType = "icp_evaluation")
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long id) {
        return ApiResponse.ok(service.submit(id));
    }

    @Operation(summary = "开始评价（按矩阵幂等生成任务）")
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('icp:evaluation:start')")
    @AuditLog(module = "内控管理", action = "开始评价", businessType = "icp_evaluation")
    public ApiResponse<Map<String, Object>> start(@PathVariable Long id) {
        return ApiResponse.ok(service.start(id));
    }

    @Operation(summary = "完成评价")
    @PostMapping("/{id}/finish")
    @PreAuthorize("hasAuthority('icp:evaluation:finish')")
    @AuditLog(module = "内控管理", action = "完成评价", businessType = "icp_evaluation")
    public ApiResponse<Map<String, Object>> finish(@PathVariable Long id) {
        return ApiResponse.ok(service.finish(id));
    }

    @Operation(summary = "关闭评价计划")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('icp:evaluation:close')")
    @AuditLog(module = "内控管理", action = "关闭评价计划", businessType = "icp_evaluation")
    public ApiResponse<Map<String, Object>> close(@PathVariable Long id) {
        return ApiResponse.ok(service.close(id));
    }
}

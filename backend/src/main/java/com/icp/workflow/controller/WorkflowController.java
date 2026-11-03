package com.icp.workflow.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.workflow.dto.WorkflowRequests;
import com.icp.workflow.service.WorkflowBusinessService;
import com.icp.workflow.service.WorkflowDefinitionService;
import com.icp.workflow.service.WorkflowInstanceService;
import com.icp.workflow.service.WorkflowMonitorService;
import com.icp.workflow.service.WorkflowTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 流程中心接口（docs/03 §5 流程中心）。
 *
 * <p>只暴露业务节点/表单/意见，不暴露 ACT_* 原始字段（§5.1 D30-D34）。</p>
 */
@Tag(name = "流程中心")
@RestController
@RequestMapping("/api/v1/workflow")
public class WorkflowController {

    private final WorkflowDefinitionService definitionService;
    private final WorkflowInstanceService instanceService;
    private final WorkflowTaskService taskService;
    private final WorkflowMonitorService monitorService;
    private final WorkflowBusinessService businessService;

    public WorkflowController(WorkflowDefinitionService definitionService,
                              WorkflowInstanceService instanceService,
                              WorkflowTaskService taskService,
                              WorkflowMonitorService monitorService,
                              WorkflowBusinessService businessService) {
        this.definitionService = definitionService;
        this.instanceService = instanceService;
        this.taskService = taskService;
        this.monitorService = monitorService;
        this.businessService = businessService;
    }

    // ------------------------------------------------------------------ 流程定义

    @Operation(summary = "流程定义列表")
    @GetMapping("/processes")
    @PreAuthorize("hasAuthority('workflow:definition:list')")
    public ApiResponse<List<Map<String, Object>>> processes() {
        return ApiResponse.ok(definitionService.listProcesses());
    }

    @Operation(summary = "流程定义 BPMN XML")
    @GetMapping("/processes/{key}/diagram")
    @PreAuthorize("hasAnyAuthority('workflow:definition:list','workflow:todo','workflow:monitor:view')")
    public ApiResponse<Map<String, Object>> diagram(@PathVariable("key") String key) {
        return ApiResponse.ok(definitionService.diagram(key));
    }

    // ------------------------------------------------------------------ 流程实例

    @Operation(summary = "发起流程实例")
    @PostMapping("/instances")
    @PreAuthorize("hasAnyAuthority('workflow:purchase:add','workflow:seal:add','workflow:borrow:add')")
    @AuditLog(module = "流程中心", action = "发起流程", businessType = "wf_business_link")
    public ApiResponse<Map<String, Object>> start(@Valid @RequestBody WorkflowRequests.StartRequest request,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(instanceService.start(request, idempotencyKey));
    }

    @Operation(summary = "流程实例详情")
    @GetMapping("/instances/{id}")
    @PreAuthorize("hasAnyAuthority('workflow:instance:get','workflow:todo','workflow:done','workflow:initiated')")
    public ApiResponse<Map<String, Object>> instance(@PathVariable("id") String id) {
        return ApiResponse.ok(instanceService.detail(id));
    }

    @Operation(summary = "流程实例历史留痕")
    @GetMapping("/instances/{id}/history")
    @PreAuthorize("hasAnyAuthority('workflow:instance:get','workflow:todo','workflow:done','workflow:initiated')")
    public ApiResponse<List<Map<String, Object>>> history(@PathVariable("id") String id) {
        return ApiResponse.ok(instanceService.history(id));
    }

    @Operation(summary = "流程图节点高亮")
    @GetMapping("/instances/{id}/diagram-highlight")
    @PreAuthorize("hasAnyAuthority('workflow:instance:get','workflow:todo','workflow:done','workflow:initiated')")
    public ApiResponse<Map<String, Object>> diagramHighlight(@PathVariable("id") String id) {
        return ApiResponse.ok(instanceService.diagramHighlight(id));
    }

    @Operation(summary = "重新提交被驳回的实例")
    @PostMapping("/instances/{id}/resubmit")
    @PreAuthorize("hasAnyAuthority('workflow:instance:get','workflow:initiated','workflow:todo')")
    @AuditLog(module = "流程中心", action = "重新提交", businessType = "wf_business_link")
    public ApiResponse<Map<String, Object>> resubmit(@PathVariable("id") String id,
                                                     @RequestParam(value = "comment", required = false) String comment) {
        // 兼容空 body / 任意 Content-Type 的调用方：驳回意见通过 query 参数传入（前端如需 body 可继续用 approve 接口的约定）
        return ApiResponse.ok(instanceService.resubmit(id, comment));
    }

    // ------------------------------------------------------------------ 待办 / 已办 / 我发起

    @Operation(summary = "我的待办")
    @GetMapping("/todo")
    @PreAuthorize("hasAuthority('workflow:todo')")
    public ApiResponse<PageResult<Map<String, Object>>> todo(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                                             @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                             @RequestParam(value = "processKey", required = false) String processKey,
                                                             @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(taskService.todo(pageNum, pageSize, processKey, keyword));
    }

    @Operation(summary = "我的已办")
    @GetMapping("/done")
    @PreAuthorize("hasAuthority('workflow:done')")
    public ApiResponse<PageResult<Map<String, Object>>> done(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                                             @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                             @RequestParam(value = "processKey", required = false) String processKey,
                                                             @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(taskService.done(pageNum, pageSize, processKey, keyword));
    }

    @Operation(summary = "我发起的流程")
    @GetMapping("/initiated")
    @PreAuthorize("hasAuthority('workflow:initiated')")
    public ApiResponse<PageResult<Map<String, Object>>> initiated(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                                                  @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                                  @RequestParam(value = "status", required = false) Integer status,
                                                                  @RequestParam(value = "processKey", required = false) String processKey,
                                                                  @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(instanceService.initiated(pageNum, pageSize, processKey, status, keyword));
    }

    // ------------------------------------------------------------------ 任务办理

    @Operation(summary = "任务详情")
    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyAuthority('workflow:todo','workflow:done','workflow:instance:get')")
    public ApiResponse<Map<String, Object>> task(@PathVariable("taskId") String taskId) {
        return ApiResponse.ok(taskService.taskDetail(taskId));
    }

    @Operation(summary = "同意")
    @PostMapping("/tasks/{taskId}/approve")
    @PreAuthorize("hasAuthority('workflow:task:approve')")
    @AuditLog(module = "流程中心", action = "同意", businessType = "wf_business_link")
    public ApiResponse<Map<String, Object>> approve(@PathVariable("taskId") String taskId,
                                                    @RequestBody(required = false) WorkflowRequests.ApproveRequest request) {
        return ApiResponse.ok(taskService.approve(taskId, request == null ? new WorkflowRequests.ApproveRequest(null, null, null) : request));
    }

    @Operation(summary = "驳回")
    @PostMapping("/tasks/{taskId}/reject")
    @PreAuthorize("hasAuthority('workflow:task:reject')")
    @AuditLog(module = "流程中心", action = "驳回", businessType = "wf_business_link")
    public ApiResponse<Map<String, Object>> reject(@PathVariable("taskId") String taskId,
                                                   @Valid @RequestBody WorkflowRequests.RejectRequest request) {
        return ApiResponse.ok(taskService.reject(taskId, request));
    }

    @Operation(summary = "催办")
    @PostMapping("/tasks/{taskId}/urge")
    @PreAuthorize("hasAuthority('workflow:task:urge')")
    @AuditLog(module = "流程中心", action = "催办", businessType = "wf_task_urge")
    public ApiResponse<Map<String, Object>> urge(@PathVariable("taskId") String taskId,
                                                 @RequestBody(required = false) WorkflowRequests.UrgeRequest request) {
        String content = request == null ? null : request.content();
        return ApiResponse.ok(taskService.urge(taskId, request == null ? new WorkflowRequests.UrgeRequest(content) : request));
    }

    // ------------------------------------------------------------------ 流程监控

    @Operation(summary = "流程监控")
    @GetMapping("/monitor")
    @PreAuthorize("hasAuthority('workflow:monitor:view')")
    public ApiResponse<Map<String, Object>> monitor(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                                    @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                    @RequestParam(value = "status", required = false) String status,
                                                    @RequestParam(value = "overdue", required = false) Boolean overdue,
                                                    @RequestParam(value = "processKey", required = false) String processKey,
                                                    @RequestParam(value = "keyword", required = false) String keyword,
                                                    @RequestParam(value = "timeoutHours", required = false) Integer timeoutHours) {
        PageResult<Map<String, Object>> page = monitorService.monitor(pageNum, pageSize, status, overdue, processKey, keyword, timeoutHours);
        Map<String, Object> data = new java.util.LinkedHashMap<>(monitorService.stats(timeoutHours));
        data.put("list", page.getList());
        data.put("total", page.getTotal());
        data.put("pageNum", page.getPageNum());
        data.put("pageSize", page.getPageSize());
        return ApiResponse.ok(data);
    }

    // ------------------------------------------------------------------ 采购申请

    @Operation(summary = "采购申请列表")
    @GetMapping("/purchase")
    @PreAuthorize("hasAuthority('workflow:purchase:list')")
    public ApiResponse<PageResult<Map<String, Object>>> purchases(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                                                  @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                                  @RequestParam(value = "status", required = false) Integer status,
                                                                  @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(businessService.purchases(pageNum, pageSize, status, keyword));
    }

    @Operation(summary = "采购申请详情")
    @GetMapping("/purchase/{id}")
    @PreAuthorize("hasAnyAuthority('workflow:purchase:list','workflow:purchase:get')")
    public ApiResponse<Map<String, Object>> purchase(@PathVariable("id") Long id) {
        return ApiResponse.ok(businessService.purchase(id));
    }

    @Operation(summary = "创建采购申请")
    @PostMapping("/purchase")
    @PreAuthorize("hasAuthority('workflow:purchase:add')")
    @AuditLog(module = "流程中心", action = "创建采购申请", businessType = "wf_purchase")
    public ApiResponse<Map<String, Object>> createPurchase(@Valid @RequestBody WorkflowRequests.PurchaseRequest request) {
        return ApiResponse.ok(businessService.createPurchase(request));
    }

    @Operation(summary = "提交采购申请")
    @PostMapping("/purchase/{id}/submit")
    @PreAuthorize("hasAuthority('workflow:purchase:add')")
    @AuditLog(module = "流程中心", action = "提交采购申请", businessType = "wf_purchase")
    public ApiResponse<Map<String, Object>> submitPurchase(@PathVariable("id") Long id) {
        return ApiResponse.ok(businessService.submitPurchase(id));
    }

    // ------------------------------------------------------------------ 用印申请

    @Operation(summary = "用印申请列表")
    @GetMapping("/seal")
    @PreAuthorize("hasAuthority('workflow:seal:list')")
    public ApiResponse<PageResult<Map<String, Object>>> seals(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                                              @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                              @RequestParam(value = "status", required = false) Integer status,
                                                              @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(businessService.seals(pageNum, pageSize, status, keyword));
    }

    @Operation(summary = "用印申请详情")
    @GetMapping("/seal/{id}")
    @PreAuthorize("hasAnyAuthority('workflow:seal:list','workflow:seal:get')")
    public ApiResponse<Map<String, Object>> seal(@PathVariable("id") Long id) {
        return ApiResponse.ok(businessService.seal(id));
    }

    @Operation(summary = "创建用印申请")
    @PostMapping("/seal")
    @PreAuthorize("hasAuthority('workflow:seal:add')")
    @AuditLog(module = "流程中心", action = "创建用印申请", businessType = "wf_seal")
    public ApiResponse<Map<String, Object>> createSeal(@Valid @RequestBody WorkflowRequests.SealRequest request) {
        return ApiResponse.ok(businessService.createSeal(request));
    }

    @Operation(summary = "提交用印申请")
    @PostMapping("/seal/{id}/submit")
    @PreAuthorize("hasAuthority('workflow:seal:add')")
    @AuditLog(module = "流程中心", action = "提交用印申请", businessType = "wf_seal")
    public ApiResponse<Map<String, Object>> submitSeal(@PathVariable("id") Long id) {
        return ApiResponse.ok(businessService.submitSeal(id));
    }

    // ------------------------------------------------------------------ 文档借阅

    @Operation(summary = "文档借阅申请列表")
    @GetMapping("/borrows")
    @PreAuthorize("hasAnyAuthority('workflow:borrow:list','workflow:purchase:list')")
    public ApiResponse<PageResult<Map<String, Object>>> borrows(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                                                @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                                @RequestParam(value = "status", required = false) Integer status,
                                                                @RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(businessService.borrows(pageNum, pageSize, status, keyword));
    }

    @Operation(summary = "文档借阅详情")
    @GetMapping("/borrows/{id}")
    @PreAuthorize("hasAnyAuthority('workflow:borrow:get','workflow:borrow:list','workflow:purchase:list')")
    public ApiResponse<Map<String, Object>> borrow(@PathVariable("id") Long id) {
        return ApiResponse.ok(businessService.borrow(id));
    }

    @Operation(summary = "创建文档借阅申请")
    @PostMapping("/borrows")
    @PreAuthorize("hasAnyAuthority('workflow:borrow:add','workflow:purchase:add','workflow:seal:add')")
    @AuditLog(module = "流程中心", action = "创建借阅申请", businessType = "coll_doc_borrow")
    public ApiResponse<Map<String, Object>> createBorrow(@Valid @RequestBody WorkflowRequests.BorrowRequest request) {
        return ApiResponse.ok(businessService.createBorrow(request));
    }
}

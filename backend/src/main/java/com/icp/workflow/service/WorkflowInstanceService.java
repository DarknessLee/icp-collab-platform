package com.icp.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icp.collab.mapper.CollDocMapper;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.cache.CacheService;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.icp.entity.IcpDefect;
import com.icp.icp.entity.IcpEvaluation;
import com.icp.icp.entity.IcpPolicy;
import com.icp.icp.entity.IcpRectify;
import com.icp.icp.mapper.IcpDefectMapper;
import com.icp.icp.mapper.IcpEvaluationMapper;
import com.icp.icp.mapper.IcpPolicyMapper;
import com.icp.icp.mapper.IcpRectifyMapper;
import com.icp.icp.support.CodeSeq;
import com.icp.icp.support.IcpLookupService;
import com.icp.system.entity.SysDept;
import com.icp.system.entity.SysFile;
import com.icp.system.entity.SysUser;
import com.icp.system.mapper.SysDeptMapper;
import com.icp.system.mapper.SysFileMapper;
import com.icp.system.mapper.SysUserMapper;
import com.icp.system.service.ConfigService;
import com.icp.system.service.DataScopeService;
import com.icp.workflow.dto.WorkflowRequests;
import com.icp.workflow.entity.CollDocBorrow;
import com.icp.workflow.entity.WfBusinessLink;
import com.icp.workflow.entity.WfForm;
import com.icp.workflow.entity.WfProcessMeta;
import com.icp.workflow.entity.WfPurchase;
import com.icp.workflow.entity.WfSeal;
import com.icp.workflow.mapper.CollDocBorrowMapper;
import com.icp.workflow.mapper.WfBusinessLinkMapper;
import com.icp.workflow.mapper.WfFormMapper;
import com.icp.workflow.mapper.WfProcessMetaMapper;
import com.icp.workflow.mapper.WfPurchaseMapper;
import com.icp.workflow.mapper.WfSealMapper;
import com.icp.workflow.support.WorkflowConstants;
import com.icp.workflow.support.WorkflowUserResolver;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 流程实例服务：发起、详情、历史、节点高亮、重新提交、我发起的（docs/03 §5 / §5.1）。
 *
 * <p>发起时把 formData、发起人、候选审批组写入流程变量，并落 wf_business_link；
 * 业务单据的 flow_instance_id 回写，审批结束由 {@link #onApproved} 回调业务状态。</p>
 */
@Service
public class WorkflowInstanceService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowInstanceService.class);
    private static final String IDEM_PREFIX = "wf:idem:";

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final ObjectMapper objectMapper;
    private final WfBusinessLinkMapper linkMapper;
    private final WfFormMapper formMapper;
    private final WfProcessMetaMapper processMetaMapper;
    private final WfPurchaseMapper purchaseMapper;
    private final WfSealMapper sealMapper;
    private final CollDocBorrowMapper borrowMapper;
    private final IcpPolicyMapper policyMapper;
    private final IcpEvaluationMapper evaluationMapper;
    private final IcpRectifyMapper rectifyMapper;
    private final IcpDefectMapper defectMapper;
    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysFileMapper sysFileMapper;
    private final IcpLookupService icpLookupService;
    private final CollDocMapper collDocMapper;
    private final WorkflowUserResolver userResolver;
    private final DataScopeService dataScopeService;
    private final ConfigService configService;
    private final CacheService cacheService;

    public WorkflowInstanceService(RuntimeService runtimeService,
                                   TaskService taskService,
                                   HistoryService historyService,
                                   ObjectMapper objectMapper,
                                   WfBusinessLinkMapper linkMapper,
                                   WfFormMapper formMapper,
                                   WfProcessMetaMapper processMetaMapper,
                                   WfPurchaseMapper purchaseMapper,
                                   WfSealMapper sealMapper,
                                   CollDocBorrowMapper borrowMapper,
                                   IcpPolicyMapper policyMapper,
                                   IcpEvaluationMapper evaluationMapper,
                                   IcpRectifyMapper rectifyMapper,
                                   IcpDefectMapper defectMapper,
                                   SysUserMapper sysUserMapper,
                                   SysDeptMapper sysDeptMapper,
                                   SysFileMapper sysFileMapper,
                                   IcpLookupService icpLookupService,
                                   CollDocMapper collDocMapper,
                                   WorkflowUserResolver userResolver,
                                   DataScopeService dataScopeService,
                                   ConfigService configService,
                                   CacheService cacheService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.objectMapper = objectMapper;
        this.linkMapper = linkMapper;
        this.formMapper = formMapper;
        this.processMetaMapper = processMetaMapper;
        this.purchaseMapper = purchaseMapper;
        this.sealMapper = sealMapper;
        this.borrowMapper = borrowMapper;
        this.policyMapper = policyMapper;
        this.evaluationMapper = evaluationMapper;
        this.rectifyMapper = rectifyMapper;
        this.defectMapper = defectMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysDeptMapper = sysDeptMapper;
        this.sysFileMapper = sysFileMapper;
        this.icpLookupService = icpLookupService;
        this.collDocMapper = collDocMapper;
        this.userResolver = userResolver;
        this.dataScopeService = dataScopeService;
        this.configService = configService;
        this.cacheService = cacheService;
    }

    // ------------------------------------------------------------------ 发起

    /**
     * 发起流程。支持 Idempotency-Key：同一 key 重复提交只生成一个实例。
     */
    @Transactional
    public Map<String, Object> start(WorkflowRequests.StartRequest request, String idempotencyKey) {
        if (request == null || request.processKey() == null || !WorkflowConstants.isKnown(request.processKey())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未知的流程类型");
        }
        Long userId = userResolver.currentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String cacheKey = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            cacheKey = IDEM_PREFIX + idempotencyKey.trim();
            Object cached = cacheService.get(cacheKey).orElse(null);
            if (cached instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) map;
                return data;
            }
        }

        String processKey = request.processKey();
        String businessType = request.businessType() == null || request.businessType().isBlank()
                ? WorkflowConstants.BUSINESS_TYPES.get(processKey) : request.businessType();
        Map<String, Object> formData = request.formData() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request.formData());

        BusinessRef ref = resolveBusiness(processKey, request, formData, userId);

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put(WorkflowConstants.VAR_INITIATOR, String.valueOf(userId));
        variables.put(WorkflowConstants.VAR_INITIATOR_NAME, userResolver.currentRealName());
        variables.put(WorkflowConstants.VAR_APPROVER_GROUPS, WorkflowConstants.APPROVER_GROUPS.get(processKey));
        variables.put(WorkflowConstants.VAR_FORM_DATA, toJson(formData));
        variables.put(WorkflowConstants.VAR_BUSINESS_NO, ref.businessNo());
        variables.put(WorkflowConstants.VAR_TITLE, ref.title());
        variables.put(WorkflowConstants.VAR_PROCESS_KEY, processKey);
        variables.put("businessId", ref.businessId());
        variables.put("businessType", businessType);
        if (WorkflowConstants.PURCHASE_APPLY.equals(processKey)) {
            variables.put(WorkflowConstants.VAR_PURCHASE_AMOUNT, purchaseAmount(formData));
        }

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(processKey, ref.businessNo(), variables);
        String processInstanceId = instance.getProcessInstanceId();

        WfBusinessLink link = new WfBusinessLink();
        link.setProcessInstanceId(processInstanceId);
        link.setProcessKey(processKey);
        link.setBusinessType(businessType);
        link.setBusinessId(ref.businessId());
        link.setBusinessNo(ref.businessNo());
        link.setTitle(ref.title());
        link.setFormCode(processKey);
        link.setInitiatorId(userId);
        link.setStatus(WorkflowConstants.LINK_RUNNING);
        link.setStartTime(LocalDateTime.now());
        linkMapper.insert(link);

        markBusinessStarted(processKey, ref.businessId(), processInstanceId);

        Task current = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processInstanceId", processInstanceId);
        result.put("taskId", current == null ? null : current.getId());
        result.put("businessId", ref.businessId());
        result.put("businessNo", ref.businessNo());
        result.put("processKey", processKey);
        result.put("title", ref.title());
        result.put("status", WorkflowConstants.LINK_RUNNING);
        result.put("pendingWorkflow", false);
        if (cacheKey != null) {
            cacheService.put(cacheKey, result, 86400);
        }
        return result;
    }

    private BusinessRef resolveBusiness(String processKey,
                                        WorkflowRequests.StartRequest request,
                                        Map<String, Object> formData, Long userId) {
        Long businessId = request.businessId();
        String prefix = WorkflowConstants.TITLE_PREFIX.get(processKey);
        String title = request.title() == null ? null : request.title().trim();
        switch (processKey) {
            case WorkflowConstants.POLICY_PUBLISH -> {
                IcpPolicy policy = requireBusiness(businessId, policyMapper.selectById(businessId), "制度");
                checkScope(policy.getOwnerDeptId(), policy.getOwnerUserId());
                if (policy.getStatus() == null || policy.getStatus() != 0) {
                    throw new BusinessException(ErrorCode.STATE_INVALID, "仅草稿状态的制度可送审");
                }
                formData.putIfAbsent("policyCode", policy.getPolicyCode());
                formData.putIfAbsent("policyName", policy.getPolicyName());
                return new BusinessRef(policy.getId(), policy.getPolicyCode(),
                        title != null ? title : prefix + "-" + policy.getPolicyName(), policy.getOwnerDeptId());
            }
            case WorkflowConstants.EVALUATION_PLAN -> {
                IcpEvaluation evaluation = requireBusiness(businessId, evaluationMapper.selectById(businessId), "评价计划");
                checkScope(evaluation.getOwnerDeptId(), evaluation.getOwnerUserId());
                if (evaluation.getStatus() == null || evaluation.getStatus() != 0) {
                    throw new BusinessException(ErrorCode.STATE_INVALID, "仅草稿状态的评价计划可送审");
                }
                formData.putIfAbsent("planCode", evaluation.getPlanCode());
                formData.putIfAbsent("planName", evaluation.getPlanName());
                return new BusinessRef(evaluation.getId(), evaluation.getPlanCode(),
                        title != null ? title : prefix + "-" + evaluation.getPlanName(), evaluation.getOwnerDeptId());
            }
            case WorkflowConstants.DEFECT_RECTIFY -> {
                IcpRectify rectify = requireBusiness(businessId, rectifyMapper.selectById(businessId), "整改记录");
                checkScope(rectify.getDeptId(), rectify.getResponsibleUserId());
                if (rectify.getStatus() != null && rectify.getStatus() != 0 && rectify.getStatus() != 3) {
                    throw new BusinessException(ErrorCode.STATE_INVALID, "仅待整改/已驳回的整改记录可提交");
                }
                IcpDefect defect = rectify.getDefectId() == null ? null : defectMapper.selectById(rectify.getDefectId());
                formData.putIfAbsent("rectifyCode", rectify.getRectifyCode());
                formData.putIfAbsent("defectCode", defect == null ? null : defect.getDefectCode());
                return new BusinessRef(rectify.getId(), rectify.getRectifyCode(),
                        title != null ? title : prefix + "-" + (defect == null ? rectify.getRectifyCode() : defect.getDefectName()),
                        rectify.getDeptId());
            }
            case WorkflowConstants.PURCHASE_APPLY -> {
                if (businessId != null) {
                    WfPurchase purchase = requireBusiness(businessId, purchaseMapper.selectById(businessId), "采购申请");
                    checkScope(purchase.getDeptId(), purchase.getApplicantId());
                    if (purchase.getStatus() != null && purchase.getStatus() != 0) {
                        throw new BusinessException(ErrorCode.STATE_INVALID, "仅草稿状态的采购申请可提交");
                    }
                    return new BusinessRef(purchase.getId(), purchase.getApplyNo(),
                            title != null ? title : prefix + "-" + purchase.getTitle(), purchase.getDeptId());
                }
                WfPurchase purchase = createPurchase(formData, userId, title);
                return new BusinessRef(purchase.getId(), purchase.getApplyNo(), prefix + "-" + purchase.getTitle(), purchase.getDeptId());
            }
            case WorkflowConstants.SEAL_APPLY -> {
                if (businessId != null) {
                    WfSeal seal = requireBusiness(businessId, sealMapper.selectById(businessId), "用印申请");
                    checkScope(seal.getDeptId(), seal.getApplicantId());
                    if (seal.getStatus() != null && seal.getStatus() != 0) {
                        throw new BusinessException(ErrorCode.STATE_INVALID, "仅草稿状态的用印申请可提交");
                    }
                    return new BusinessRef(seal.getId(), seal.getApplyNo(),
                            title != null ? title : prefix + "-" + seal.getReason(), seal.getDeptId());
                }
                WfSeal seal = createSeal(formData, userId, title);
                return new BusinessRef(seal.getId(), seal.getApplyNo(), prefix + "-" + seal.getReason(), seal.getDeptId());
            }
            case WorkflowConstants.DOC_BORROW -> {
                if (businessId != null) {
                    CollDocBorrow borrow = requireBusiness(businessId, borrowMapper.selectById(businessId), "借阅申请");
                    checkScope(null, borrow.getBorrowerId());
                    if (borrow.getStatus() != null && borrow.getStatus() != 0) {
                        throw new BusinessException(ErrorCode.STATE_INVALID, "仅申请中的借阅记录可提交");
                    }
                    return new BusinessRef(borrow.getId(), "JY-" + borrow.getId(),
                            title != null ? title : prefix + "-" + borrow.getReason() + "-" + borrow.getId(), null);
                }
                CollDocBorrow borrow = createBorrow(formData, userId);
                return new BusinessRef(borrow.getId(), "JY-" + borrow.getId(),
                        title != null ? title : prefix + "-" + borrow.getReason() + "-" + borrow.getId(), null);
            }
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "未知的流程类型");
        }
    }

    private WfPurchase createPurchase(Map<String, Object> formData, Long userId, String title) {
        String purchaseTitle = title != null ? title : str(formData.get("title"));
        if (purchaseTitle == null || purchaseTitle.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "采购标题不能为空");
        }
        BigDecimal amount = decimal(formData.get("amount"));
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "采购金额必须大于 0");
        }
        BigDecimal budget = decimal(formData.get("budgetAmount"));
        if (budget != null && amount.compareTo(budget) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "采购金额不能超过预算金额");
        }
        WfPurchase purchase = new WfPurchase();
        purchase.setApplyNo(CodeSeq.next(purchaseMapper.selectMaxCode(), "CG", 3));
        purchase.setTitle(purchaseTitle);
        purchase.setDeptId(formData.get("deptId") == null ? currentDeptId(userId) : longVal(formData.get("deptId")));
        purchase.setApplicantId(userId);
        purchase.setAmount(amount);
        purchase.setBudgetAmount(budget);
        purchase.setSupplier(str(formData.get("supplier")));
        purchase.setPurchaseType(formData.get("purchaseType") == null ? 1 : intVal(formData.get("purchaseType")));
        purchase.setPurpose(str(formData.get("purpose")));
        purchase.setApplyDate(formData.get("applyDate") == null ? LocalDate.now() : LocalDate.parse(str(formData.get("applyDate"))));
        purchase.setStatus(0);
        purchaseMapper.insert(purchase);
        formData.putIfAbsent("applyNo", purchase.getApplyNo());
        formData.putIfAbsent("budgetAmount", budget);
        return purchase;
    }

    private WfSeal createSeal(Map<String, Object> formData, Long userId, String title) {
        String reason = title != null ? title : str(formData.get("reason"));
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用印事由不能为空");
        }
        Long fileId = formData.get("fileId") == null ? null : longVal(formData.get("fileId"));
        if (fileId != null) {
            SysFile file = sysFileMapper.selectById(fileId);
            if (file == null || (file.getDeleted() != null && file.getDeleted() == 1)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "附件不存在: " + fileId);
            }
        }
        WfSeal seal = new WfSeal();
        seal.setApplyNo(CodeSeq.next(sealMapper.selectMaxCode(), "YY", 3));
        seal.setSealType(formData.get("sealType") == null ? 1 : intVal(formData.get("sealType")));
        seal.setReason(reason);
        seal.setUseDate(formData.get("useDate") == null ? LocalDate.now() : LocalDate.parse(str(formData.get("useDate"))));
        seal.setFileId(fileId);
        seal.setApplicantId(userId);
        seal.setDeptId(formData.get("deptId") == null ? currentDeptId(userId) : longVal(formData.get("deptId")));
        seal.setStatus(0);
        sealMapper.insert(seal);
        formData.putIfAbsent("applyNo", seal.getApplyNo());
        return seal;
    }

    private CollDocBorrow createBorrow(Map<String, Object> formData, Long userId) {
        Long docId = formData.get("docId") == null ? null : longVal(formData.get("docId"));
        if (docId == null || borrowMapper.countDoc(docId) == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "借阅文档不存在");
        }
        String reason = str(formData.get("reason"));
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "借阅原因不能为空");
        }
        CollDocBorrow borrow = new CollDocBorrow();
        borrow.setDocId(docId);
        borrow.setBorrowerId(userId);
        borrow.setReason(reason);
        borrow.setStartDate(formData.get("startDate") == null ? LocalDate.now() : LocalDate.parse(str(formData.get("startDate"))));
        borrow.setEndDate(formData.get("endDate") == null ? LocalDate.now().plusDays(7) : LocalDate.parse(str(formData.get("endDate"))));
        if (borrow.getEndDate().isBefore(borrow.getStartDate())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "借阅结束日期不能早于开始日期");
        }
        borrow.setStatus(0);
        borrowMapper.insert(borrow);
        formData.putIfAbsent("docId", docId);
        return borrow;
    }

    private void markBusinessStarted(String processKey, Long businessId, String processInstanceId) {
        switch (processKey) {
            case WorkflowConstants.POLICY_PUBLISH -> {
                IcpPolicy policy = policyMapper.selectById(businessId);
                policy.setStatus(1);
                policy.setFlowInstanceId(processInstanceId);
                policyMapper.updateById(policy);
            }
            case WorkflowConstants.EVALUATION_PLAN -> {
                IcpEvaluation evaluation = evaluationMapper.selectById(businessId);
                evaluation.setStatus(1);
                evaluation.setFlowInstanceId(processInstanceId);
                evaluationMapper.updateById(evaluation);
            }
            case WorkflowConstants.DEFECT_RECTIFY -> {
                IcpRectify rectify = rectifyMapper.selectById(businessId);
                rectify.setStatus(1);
                rectify.setFlowInstanceId(processInstanceId);
                rectifyMapper.updateById(rectify);
            }
            case WorkflowConstants.PURCHASE_APPLY -> {
                WfPurchase purchase = purchaseMapper.selectById(businessId);
                purchase.setStatus(1);
                purchase.setFlowInstanceId(processInstanceId);
                purchaseMapper.updateById(purchase);
            }
            case WorkflowConstants.SEAL_APPLY -> {
                WfSeal seal = sealMapper.selectById(businessId);
                seal.setStatus(1);
                seal.setFlowInstanceId(processInstanceId);
                sealMapper.updateById(seal);
            }
            case WorkflowConstants.DOC_BORROW -> {
                CollDocBorrow borrow = borrowMapper.selectById(businessId);
                borrow.setStatus(0);
                borrow.setFlowInstanceId(processInstanceId);
                borrowMapper.updateById(borrow);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ 查询

    public Map<String, Object> detail(String processInstanceId) {
        WfBusinessLink link = requireLink(processInstanceId);
        checkAccess(link);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("processInstanceId", link.getProcessInstanceId());
        data.put("processKey", link.getProcessKey());
        WfProcessMeta meta = processMetaMapper.selectByProcessKey(link.getProcessKey());
        data.put("processName", meta == null ? WorkflowConstants.PROCESS_NAMES.get(link.getProcessKey()) : meta.getProcessName());
        data.put("businessType", link.getBusinessType());
        data.put("businessId", link.getBusinessId());
        data.put("businessNo", link.getBusinessNo());
        data.put("title", link.getTitle());
        data.put("formCode", link.getFormCode());
        FormDisplay form = displayForm(link);
        data.put("formSchema", form.schema());
        data.put("formData", form.data());
        data.put("status", link.getStatus());
        data.put("statusLabel", statusLabel(link.getStatus()));
        data.put("initiatorId", link.getInitiatorId());
        data.put("initiatorName", userResolver.nameOf(link.getInitiatorId()));
        data.put("startTime", link.getStartTime());
        data.put("endTime", link.getEndTime());
        data.put("business", form.business());
        Task current = currentTask(link.getProcessInstanceId());
        Map<String, Object> node = new LinkedHashMap<>();
        if (current != null) {
            node.put("taskId", current.getId());
            node.put("nodeName", current.getName());
            node.put("activityId", current.getTaskDefinitionKey());
            node.put("assignee", current.getAssignee());
            node.put("assigneeName", userResolver.nameOf(parseLong(current.getAssignee())));
            node.put("createTime", toLocal(current.getCreateTime()));
            node.put("dueTime", dueTime(link));
        }
        data.put("currentTask", node);
        data.put("currentNode", current == null ? null : current.getName());
        data.put("attachments", attachments(link.getProcessInstanceId()));
        return data;
    }

    public List<Map<String, Object>> history(String processInstanceId) {
        WfBusinessLink link = requireLink(processInstanceId);
        checkAccess(link);
        List<HistoricActivityInstance> activities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();
        Map<String, org.flowable.engine.task.Comment> commentByTask = new HashMap<>();
        List<org.flowable.engine.task.Comment> comments = taskService.getProcessInstanceComments(processInstanceId);
        if (comments != null) {
            for (org.flowable.engine.task.Comment comment : comments) {
                if (comment.getTaskId() != null) {
                    commentByTask.put(comment.getTaskId(), comment);
                }
            }
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (HistoricActivityInstance activity : activities) {
            String activityType = activity.getActivityType();
            // 只保留业务可见节点（发起/用户任务/结束），过滤 sequenceFlow 等连线记录
            if (!"startEvent".equals(activityType) && !"userTask".equals(activityType) && !"endEvent".equals(activityType)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("activityId", activity.getActivityId());
            item.put("activityType", activityType);
            item.put("nodeName", activity.getActivityName());
            String assignee = activity.getAssignee();
            item.put("assignee", assignee);
            item.put("assigneeName", userResolver.nameOf(parseLong(assignee)));
            item.put("startTime", toLocal(activity.getStartTime()));
            item.put("endTime", toLocal(activity.getEndTime()));
            item.put("durationSeconds", activity.getDurationInMillis() == null ? null : activity.getDurationInMillis() / 1000);
            org.flowable.engine.task.Comment comment = activity.getTaskId() == null ? null : commentByTask.get(activity.getTaskId());
            if (comment != null) {
                item.put("action", comment.getType());
                item.put("actionLabel", actionLabel(comment.getType()));
                item.put("comment", comment.getFullMessage());
                item.put("commentTime", toLocal(comment.getTime()));
                item.put("commentUserId", comment.getUserId());
                if (assignee == null) {
                    // 驳回后 historical activity 不再带 assignee，用意见记录人兜底
                    item.put("assignee", comment.getUserId());
                    item.put("assigneeName", userResolver.nameOf(parseLong(comment.getUserId())));
                }
            } else if ("startEvent".equals(activityType)) {
                item.put("action", "start");
                item.put("actionLabel", "发起");
            } else if ("endEvent".equals(activityType)) {
                item.put("action", "end");
                item.put("actionLabel", "结束");
            } else if (activity.getEndTime() == null) {
                item.put("action", "running");
                item.put("actionLabel", "办理中");
            }
            list.add(item);
        }
        return list;
    }

    public Map<String, Object> diagramHighlight(String processInstanceId) {
        WfBusinessLink link = requireLink(processInstanceId);
        checkAccess(link);
        Task current = currentTask(processInstanceId);
        List<String> finished = new ArrayList<>();
        List<HistoricActivityInstance> activities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId).orderByHistoricActivityInstanceStartTime().asc().list();
        String lastFinished = null;
        for (HistoricActivityInstance activity : activities) {
            if (activity.getEndTime() != null) {
                finished.add(activity.getActivityId());
                lastFinished = activity.getActivityId();
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("processInstanceId", processInstanceId);
        data.put("activityId", current != null ? current.getTaskDefinitionKey() : lastFinished);
        data.put("currentActivityId", current != null ? current.getTaskDefinitionKey() : lastFinished);
        data.put("finishedActivityIds", finished);
        return data;
    }

    public PageResult<Map<String, Object>> initiated(int pageNum, int pageSize, String processKey,
                                                     Integer status, String keyword) {
        Long userId = userResolver.currentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        LambdaQueryWrapper<WfBusinessLink> wrapper = new LambdaQueryWrapper<WfBusinessLink>()
                .eq(WfBusinessLink::getInitiatorId, userId);
        if (processKey != null && !processKey.isBlank()) {
            wrapper.eq(WfBusinessLink::getProcessKey, processKey);
        }
        if (status != null) {
            wrapper.eq(WfBusinessLink::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(WfBusinessLink::getTitle, keyword).or().like(WfBusinessLink::getBusinessNo, keyword));
        }
        wrapper.orderByDesc(WfBusinessLink::getStartTime);
        long total = linkMapper.selectCount(wrapper);
        int offset = Math.max(0, (pageNum - 1) * pageSize);
        wrapper.last("LIMIT " + pageSize + " OFFSET " + offset);
        List<WfBusinessLink> rows = linkMapper.selectList(wrapper);
        List<Map<String, Object>> list = new ArrayList<>();
        for (WfBusinessLink row : rows) {
            list.add(linkItem(row));
        }
        return new PageResult<>(list, total, pageNum, pageSize);
    }

    public Map<String, Object> linkItem(WfBusinessLink link) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("processInstanceId", link.getProcessInstanceId());
        item.put("processKey", link.getProcessKey());
        WfProcessMeta meta = processMetaMapper.selectByProcessKey(link.getProcessKey());
        item.put("processName", meta == null ? WorkflowConstants.PROCESS_NAMES.get(link.getProcessKey()) : meta.getProcessName());
        item.put("businessType", link.getBusinessType());
        item.put("businessId", link.getBusinessId());
        item.put("businessNo", link.getBusinessNo());
        item.put("title", link.getTitle());
        item.put("status", link.getStatus());
        item.put("statusLabel", statusLabel(link.getStatus()));
        item.put("initiatorId", link.getInitiatorId());
        item.put("initiatorName", userResolver.nameOf(link.getInitiatorId()));
        item.put("startTime", link.getStartTime());
        item.put("endTime", link.getEndTime());
        Task current = currentTask(link.getProcessInstanceId());
        item.put("currentNode", current == null ? null : current.getName());
        item.put("currentTaskId", current == null ? null : current.getId());
        item.put("overdue", isOverdue(link));
        item.put("dueTime", dueTime(link));
        return item;
    }

    /** 发起人重新提交（驳回后）→ 回到首个审批节点，历史追加而非覆盖（docs/03 §5.1 第 5 条）。 */
    @Transactional
    public Map<String, Object> resubmit(String processInstanceId, String comment) {
        WfBusinessLink link = requireLink(processInstanceId);
        Long userId = userResolver.currentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!userId.equals(link.getInitiatorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅发起人可重新提交");
        }
        if (link.getStatus() == null || link.getStatus() != WorkflowConstants.LINK_REJECTED) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅已驳回的流程可重新提交");
        }
        Task current = currentTask(processInstanceId);
        if (current == null) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "流程已结束，无法重新提交");
        }
        taskService.addComment(current.getId(), processInstanceId, WorkflowConstants.ACTION_RESUBMIT,
                comment == null || comment.isBlank() ? "重新提交" : comment);
        taskService.complete(current.getId());
        link.setStatus(WorkflowConstants.LINK_RUNNING);
        link.setEndTime(null);
        linkMapper.updateById(link);
        restoreBusiness(link.getProcessKey(), link.getBusinessId());
        Task next = currentTask(processInstanceId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processInstanceId", processInstanceId);
        result.put("taskId", next == null ? null : next.getId());
        result.put("status", link.getStatus());
        return result;
    }

    private void restoreBusiness(String processKey, Long businessId) {
        switch (processKey) {
            case WorkflowConstants.POLICY_PUBLISH -> {
                IcpPolicy policy = policyMapper.selectById(businessId);
                policy.setStatus(1);
                policyMapper.updateById(policy);
            }
            case WorkflowConstants.EVALUATION_PLAN -> {
                IcpEvaluation evaluation = evaluationMapper.selectById(businessId);
                evaluation.setStatus(1);
                evaluationMapper.updateById(evaluation);
            }
            case WorkflowConstants.DEFECT_RECTIFY -> {
                IcpRectify rectify = rectifyMapper.selectById(businessId);
                rectify.setStatus(1);
                rectifyMapper.updateById(rectify);
            }
            case WorkflowConstants.PURCHASE_APPLY -> {
                WfPurchase purchase = purchaseMapper.selectById(businessId);
                purchase.setStatus(1);
                purchaseMapper.updateById(purchase);
            }
            case WorkflowConstants.SEAL_APPLY -> {
                WfSeal seal = sealMapper.selectById(businessId);
                seal.setStatus(1);
                sealMapper.updateById(seal);
            }
            case WorkflowConstants.DOC_BORROW -> {
                CollDocBorrow borrow = borrowMapper.selectById(businessId);
                borrow.setStatus(0);
                borrowMapper.updateById(borrow);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ 业务回调

    /**
     * 末节点审批通过后的业务回调（docs/03 §5.1 第 10 条）。
     */
    @Transactional
    public void onApproved(WfBusinessLink link, Long approverId, String comment) {
        switch (link.getProcessKey()) {
            case WorkflowConstants.POLICY_PUBLISH -> {
                IcpPolicy policy = policyMapper.selectById(link.getBusinessId());
                policy.setStatus(2);
                policy.setPublishTime(LocalDateTime.now());
                if (policy.getEffectiveDate() == null) {
                    policy.setEffectiveDate(LocalDate.now());
                }
                policy.setFlowInstanceId(link.getProcessInstanceId());
                policyMapper.updateById(policy);
            }
            case WorkflowConstants.EVALUATION_PLAN -> {
                IcpEvaluation evaluation = evaluationMapper.selectById(link.getBusinessId());
                evaluation.setStatus(1);
                evaluation.setFlowInstanceId(link.getProcessInstanceId());
                evaluationMapper.updateById(evaluation);
            }
            case WorkflowConstants.DEFECT_RECTIFY -> {
                IcpRectify rectify = rectifyMapper.selectById(link.getBusinessId());
                rectify.setStatus(2);
                rectify.setReviewUserId(approverId);
                rectify.setReviewTime(LocalDateTime.now());
                rectify.setReviewOpinion(comment == null || comment.isBlank() ? "同意" : comment);
                if (rectify.getFinishDate() == null) {
                    rectify.setFinishDate(LocalDate.now());
                }
                rectify.setFlowInstanceId(link.getProcessInstanceId());
                rectifyMapper.updateById(rectify);
                IcpDefect defect = rectify.getDefectId() == null ? null : defectMapper.selectById(rectify.getDefectId());
                if (defect != null && defect.getStatus() != null && defect.getStatus() < 2) {
                    defect.setStatus(2);
                    defectMapper.updateById(defect);
                }
            }
            case WorkflowConstants.PURCHASE_APPLY -> {
                WfPurchase purchase = purchaseMapper.selectById(link.getBusinessId());
                purchase.setStatus(2);
                purchase.setFlowInstanceId(link.getProcessInstanceId());
                purchaseMapper.updateById(purchase);
            }
            case WorkflowConstants.SEAL_APPLY -> {
                WfSeal seal = sealMapper.selectById(link.getBusinessId());
                seal.setStatus(2);
                seal.setFlowInstanceId(link.getProcessInstanceId());
                sealMapper.updateById(seal);
            }
            case WorkflowConstants.DOC_BORROW -> {
                CollDocBorrow borrow = borrowMapper.selectById(link.getBusinessId());
                borrow.setStatus(1);
                borrow.setFlowInstanceId(link.getProcessInstanceId());
                borrowMapper.updateById(borrow);
            }
            default -> {
            }
        }
        log.info("流程审批通过: instance={} processKey={} businessNo={}",
                link.getProcessInstanceId(), link.getProcessKey(), link.getBusinessNo());
    }

    /** 驳回后的业务回退（docs/03 §5.1 第 5 条）。 */
    @Transactional
    public void onRejected(WfBusinessLink link) {
        switch (link.getProcessKey()) {
            case WorkflowConstants.POLICY_PUBLISH -> {
                IcpPolicy policy = policyMapper.selectById(link.getBusinessId());
                policy.setStatus(0);
                policyMapper.updateById(policy);
            }
            case WorkflowConstants.EVALUATION_PLAN -> {
                IcpEvaluation evaluation = evaluationMapper.selectById(link.getBusinessId());
                evaluation.setStatus(0);
                evaluationMapper.updateById(evaluation);
            }
            case WorkflowConstants.DEFECT_RECTIFY -> {
                IcpRectify rectify = rectifyMapper.selectById(link.getBusinessId());
                rectify.setStatus(0);
                rectifyMapper.updateById(rectify);
                // 驳回后缺陷回到"整改中"（M2c 修订 B22：提交不再置 2，待复核由审批通过触发）
                IcpDefect rejectedDefect = rectify.getDefectId() == null ? null : defectMapper.selectById(rectify.getDefectId());
                if (rejectedDefect != null && rejectedDefect.getStatus() != null && rejectedDefect.getStatus() == 2) {
                    rejectedDefect.setStatus(1);
                    defectMapper.updateById(rejectedDefect);
                }
            }
            case WorkflowConstants.PURCHASE_APPLY -> {
                WfPurchase purchase = purchaseMapper.selectById(link.getBusinessId());
                purchase.setStatus(0);
                purchaseMapper.updateById(purchase);
            }
            case WorkflowConstants.SEAL_APPLY -> {
                WfSeal seal = sealMapper.selectById(link.getBusinessId());
                seal.setStatus(0);
                sealMapper.updateById(seal);
            }
            case WorkflowConstants.DOC_BORROW -> {
                CollDocBorrow borrow = borrowMapper.selectById(link.getBusinessId());
                borrow.setStatus(2);
                borrowMapper.updateById(borrow);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ 辅助

    /** 宽松查询：任务列表映射时允许实例已被清理（返回 null）。 */
    public WfBusinessLink requireLinkQuietly(String processInstanceId) {
        return linkMapper.selectByProcessInstanceId(processInstanceId);
    }

    public WfBusinessLink requireLink(String processInstanceId) {
        WfBusinessLink link = linkMapper.selectByProcessInstanceId(processInstanceId);
        if (link == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "流程实例不存在");
        }
        return link;
    }

    public Task currentTask(String processInstanceId) {
        return taskService.createTaskQuery().processInstanceId(processInstanceId).list().stream()
                .min(Comparator.comparing(Task::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    public boolean isCurrentHandler(Task task) {
        if (task == null) {
            return false;
        }
        Long userId = userResolver.currentUserId();
        if (userId == null) {
            return false;
        }
        if (String.valueOf(userId).equals(task.getAssignee())) {
            return true;
        }
        Set<String> groups = userResolver.currentRoleGroups();
        if (groups.isEmpty() || task.getId() == null) {
            return false;
        }
        return taskService.createTaskQuery().taskId(task.getId())
                .taskCandidateGroupIn(List.copyOf(groups)).count() > 0;
    }

    public boolean isParticipant(String processInstanceId) {
        Task task = currentTask(processInstanceId);
        return isCurrentHandler(task);
    }

    private void checkAccess(WfBusinessLink link) {
        Long userId = userResolver.currentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (isCurrentHandler(currentTask(link.getProcessInstanceId()))) {
            return;
        }
        DataScopeInfo info = dataScopeService.current();
        BusinessScope scope = businessScope(link);
        if (!dataScopeService.canAccess(info, scope.deptId(), scope.ownerUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无数据权限访问该记录");
        }
    }

    private BusinessScope businessScope(WfBusinessLink link) {
        Long deptId = null;
        Long ownerUserId = link.getInitiatorId();
        switch (link.getProcessKey()) {
            case WorkflowConstants.POLICY_PUBLISH -> {
                IcpPolicy policy = policyMapper.selectById(link.getBusinessId());
                if (policy != null) {
                    deptId = policy.getOwnerDeptId();
                    ownerUserId = policy.getOwnerUserId();
                }
            }
            case WorkflowConstants.EVALUATION_PLAN -> {
                IcpEvaluation evaluation = evaluationMapper.selectById(link.getBusinessId());
                if (evaluation != null) {
                    deptId = evaluation.getOwnerDeptId();
                    ownerUserId = evaluation.getOwnerUserId();
                }
            }
            case WorkflowConstants.DEFECT_RECTIFY -> {
                IcpRectify rectify = rectifyMapper.selectById(link.getBusinessId());
                if (rectify != null) {
                    deptId = rectify.getDeptId();
                    ownerUserId = rectify.getResponsibleUserId();
                }
            }
            case WorkflowConstants.PURCHASE_APPLY -> {
                WfPurchase purchase = purchaseMapper.selectById(link.getBusinessId());
                if (purchase != null) {
                    deptId = purchase.getDeptId();
                    ownerUserId = purchase.getApplicantId();
                }
            }
            case WorkflowConstants.SEAL_APPLY -> {
                WfSeal seal = sealMapper.selectById(link.getBusinessId());
                if (seal != null) {
                    deptId = seal.getDeptId();
                    ownerUserId = seal.getApplicantId();
                }
            }
            case WorkflowConstants.DOC_BORROW -> {
                CollDocBorrow borrow = borrowMapper.selectById(link.getBusinessId());
                if (borrow != null) {
                    ownerUserId = borrow.getBorrowerId();
                }
            }
            default -> {
            }
        }
        return new BusinessScope(deptId, ownerUserId);
    }

    private Object businessData(WfBusinessLink link) {
        return switch (link.getProcessKey()) {
            case WorkflowConstants.POLICY_PUBLISH -> policyMapper.selectById(link.getBusinessId());
            case WorkflowConstants.EVALUATION_PLAN -> evaluationMapper.selectById(link.getBusinessId());
            case WorkflowConstants.DEFECT_RECTIFY -> rectifyMapper.selectById(link.getBusinessId());
            case WorkflowConstants.PURCHASE_APPLY -> purchaseMapper.selectById(link.getBusinessId());
            case WorkflowConstants.SEAL_APPLY -> sealMapper.selectById(link.getBusinessId());
            case WorkflowConstants.DOC_BORROW -> borrowMapper.selectById(link.getBusinessId());
            default -> null;
        };
    }

    private List<Map<String, Object>> attachments(String processInstanceId) {
        List<Map<String, Object>> list = new ArrayList<>();
        List<org.flowable.engine.task.Attachment> attachments = taskService.getProcessInstanceAttachments(processInstanceId);
        if (attachments == null) {
            return list;
        }
        for (org.flowable.engine.task.Attachment attachment : attachments) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", attachment.getId());
            item.put("name", attachment.getName());
            item.put("type", attachment.getType());
            item.put("url", attachment.getUrl());
            item.put("description", attachment.getDescription());
            item.put("taskId", attachment.getTaskId());
            item.put("time", toLocal(attachment.getTime()));
            item.put("userId", attachment.getUserId());
            list.add(item);
        }
        return list;
    }

    private Object readVariable(String processInstanceId, String name, Object defaultValue) {
        try {
            Object value = runtimeService.getVariable(processInstanceId, name);
            if (value != null) {
                return value;
            }
        } catch (Exception ignored) {
            // 流程已结束时从历史变量读取
        }
        try {
            org.flowable.variable.api.history.HistoricVariableInstance variable =
                    historyService.createHistoricVariableInstanceQuery()
                            .processInstanceId(processInstanceId).variableName(name).singleResult();
            if (variable != null) {
                return variable.getValue();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return defaultValue;
    }

    public Object variable(String processInstanceId, String name) {
        return readVariable(processInstanceId, name, null);
    }

    private String formSchema(String formCode) {
        WfForm form = formMapper.selectByFormCode(formCode);
        return form == null ? null : form.getFormSchema();
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------ 表单展示数据（ID -> 名称）

    private record FormDisplay(Map<String, Object> schema, Map<String, Object> data, Object business) {
    }

    /**
     * 详情中的表单展示数据：把 formData / business 里的原始数字 ID 解析成名称
     * （部门 / 人员 / 缺陷编码 / 分类 / 附件 / 文档），并把展示字段补进 formSchema，
     * 避免前端直接展示「部门 ID=7」「责任人 ID=23」「缺陷 ID=1720」。
     */
    private FormDisplay displayForm(WfBusinessLink link) {
        Map<String, Object> schema = readObjectMap(formSchema(link.getFormCode()));
        Map<String, Object> formData = readObjectMap(String.valueOf(
                readVariable(link.getProcessInstanceId(), WorkflowConstants.VAR_FORM_DATA, "{}")));
        List<Map<String, Object>> fields = new ArrayList<>();
        Object schemaFields = schema.get("fields");
        if (schemaFields instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> copied = new LinkedHashMap<>();
                    map.forEach((key, value) -> copied.put(String.valueOf(key), value));
                    fields.add(copied);
                }
            }
        }
        displayFields(formData, fields);
        Object business = displayBusiness(link, fields);
        dedupeFields(fields);
        Map<String, Object> displaySchema = new LinkedHashMap<>();
        displaySchema.put("fields", fields);
        return new FormDisplay(displaySchema, formData, business);
    }

    private void displayFields(Map<String, Object> data, List<Map<String, Object>> fields) {
        addNamedField(data, fields, "deptId", "deptName", "部门", this::deptName);
        addNamedField(data, fields, "ownerDeptId", "ownerDeptName", "归属部门", this::deptName);
        addNamedField(data, fields, "evaluateDeptId", "evaluateDeptName", "评价部门", this::deptName);
        addNamedField(data, fields, "applicantId", "applicantName", "申请人", userResolver::nameOf);
        addNamedField(data, fields, "responsibleUserId", "responsibleUserName", "责任人", userResolver::nameOf);
        addNamedField(data, fields, "assigneeId", "assigneeName", "办理人", userResolver::nameOf);
        addNamedField(data, fields, "evaluatorId", "evaluatorName", "评价人", userResolver::nameOf);
        addNamedField(data, fields, "discoverUserId", "discoverUserName", "发现人", userResolver::nameOf);
        addNamedField(data, fields, "reviewUserId", "reviewUserName", "复核人", userResolver::nameOf);
        addNamedField(data, fields, "reviewerId", "reviewerName", "复核人", userResolver::nameOf);
        addNamedField(data, fields, "ownerUserId", "ownerUserName", "负责人", userResolver::nameOf);
        addNamedField(data, fields, "borrowerId", "borrowerName", "借阅人", userResolver::nameOf);
        addDefectCode(data, fields);
        addFileName(data, fields);
        addEvidenceNames(data, fields);
        addDeptScopeNames(data, fields);
        // 只展示编码/名称的关联字段，移除原始 ID，避免出现无标签的裸 ID。
        for (String idKey : List.of("docId", "controlId", "riskId", "policyId", "taskId", "categoryId")) {
            data.remove(idKey);
        }
    }

    private Object displayBusiness(WfBusinessLink link, List<Map<String, Object>> fields) {
        Object entity = businessData(link);
        if (entity == null) {
            return null;
        }
        Map<String, Object> business = objectMapper.convertValue(entity,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        business.values().removeIf(value -> value == null);
        if (business.containsKey("docId")) {
            Long docId = longValue(business.get("docId"));
            business.remove("docId");
            if (docId != null) {
                com.icp.collab.entity.CollDoc doc = collDocMapper.selectById(docId);
                if (doc != null && doc.getDocName() != null) {
                    business.put("docName", doc.getDocName());
                    fields.add(displayField("docName", "文档"));
                }
            }
        }
        if (business.containsKey("categoryId")) {
            Long categoryId = longValue(business.get("categoryId"));
            business.remove("categoryId");
            if (categoryId != null) {
                Map<Long, String> names = WorkflowConstants.POLICY_PUBLISH.equals(link.getProcessKey())
                        ? icpLookupService.policyCategoryNames(List.of(categoryId))
                        : icpLookupService.riskCategoryNames(List.of(categoryId));
                String name = names.get(categoryId);
                if (name != null) {
                    business.put("categoryName", name);
                    fields.add(displayField("categoryName", "分类"));
                }
            }
        }
        displayFields(business, fields);
        return business;
    }

    private void addNamedField(Map<String, Object> data, List<Map<String, Object>> fields,
                               String idKey, String nameKey, String label, Function<Long, String> resolver) {
        Long id = longValue(data.remove(idKey));
        if (id == null) {
            return;
        }
        String name = resolver.apply(id);
        if (name == null || name.isBlank()) {
            return;
        }
        data.put(nameKey, name);
        fields.add(displayField(nameKey, label));
    }

    private void addDefectCode(Map<String, Object> data, List<Map<String, Object>> fields) {
        Long defectId = longValue(data.remove("defectId"));
        if (defectId == null) {
            return;
        }
        IcpDefect defect = defectMapper.selectById(defectId);
        if (defect == null || defect.getDefectCode() == null || defect.getDefectCode().isBlank()) {
            return;
        }
        data.put("defectCode", defect.getDefectCode());
        fields.add(displayField("defectCode", "缺陷编号"));
    }

    private void addFileName(Map<String, Object> data, List<Map<String, Object>> fields) {
        Long fileId = longValue(data.remove("fileId"));
        if (fileId == null) {
            return;
        }
        SysFile file = sysFileMapper.selectById(fileId);
        if (file == null) {
            return;
        }
        String name = file.getOriginalName() == null || file.getOriginalName().isBlank()
                ? file.getFileName() : file.getOriginalName();
        if (name == null || name.isBlank()) {
            return;
        }
        data.put("fileName", name);
        fields.add(displayField("fileName", "附件"));
    }

    /** deptScope 存的是部门 ID CSV 或「全公司」，展示时解析为部门名称。 */
    private void addDeptScopeNames(Map<String, Object> data, List<Map<String, Object>> fields) {
        Object value = data.remove("deptScope");
        if (value == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (String part : String.valueOf(value).split(",")) {
            String text = part.trim();
            if (text.isEmpty()) {
                continue;
            }
            Long deptId = longValue(text);
            if (deptId == null) {
                names.add(text);
                continue;
            }
            String name = deptName(deptId);
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        if (!names.isEmpty()) {
            data.put("deptScopeNames", String.join("、", names));
            fields.add(displayField("deptScopeNames", "评价范围"));
        }
    }

    /** evidence 存的是附件 ID CSV，展示时解析为文件名。 */
    private void addEvidenceNames(Map<String, Object> data, List<Map<String, Object>> fields) {
        Object value = data.remove("evidence");
        if (value == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (String part : String.valueOf(value).split(",")) {
            Long fileId = longValue(part);
            if (fileId == null) {
                continue;
            }
            SysFile file = sysFileMapper.selectById(fileId);
            if (file == null) {
                continue;
            }
            String name = file.getOriginalName() == null || file.getOriginalName().isBlank()
                    ? file.getFileName() : file.getOriginalName();
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        if (!names.isEmpty()) {
            data.put("evidenceNames", String.join("、", names));
            fields.add(displayField("evidenceNames", "佐证材料"));
        }
    }

    private void dedupeFields(List<Map<String, Object>> fields) {
        Set<String> seen = new java.util.LinkedHashSet<>();
        fields.removeIf(field -> {
            Object prop = field.get("prop");
            if (prop == null) {
                return false;
            }
            return !seen.add(String.valueOf(prop));
        });
    }

    private Map<String, Object> displayField(String prop, String label) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("prop", prop);
        field.put("label", label);
        field.put("type", "text");
        return field;
    }

    private String deptName(Long deptId) {
        SysDept dept = sysDeptMapper.selectById(deptId);
        return dept == null ? null : dept.getDeptName();
    }

    /**
     * 采购金额流程变量：用于 purchase_apply 金额分级审批（≤5000 部门审批，>5000 追加管理层）。
     */
    private java.math.BigDecimal purchaseAmount(Map<String, Object> formData) {
        Object raw = formData == null ? null : formData.get("amount");
        if (raw == null) {
            return java.math.BigDecimal.ZERO;
        }
        if (raw instanceof java.math.BigDecimal decimal) {
            return decimal;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return java.math.BigDecimal.ZERO;
        }
        try {
            return new java.math.BigDecimal(text);
        } catch (NumberFormatException e) {
            return java.math.BigDecimal.ZERO;
        }
    }

    private Long longValue(Object value) {        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readObjectMap(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        JsonNode node = readJson(json);
        if (node == null || !node.isObject()) {
            return result;
        }
        Map<String, Object> parsed = objectMapper.convertValue(node, Map.class);
        if (parsed != null) {
            result.putAll(parsed);
        }
        return result;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    public LocalDateTime dueTime(WfBusinessLink link) {
        if (link.getStartTime() == null) {
            return null;
        }
        return link.getStartTime().plusHours(timeoutHours());
    }

    public boolean isOverdue(WfBusinessLink link) {
        if (link.getStatus() == null || link.getStatus() != WorkflowConstants.LINK_RUNNING || link.getStartTime() == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(dueTime(link));
    }

    /** 超时判断（可覆盖超时阈值，便于验收/演示不同阈值下的超时预警）。 */
    public boolean isOverdue(WfBusinessLink link, int timeoutHours) {
        if (link.getStatus() == null || link.getStatus() != WorkflowConstants.LINK_RUNNING || link.getStartTime() == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(link.getStartTime().plusHours(Math.max(0, timeoutHours)));
    }

    public long timeoutHours() {
        return configService.getInt(WorkflowConstants.CONFIG_TIMEOUT_HOURS, (int) WorkflowConstants.DEFAULT_TIMEOUT_HOURS);
    }

    public String statusLabel(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 0 -> "运行中";
            case 1 -> "已通过";
            case 2 -> "已驳回";
            case 3 -> "已撤回";
            default -> "未知";
        };
    }

    public String actionLabel(String action) {
        if (action == null) {
            return null;
        }
        return switch (action) {
            case WorkflowConstants.ACTION_APPROVE -> "审批";
            case WorkflowConstants.ACTION_REJECT -> "驳回";
            case WorkflowConstants.ACTION_SUBMIT -> "提交";
            case WorkflowConstants.ACTION_RESUBMIT -> "重提";
            default -> action;
        };
    }

    public void markLinkApproved(WfBusinessLink link) {
        link.setStatus(WorkflowConstants.LINK_APPROVED);
        link.setEndTime(LocalDateTime.now());
        linkMapper.updateById(link);
    }

    public void markLinkRejected(WfBusinessLink link) {
        link.setStatus(WorkflowConstants.LINK_REJECTED);
        link.setEndTime(LocalDateTime.now());
        linkMapper.updateById(link);
    }

    private Long currentDeptId(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        return user == null ? null : user.getDeptId();
    }

    private <T> T requireBusiness(Long id, T value, String label) {
        if (id == null || value == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, label + "不存在");
        }
        return value;
    }

    private void checkScope(Long deptId, Long ownerUserId) {
        dataScopeService.checkAccess(dataScopeService.current(), deptId, ownerUserId);
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static int intVal(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    static Long longVal(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(String.valueOf(value));
    }

    static LocalDateTime toLocal(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private record BusinessRef(Long businessId, String businessNo, String title, Long deptId) {
    }

    private record BusinessScope(Long deptId, Long ownerUserId) {
    }
}

package com.icp.icp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.common.security.SecurityUtils;
import com.icp.icp.entity.IcpEvaluation;
import com.icp.icp.entity.IcpEvaluationTask;
import com.icp.icp.entity.IcpMatrix;
import com.icp.icp.entity.IcpRisk;
import com.icp.icp.mapper.IcpEvaluationMapper;
import com.icp.icp.mapper.IcpEvaluationTaskMapper;
import com.icp.icp.mapper.IcpMatrixMapper;
import com.icp.icp.mapper.IcpRiskMapper;
import com.icp.icp.support.CodeSeq;
import com.icp.icp.support.IcpLookupService;
import com.icp.icp.support.IcpScope;
import com.icp.system.service.DataScopeService;
import com.icp.workflow.dto.WorkflowRequests;
import com.icp.workflow.service.WorkflowInstanceService;
import com.icp.workflow.support.WorkflowConstants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 内控评价计划（docs/03 §4 / docs/04 §4）：0 草稿 →1 审批中 →2 执行中 →3 已完成 →4 已关闭。
 *
 * <p>M2b：submit 只做状态流转（flow_instance_id=null，pendingWorkflow=true）；start 按矩阵模板幂等生成评价任务。</p>
 */
@Service
public class EvaluationService {

    private static final int DRAFT = 0;
    private static final int SUBMITTED = 1;
    private static final int RUNNING = 2;
    private static final int FINISHED = 3;
    private static final int CLOSED = 4;

    private final IcpEvaluationMapper evaluationMapper;
    private final IcpEvaluationTaskMapper taskMapper;
    private final IcpMatrixMapper matrixMapper;
    private final IcpRiskMapper riskMapper;
    private final IcpLookupService lookupService;
    private final DataScopeService dataScopeService;
    private final WorkflowInstanceService workflowInstanceService;

    public EvaluationService(IcpEvaluationMapper evaluationMapper,
                             IcpEvaluationTaskMapper taskMapper,
                             IcpMatrixMapper matrixMapper,
                             IcpRiskMapper riskMapper,
                             IcpLookupService lookupService,
                             DataScopeService dataScopeService,
                             WorkflowInstanceService workflowInstanceService) {
        this.evaluationMapper = evaluationMapper;
        this.taskMapper = taskMapper;
        this.matrixMapper = matrixMapper;
        this.riskMapper = riskMapper;
        this.lookupService = lookupService;
        this.dataScopeService = dataScopeService;
        this.workflowInstanceService = workflowInstanceService;
    }

    public PageResult<IcpEvaluation> page(long pageNum, long pageSize, String keyword, Integer status,
                                          Integer planYear, Integer planType) {
        DataScopeInfo info = dataScopeService.current();
        LambdaQueryWrapper<IcpEvaluation> wrapper = new LambdaQueryWrapper<IcpEvaluation>()
                .orderByDesc(IcpEvaluation::getId);
        IcpScope.apply(wrapper, info, IcpEvaluation::getOwnerDeptId, IcpEvaluation::getOwnerUserId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(q -> q.like(IcpEvaluation::getPlanName, kw)
                    .or().like(IcpEvaluation::getPlanCode, kw));
        }
        if (status != null) {
            wrapper.eq(IcpEvaluation::getStatus, status);
        }
        if (planYear != null) {
            wrapper.eq(IcpEvaluation::getPlanYear, planYear);
        }
        if (planType != null) {
            wrapper.eq(IcpEvaluation::getPlanType, planType);
        }
        Page<IcpEvaluation> page = evaluationMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillNames(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public IcpEvaluation get(Long id) {
        IcpEvaluation plan = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), plan.getOwnerDeptId(), plan.getOwnerUserId());
        fillNames(List.of(plan));
        return plan;
    }

    @Transactional
    public IcpEvaluation create(IcpEvaluation request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        IcpEvaluation plan = new IcpEvaluation();
        plan.setPlanName(request.getPlanName() == null ? null : request.getPlanName().trim());
        plan.setPlanYear(request.getPlanYear() == null ? LocalDate.now().getYear() : request.getPlanYear());
        plan.setPlanType(request.getPlanType() == null ? 1 : request.getPlanType());
        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        plan.setDeptScope(request.getDeptScope());
        plan.setDescription(request.getDescription());
        plan.setRemark(request.getRemark());
        plan.setStatus(DRAFT);
        plan.setFlowInstanceId(null);
        if (request.getOwnerDeptId() != null) {
            plan.setOwnerDeptId(request.getOwnerDeptId());
        } else {
            plan.setOwnerDeptId(dataScopeService.current().getDeptId());
        }
        plan.setOwnerUserId(request.getOwnerUserId() != null
                ? request.getOwnerUserId() : SecurityUtils.currentUserIdOrNull());
        plan.setPlanCode(StringUtils.hasText(request.getPlanCode())
                ? request.getPlanCode().trim() : CodeSeq.next(evaluationMapper.selectMaxCode(), "PJ", 2));
        validate(plan);
        evaluationMapper.insert(plan);
        fillNames(List.of(plan));
        return plan;
    }

    @Transactional
    public IcpEvaluation update(Long id, IcpEvaluation request) {
        IcpEvaluation existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getOwnerDeptId(), existing.getOwnerUserId());
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (existing.getStatus() != DRAFT) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅草稿状态的评价计划可编辑");
        }
        if (request.getStatus() != null && !Objects.equals(request.getStatus(), existing.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "计划状态请使用送审/开始/完成/关闭接口变更");
        }
        if (StringUtils.hasText(request.getPlanName())) {
            existing.setPlanName(request.getPlanName().trim());
        }
        if (request.getPlanYear() != null) {
            existing.setPlanYear(request.getPlanYear());
        }
        if (request.getPlanType() != null) {
            existing.setPlanType(request.getPlanType());
        }
        if (request.getStartDate() != null) {
            existing.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            existing.setEndDate(request.getEndDate());
        }
        if (request.getDeptScope() != null) {
            existing.setDeptScope(request.getDeptScope());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getRemark() != null) {
            existing.setRemark(request.getRemark());
        }
        validate(existing);
        evaluationMapper.updateById(existing);
        fillNames(List.of(existing));
        return existing;
    }

    @Transactional
    public void delete(Long id) {
        IcpEvaluation existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getOwnerDeptId(), existing.getOwnerUserId());
        if (existing.getStatus() != DRAFT) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅草稿状态的评价计划可删除");
        }
        if (taskMapper.countByPlan(id) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "计划下存在评价任务，不能删除");
        }
        evaluationMapper.deleteById(id);
    }

    /** 评价计划 0 → 1：真实发起 Flowable 评价计划流程（docs/03 §5.1 D34）。 */
    @Transactional
    public Map<String, Object> submit(Long id) {
        IcpEvaluation existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getOwnerDeptId(), existing.getOwnerUserId());
        if (existing.getStatus() != DRAFT) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅草稿状态的评价计划可提交");
        }
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("planCode", existing.getPlanCode());
        formData.put("planName", existing.getPlanName());
        formData.put("planYear", existing.getPlanYear());
        formData.put("planType", existing.getPlanType());
        formData.put("startDate", existing.getStartDate() == null ? null : existing.getStartDate().toString());
        formData.put("endDate", existing.getEndDate() == null ? null : existing.getEndDate().toString());
        formData.put("deptScope", existing.getDeptScope());
        Map<String, Object> started = workflowInstanceService.start(new WorkflowRequests.StartRequest(
                WorkflowConstants.EVALUATION_PLAN, id, "evaluation", formData, existing.getPlanName()), null);
        IcpEvaluation updated = evaluationMapper.selectById(id);
        Map<String, Object> result = actionResult(updated, 0, false);
        result.put("processInstanceId", started.get("processInstanceId"));
        result.put("taskId", started.get("taskId"));
        result.put("businessNo", started.get("businessNo"));
        return result;
    }

    /** 开始评价：1 → 2，按矩阵模板生成任务（幂等：已生成过则跳过）。 */
    @Transactional
    public Map<String, Object> start(Long id) {
        IcpEvaluation existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getOwnerDeptId(), existing.getOwnerUserId());
        if (existing.getStatus() == RUNNING) {
            // 幂等：已启动的计划重复调用不报错、不重复生成任务。
            return actionResult(existing, 0, false);
        }
        if (existing.getStatus() != SUBMITTED) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅审批中状态的评价计划可开始");
        }
        long already = taskMapper.countByPlan(id);
        int generated = 0;
        if (already == 0) {
            List<IcpMatrix> matrices = matrixMapper.selectList(new LambdaQueryWrapper<IcpMatrix>()
                    .eq(IcpMatrix::getStatus, 1)
                    .orderByAsc(IcpMatrix::getRiskId)
                    .orderByAsc(IcpMatrix::getId));
            Map<Long, String> riskNames = new HashMap<>();
            Set<Long> riskIds = matrices.stream().map(IcpMatrix::getRiskId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!riskIds.isEmpty()) {
                for (IcpRisk risk : riskMapper.selectBatchIds(riskIds)) {
                    riskNames.put(risk.getId(), risk.getRiskName());
                }
            }
            List<IcpEvaluationTask> tasks = new ArrayList<>();
            for (IcpMatrix matrix : matrices) {
                IcpEvaluationTask task = new IcpEvaluationTask();
                task.setPlanId(id);
                task.setTaskName("评价：" + riskNames.getOrDefault(matrix.getRiskId(), "风险 " + matrix.getRiskId()));
                task.setRiskId(matrix.getRiskId());
                task.setControlId(matrix.getControlId());
                task.setEvaluateDeptId(existing.getOwnerDeptId());
                task.setEvaluatorId(existing.getOwnerUserId());
                task.setStartDate(existing.getStartDate() == null ? LocalDate.now() : existing.getStartDate());
                task.setDueDate(existing.getEndDate() == null ? LocalDate.now().plusMonths(1) : existing.getEndDate());
                task.setStatus(0);
                tasks.add(task);
            }
            for (int i = 0; i < tasks.size(); i++) {
                IcpEvaluationTask task = tasks.get(i);
                task.setTaskCode(existing.getPlanCode() + "-" + String.format("%02d", i + 1));
                taskMapper.insert(task);
                generated++;
            }
        }
        existing.setStatus(RUNNING);
        evaluationMapper.updateById(existing);
        return actionResult(existing, generated, false);
    }

    /** 完成：2 → 3，要求所有任务已完成（status=2）。 */
    @Transactional
    public Map<String, Object> finish(Long id) {
        IcpEvaluation existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getOwnerDeptId(), existing.getOwnerUserId());
        if (existing.getStatus() != RUNNING) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅执行中状态的评价计划可完成");
        }
        List<IcpEvaluationTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<IcpEvaluationTask>()
                .eq(IcpEvaluationTask::getPlanId, id));
        long unfinished = tasks.stream().filter(t -> t.getStatus() == null || t.getStatus() != 2).count();
        if (tasks.isEmpty()) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "计划尚未生成评价任务，不能完成");
        }
        if (unfinished > 0) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仍有 " + unfinished + " 个评价任务未完成，不能完成计划");
        }
        existing.setStatus(FINISHED);
        evaluationMapper.updateById(existing);
        return actionResult(existing, 0, false);
    }

    /** 关闭：3 → 4。 */
    @Transactional
    public Map<String, Object> close(Long id) {
        IcpEvaluation existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getOwnerDeptId(), existing.getOwnerUserId());
        if (existing.getStatus() != FINISHED) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅已完成状态的评价计划可关闭");
        }
        existing.setStatus(CLOSED);
        evaluationMapper.updateById(existing);
        return actionResult(existing, 0, false);
    }

    private Map<String, Object> actionResult(IcpEvaluation plan, int generatedTasks, boolean pendingWorkflow) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("id", plan.getId());
        data.put("planCode", plan.getPlanCode());
        data.put("status", plan.getStatus());
        data.put("flowInstanceId", plan.getFlowInstanceId());
        data.put("pendingWorkflow", pendingWorkflow);
        data.put("generatedTasks", generatedTasks);
        data.put("taskCount", taskMapper.countByPlan(plan.getId()));
        return data;
    }

    private IcpEvaluation require(Long id) {
        IcpEvaluation plan = evaluationMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评价计划不存在");
        }
        return plan;
    }

    private void validate(IcpEvaluation plan) {
        if (!StringUtils.hasText(plan.getPlanName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "计划名称不能为空");
        }
        if (plan.getPlanName().length() > 200) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "计划名称不能超过 200 个字符");
        }
        if (plan.getStartDate() != null && plan.getEndDate() != null
                && plan.getStartDate().isAfter(plan.getEndDate())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "开始日期不能晚于结束日期");
        }
        if (plan.getPlanYear() != null && (plan.getPlanYear() < 2000 || plan.getPlanYear() > 2100)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "计划年度不合法");
        }
    }

    private void fillNames(List<IcpEvaluation> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> deptIds = records.stream().map(IcpEvaluation::getOwnerDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> depts = lookupService.deptNames(deptIds);
        for (IcpEvaluation record : records) {
            record.setOwnerDeptName(depts.get(record.getOwnerDeptId()));
            record.setTaskCount((int) taskMapper.countByPlan(record.getId()));
        }
    }
}

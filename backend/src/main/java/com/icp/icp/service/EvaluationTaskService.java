package com.icp.icp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.common.security.SecurityUtils;
import com.icp.icp.dto.IcpRequests;
import com.icp.icp.entity.IcpEvaluation;
import com.icp.icp.entity.IcpEvaluationTask;
import com.icp.icp.entity.IcpEvaluationWorkpaper;
import com.icp.icp.mapper.IcpEvaluationMapper;
import com.icp.icp.mapper.IcpEvaluationTaskMapper;
import com.icp.icp.mapper.IcpEvaluationWorkpaperMapper;
import com.icp.icp.support.IcpLookupService;
import com.icp.icp.support.IcpScope;
import com.icp.system.service.DataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评价任务与底稿（docs/03 §4）：0 待开始 →1 进行中 →2 已完成（完成需评分+结论+底稿，docs/04 §4）。
 */
@Service
public class EvaluationTaskService {

    private static final int TODO = 0;
    private static final int RUNNING = 1;
    private static final int DONE = 2;

    private final IcpEvaluationTaskMapper taskMapper;
    private final IcpEvaluationWorkpaperMapper workpaperMapper;
    private final IcpEvaluationMapper evaluationMapper;
    private final IcpLookupService lookupService;
    private final DataScopeService dataScopeService;

    public EvaluationTaskService(IcpEvaluationTaskMapper taskMapper,
                                 IcpEvaluationWorkpaperMapper workpaperMapper,
                                 IcpEvaluationMapper evaluationMapper,
                                 IcpLookupService lookupService,
                                 DataScopeService dataScopeService) {
        this.taskMapper = taskMapper;
        this.workpaperMapper = workpaperMapper;
        this.evaluationMapper = evaluationMapper;
        this.lookupService = lookupService;
        this.dataScopeService = dataScopeService;
    }

    public PageResult<IcpEvaluationTask> page(long pageNum, long pageSize, Long planId, Long evaluatorId,
                                              Integer status, String keyword) {
        DataScopeInfo info = dataScopeService.current();
        LambdaQueryWrapper<IcpEvaluationTask> wrapper = new LambdaQueryWrapper<IcpEvaluationTask>()
                .orderByDesc(IcpEvaluationTask::getId);
        IcpScope.apply(wrapper, info, IcpEvaluationTask::getEvaluateDeptId, IcpEvaluationTask::getEvaluatorId);
        if (planId != null) {
            wrapper.eq(IcpEvaluationTask::getPlanId, planId);
        }
        if (evaluatorId != null) {
            wrapper.eq(IcpEvaluationTask::getEvaluatorId, evaluatorId);
        }
        if (status != null) {
            wrapper.eq(IcpEvaluationTask::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(q -> q.like(IcpEvaluationTask::getTaskName, kw)
                    .or().like(IcpEvaluationTask::getTaskCode, kw));
        }
        Page<IcpEvaluationTask> page = taskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillNames(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public IcpEvaluationTask get(Long id) {
        IcpEvaluationTask task = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), task.getEvaluateDeptId(), task.getEvaluatorId());
        fillNames(List.of(task));
        return task;
    }

    @Transactional
    public IcpEvaluationTask update(Long id, IcpEvaluationTask request) {
        IcpEvaluationTask existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getEvaluateDeptId(), existing.getEvaluatorId());
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (existing.getStatus() != null && existing.getStatus() == DONE) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "已完成的任务不可编辑");
        }
        if (request.getStatus() != null && !Objects.equals(request.getStatus(), existing.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "任务状态请使用开始/提交接口变更");
        }
        if (StringUtils.hasText(request.getTaskName())) {
            existing.setTaskName(request.getTaskName().trim());
        }
        if (request.getEvaluatorId() != null) {
            existing.setEvaluatorId(request.getEvaluatorId());
        }
        if (request.getStartDate() != null) {
            existing.setStartDate(request.getStartDate());
        }
        if (request.getDueDate() != null) {
            existing.setDueDate(request.getDueDate());
        }
        if (request.getRemark() != null) {
            existing.setRemark(request.getRemark());
        }
        validate(existing);
        taskMapper.updateById(existing);
        fillNames(List.of(existing));
        return existing;
    }

    /** 开始：0 → 1。 */
    @Transactional
    public IcpEvaluationTask start(Long id) {
        IcpEvaluationTask existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getEvaluateDeptId(), existing.getEvaluatorId());
        if (existing.getStatus() != TODO) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅待开始状态的任务可开始");
        }
        existing.setStatus(RUNNING);
        if (existing.getStartDate() == null) {
            existing.setStartDate(LocalDate.now());
        }
        taskMapper.updateById(existing);
        fillNames(List.of(existing));
        return existing;
    }

    /** 提交：1 → 2，需评分 + 结论 + 至少一份底稿（底稿可在提交时一并创建）。 */
    @Transactional
    public Map<String, Object> submit(Long id, IcpRequests.TaskSubmit request) {
        IcpEvaluationTask existing = require(id);
        IcpScope.check(dataScopeService, dataScopeService.current(), existing.getEvaluateDeptId(), existing.getEvaluatorId());
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        if (existing.getStatus() != RUNNING) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅进行中状态的任务可提交");
        }
        if (request.score() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "评分不能为空");
        }
        if (request.score().compareTo(BigDecimal.ZERO) < 0
                || request.score().compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "评分取值范围为 0-100");
        }
        if (request.conclusion() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "评价结论不能为空");
        }
        if (request.workpaper() != null) {
            insertWorkpaper(id, request.workpaper());
        }
        long count = workpaperMapper.selectCount(new LambdaQueryWrapper<IcpEvaluationWorkpaper>()
                .eq(IcpEvaluationWorkpaper::getTaskId, id));
        if (count == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "评价完成前必须至少提交一份底稿");
        }
        existing.setStatus(DONE);
        existing.setScore(request.score());
        existing.setConclusion(request.conclusion());
        taskMapper.updateById(existing);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", existing.getId());
        data.put("taskCode", existing.getTaskCode());
        data.put("status", existing.getStatus());
        data.put("score", existing.getScore());
        data.put("conclusion", existing.getConclusion());
        data.put("workpaperCount", count);
        return data;
    }

    public List<IcpEvaluationWorkpaper> workpapers(Long taskId) {
        IcpEvaluationTask task = require(taskId);
        IcpScope.check(dataScopeService, dataScopeService.current(), task.getEvaluateDeptId(), task.getEvaluatorId());
        return workpaperMapper.selectList(new LambdaQueryWrapper<IcpEvaluationWorkpaper>()
                .eq(IcpEvaluationWorkpaper::getTaskId, taskId)
                .orderByAsc(IcpEvaluationWorkpaper::getId));
    }

    @Transactional
    public IcpEvaluationWorkpaper addWorkpaper(Long taskId, IcpRequests.Workpaper request) {
        IcpEvaluationTask task = require(taskId);
        IcpScope.check(dataScopeService, dataScopeService.current(), task.getEvaluateDeptId(), task.getEvaluatorId());
        if (task.getStatus() != null && task.getStatus() == DONE) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "已完成的任务不可追加底稿");
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        return insertWorkpaper(taskId, request);
    }

    private IcpEvaluationWorkpaper insertWorkpaper(Long taskId, IcpRequests.Workpaper request) {
        if (!StringUtils.hasText(request.sampleDesc())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "抽样说明不能为空");
        }
        if (!StringUtils.hasText(request.testDesc())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "测试说明不能为空");
        }
        if (request.testResult() == null || request.testResult() < 1 || request.testResult() > 3) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "测试结果只能为 1 符合 / 2 部分符合 / 3 不符合");
        }
        IcpEvaluationWorkpaper workpaper = new IcpEvaluationWorkpaper();
        workpaper.setTaskId(taskId);
        workpaper.setSampleDesc(request.sampleDesc());
        workpaper.setTestDesc(request.testDesc());
        workpaper.setTestResult(request.testResult());
        workpaper.setEvidence(toEvidence(request.evidence()));
        workpaper.setConclusion(request.conclusion());
        workpaper.setWorkUserId(SecurityUtils.currentUserIdOrNull());
        workpaperMapper.insert(workpaper);
        return workpaper;
    }

    private String toEvidence(List<Long> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return null;
        }
        String joined = evidence.stream().filter(Objects::nonNull).map(String::valueOf)
                .collect(Collectors.joining(","));
        if (joined.length() > 500) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "证据数量过多");
        }
        return joined;
    }

    private IcpEvaluationTask require(Long id) {
        IcpEvaluationTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评价任务不存在");
        }
        return task;
    }

    private void validate(IcpEvaluationTask task) {
        if (task.getStartDate() != null && task.getDueDate() != null
                && task.getStartDate().isAfter(task.getDueDate())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "开始日期不能晚于截止日期");
        }
        if (task.getStatus() != null && (task.getStatus() < 0 || task.getStatus() > 3)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "任务状态不合法");
        }
    }

    private void fillNames(List<IcpEvaluationTask> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> planIds = records.stream().map(IcpEvaluationTask::getPlanId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> planNames = new java.util.HashMap<>();
        if (!planIds.isEmpty()) {
            for (IcpEvaluation plan : evaluationMapper.selectBatchIds(planIds)) {
                planNames.put(plan.getId(), plan.getPlanName());
            }
        }
        for (IcpEvaluationTask record : records) {
            record.setPlanName(planNames.get(record.getPlanId()));
            Set<Long> riskIds = record.getRiskId() == null ? Set.of() : Set.of(record.getRiskId());
            Set<Long> controlIds = record.getControlId() == null ? Set.of() : Set.of(record.getControlId());
            record.setRiskName(lookupService.riskLabels(riskIds).get(record.getRiskId()));
            record.setControlName(lookupService.controlLabels(controlIds).get(record.getControlId()));
            record.setWorkpaperCount(workpaperMapper.selectCount(new LambdaQueryWrapper<IcpEvaluationWorkpaper>()
                    .eq(IcpEvaluationWorkpaper::getTaskId, record.getId())).intValue());
        }
    }
}

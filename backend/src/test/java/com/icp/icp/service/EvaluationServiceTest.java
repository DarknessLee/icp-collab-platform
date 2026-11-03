package com.icp.icp.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.icp.entity.IcpEvaluation;
import com.icp.icp.entity.IcpEvaluationTask;
import com.icp.icp.entity.IcpMatrix;
import com.icp.icp.entity.IcpRisk;
import com.icp.icp.mapper.IcpEvaluationMapper;
import com.icp.icp.mapper.IcpEvaluationTaskMapper;
import com.icp.icp.mapper.IcpMatrixMapper;
import com.icp.icp.mapper.IcpRiskMapper;
import com.icp.icp.support.IcpLookupService;
import com.icp.system.service.DataScopeService;
import com.icp.workflow.dto.WorkflowRequests;
import com.icp.workflow.service.WorkflowInstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评价计划单测：送审暂不接 Flowable、开始按矩阵生成任务且重复调用幂等、完成要求全部任务已完成。
 */
@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisMetadata() {
        com.icp.testsupport.MybatisTestSupport.init();
    }

    @Mock
    private IcpEvaluationMapper evaluationMapper;
    @Mock
    private IcpEvaluationTaskMapper taskMapper;
    @Mock
    private IcpMatrixMapper matrixMapper;
    @Mock
    private IcpRiskMapper riskMapper;
    @Mock
    private IcpLookupService lookupService;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private WorkflowInstanceService workflowInstanceService;

    private EvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        evaluationService = new EvaluationService(evaluationMapper, taskMapper, matrixMapper, riskMapper,
                lookupService, dataScopeService, workflowInstanceService);
        lenient().when(dataScopeService.current())
                .thenReturn(new DataScopeInfo(1L, 2L, DataScopeInfo.ALL, Collections.emptyList()));
        lenient().when(workflowInstanceService.start(any(WorkflowRequests.StartRequest.class), any()))
                .thenReturn(Map.of("processInstanceId", "pi-eval-1", "taskId", "task-eval-1",
                        "businessNo", "PJ-2026-05"));
    }

    @Test
    void submitDraftStartsWorkflowAndReturnsInstance() {
        when(evaluationMapper.selectById(1604L)).thenReturn(plan(1604L, 0), plan(1604L, 1));
        when(taskMapper.countByPlan(1604L)).thenReturn(0L);

        Map<String, Object> result = evaluationService.submit(1604L);

        assertThat(result.get("status")).isEqualTo(1);
        assertThat(result.get("pendingWorkflow")).isEqualTo(false);
        assertThat(result.get("processInstanceId")).isEqualTo("pi-eval-1");
        org.mockito.Mockito.verify(workflowInstanceService)
                .start(any(WorkflowRequests.StartRequest.class), any());
    }

    @Test
    void startGeneratesOneTaskPerMatrix() {
        when(evaluationMapper.selectById(1605L)).thenReturn(plan(1605L, 1));
        when(taskMapper.countByPlan(1605L)).thenReturn(0L);
        when(matrixMapper.selectList(any())).thenReturn(List.of(matrix(1301L, 1401L), matrix(1302L, 1402L)));
        when(riskMapper.selectBatchIds(any())).thenReturn(List.of(risk(1301L), risk(1302L)));
        when(taskMapper.insert(any(IcpEvaluationTask.class))).thenReturn(1);

        Map<String, Object> result = evaluationService.start(1605L);

        assertThat(result.get("status")).isEqualTo(2);
        assertThat(result.get("generatedTasks")).isEqualTo(2);
        verify(taskMapper, org.mockito.Mockito.times(2)).insert(any(IcpEvaluationTask.class));
    }

    @Test
    void startIsIdempotentWhenAlreadyRunning() {
        when(evaluationMapper.selectById(1603L)).thenReturn(plan(1603L, 2));
        when(taskMapper.countByPlan(1603L)).thenReturn(20L);

        Map<String, Object> result = evaluationService.start(1603L);

        assertThat(result.get("status")).isEqualTo(2);
        assertThat(result.get("generatedTasks")).isEqualTo(0);
        verify(taskMapper, never()).insert(any(IcpEvaluationTask.class));
        verify(evaluationMapper, never()).updateById(any(IcpEvaluation.class));
    }

    @Test
    void startSkipsGenerationWhenTasksAlreadyExist() {
        when(evaluationMapper.selectById(1606L)).thenReturn(plan(1606L, 1));
        when(taskMapper.countByPlan(1606L)).thenReturn(3L, 3L);

        Map<String, Object> result = evaluationService.start(1606L);

        assertThat(result.get("status")).isEqualTo(2);
        assertThat(result.get("generatedTasks")).isEqualTo(0);
        verify(taskMapper, never()).insert(any(IcpEvaluationTask.class));
    }

    @Test
    void startDraftRejected() {
        when(evaluationMapper.selectById(1604L)).thenReturn(plan(1604L, 0));

        assertThatThrownBy(() -> evaluationService.start(1604L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void finishBlockedWhileTasksUnfinished() {
        when(evaluationMapper.selectById(1603L)).thenReturn(plan(1603L, 2));
        when(taskMapper.selectList(any())).thenReturn(List.of(task(1L, 2), task(2L, 1)));

        assertThatThrownBy(() -> evaluationService.finish(1603L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未完成");
    }

    @Test
    void finishRequiresTasks() {
        when(evaluationMapper.selectById(1603L)).thenReturn(plan(1603L, 2));
        when(taskMapper.selectList(any())).thenReturn(new ArrayList<>());

        assertThatThrownBy(() -> evaluationService.finish(1603L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未生成评价任务");
    }

    @Test
    void finishAndCloseHappyPath() {
        IcpEvaluation running = plan(1603L, 2);
        when(evaluationMapper.selectById(1603L)).thenReturn(running, plan(1603L, 3));
        when(taskMapper.countByPlan(1603L)).thenReturn(1L);
        when(taskMapper.selectList(any())).thenReturn(List.of(task(1L, 2)));

        Map<String, Object> finished = evaluationService.finish(1603L);
        assertThat(finished.get("status")).isEqualTo(3);

        Map<String, Object> closed = evaluationService.close(1603L);
        assertThat(closed.get("status")).isEqualTo(4);
    }

    private IcpEvaluation plan(Long id, int status) {
        IcpEvaluation plan = new IcpEvaluation();
        plan.setId(id);
        plan.setPlanCode("PJ-2026-05");
        plan.setPlanName("测试评价计划");
        plan.setPlanYear(2026);
        plan.setStartDate(LocalDate.of(2026, 10, 1));
        plan.setEndDate(LocalDate.of(2026, 12, 31));
        plan.setOwnerDeptId(2L);
        plan.setOwnerUserId(3L);
        plan.setStatus(status);
        return plan;
    }

    private IcpMatrix matrix(Long riskId, Long controlId) {
        IcpMatrix matrix = new IcpMatrix();
        matrix.setRiskId(riskId);
        matrix.setControlId(controlId);
        matrix.setStatus(1);
        return matrix;
    }

    private IcpRisk risk(Long id) {
        IcpRisk risk = new IcpRisk();
        risk.setId(id);
        risk.setRiskName("风险" + id);
        return risk;
    }

    private IcpEvaluationTask task(Long id, int status) {
        IcpEvaluationTask task = new IcpEvaluationTask();
        task.setId(id);
        task.setPlanId(1603L);
        task.setStatus(status);
        return task;
    }
}

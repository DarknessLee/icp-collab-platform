package com.icp.icp.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.icp.dto.IcpRequests;
import com.icp.icp.entity.IcpEvaluationTask;
import com.icp.icp.entity.IcpEvaluationWorkpaper;
import com.icp.icp.mapper.IcpEvaluationMapper;
import com.icp.icp.mapper.IcpEvaluationTaskMapper;
import com.icp.icp.mapper.IcpEvaluationWorkpaperMapper;
import com.icp.icp.support.IcpLookupService;
import com.icp.system.service.DataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评价任务单测：开始 0->1、提交 1->2 需评分+结论+底稿、已完成任务不可追加底稿。
 */
@ExtendWith(MockitoExtension.class)
class EvaluationTaskServiceTest {

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisMetadata() {
        com.icp.testsupport.MybatisTestSupport.init();
    }

    @Mock
    private IcpEvaluationTaskMapper taskMapper;
    @Mock
    private IcpEvaluationWorkpaperMapper workpaperMapper;
    @Mock
    private IcpEvaluationMapper evaluationMapper;
    @Mock
    private IcpLookupService lookupService;
    @Mock
    private DataScopeService dataScopeService;

    private EvaluationTaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new EvaluationTaskService(taskMapper, workpaperMapper, evaluationMapper,
                lookupService, dataScopeService);
        lenient().when(dataScopeService.current())
                .thenReturn(new DataScopeInfo(1L, 2L, DataScopeInfo.ALL, Collections.emptyList()));
        lenient().when(workpaperMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    void startMovesTodoToRunning() {
        when(taskMapper.selectById(1621L)).thenReturn(task(1621L, 0));

        IcpEvaluationTask started = taskService.start(1621L);

        assertThat(started.getStatus()).isEqualTo(1);
        assertThat(started.getStartDate()).isNotNull();
        verify(taskMapper).updateById(any(IcpEvaluationTask.class));
    }

    @Test
    void startAlreadyRunningRejected() {
        when(taskMapper.selectById(1622L)).thenReturn(task(1622L, 1));

        assertThatThrownBy(() -> taskService.start(1622L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void submitWithoutWorkpaperRejected() {
        when(taskMapper.selectById(1623L)).thenReturn(task(1623L, 1));
        when(workpaperMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> taskService.submit(1623L,
                new IcpRequests.TaskSubmit(new BigDecimal("86.50"), 1, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("底稿");
    }

    @Test
    void submitWithWorkpaperCompletesTask() {
        when(taskMapper.selectById(1623L)).thenReturn(task(1623L, 1));
        when(workpaperMapper.insert(any(IcpEvaluationWorkpaper.class))).thenReturn(1);
        when(workpaperMapper.selectCount(any())).thenReturn(1L);

        Map<String, Object> result = taskService.submit(1623L,
                new IcpRequests.TaskSubmit(new BigDecimal("86.50"), 1,
                        new IcpRequests.Workpaper("抽样 10 笔", "核对审批流", 2,
                                List.of(4001L, 4002L), "基本符合")));

        assertThat(result.get("status")).isEqualTo(2);
        assertThat(result.get("score")).isEqualTo(new BigDecimal("86.50"));
        assertThat(result.get("workpaperCount")).isEqualTo(1L);
        verify(workpaperMapper).insert(any(IcpEvaluationWorkpaper.class));
        verify(taskMapper).updateById(any(IcpEvaluationTask.class));
    }

    @Test
    void submitScoreOutOfRangeRejected() {
        when(taskMapper.selectById(1623L)).thenReturn(task(1623L, 1));

        assertThatThrownBy(() -> taskService.submit(1623L,
                new IcpRequests.TaskSubmit(new BigDecimal("101"), 1, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0-100");
    }

    @Test
    void addWorkpaperBlockedWhenTaskDone() {
        when(taskMapper.selectById(1624L)).thenReturn(task(1624L, 2));

        assertThatThrownBy(() -> taskService.addWorkpaper(1624L,
                new IcpRequests.Workpaper("抽样", "测试", 1, null, "符合")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void addWorkpaperValidatesRequiredFields() {
        when(taskMapper.selectById(1623L)).thenReturn(task(1623L, 1));

        assertThatThrownBy(() -> taskService.addWorkpaper(1623L,
                new IcpRequests.Workpaper(" ", "测试", 1, null, "符合")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("抽样说明");

        assertThatThrownBy(() -> taskService.addWorkpaper(1623L,
                new IcpRequests.Workpaper("抽样", "测试", 4, null, "符合")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("测试结果");
    }

    private IcpEvaluationTask task(Long id, int status) {
        IcpEvaluationTask task = new IcpEvaluationTask();
        task.setId(id);
        task.setTaskCode("PJ-2026-03-01");
        task.setPlanId(1603L);
        task.setTaskName("评价：测试风险");
        task.setEvaluateDeptId(2L);
        task.setEvaluatorId(3L);
        task.setStatus(status);
        return task;
    }
}

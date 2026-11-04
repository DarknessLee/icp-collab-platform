package com.icp.collab.service;

import com.icp.collab.entity.CollMessage;
import com.icp.collab.entity.CollTask;
import com.icp.collab.mapper.CollTaskCommentMapper;
import com.icp.collab.mapper.CollTaskMapper;
import com.icp.collab.mapper.CollabMessageMapper;
import com.icp.collab.support.CollabLookupService;
import com.icp.common.api.ErrorCode;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.system.service.DataScopeService;
import com.icp.testsupport.MybatisTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** M2d 任务状态机 / 逾期 / 权限单测。 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @BeforeAll
    static void initMybatisMetadata() {
        MybatisTestSupport.init();
    }

    @Mock
    private CollTaskMapper taskMapper;

    @Mock
    private CollTaskCommentMapper commentMapper;

    @Mock
    private CollabMessageMapper messageMapper;

    @Mock
    private CollabLookupService lookup;

    @Mock
    private DataScopeService dataScopeService;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskMapper, commentMapper, messageMapper, lookup, dataScopeService);
        lenient().when(lookup.currentUserId()).thenReturn(11L);
        lenient().when(lookup.currentRealName()).thenReturn("员工一");
        lenient().when(lookup.currentDeptId()).thenReturn(2L);
        lenient().when(dataScopeService.current())
                .thenReturn(new DataScopeInfo(11L, 2L, DataScopeInfo.SELF, List.of()));
    }

    private CollTask task(int status, Long creatorId, Long assigneeId, Long deptId) {
        CollTask task = new CollTask();
        task.setId(5001L);
        task.setTaskNo("RW-2026-0081");
        task.setTaskTitle("测试任务");
        task.setStatus(status);
        task.setPriority(2);
        task.setTaskType(2);
        task.setCreatorId(creatorId);
        task.setAssigneeId(assigneeId);
        task.setDeptId(deptId);
        task.setPlanEnd(LocalDate.now().plusDays(3));
        return task;
    }

    @Test
    void startMovesTodoToDoing() {
        when(taskMapper.selectById(5001L)).thenReturn(task(0, 11L, 11L, 2L));

        Map<String, Object> result = service.start(5001L);

        assertThat(result.get("status")).isEqualTo(1);
        assertThat(result.get("actualStart")).isNotNull();
        verify(taskMapper).updateById(any(CollTask.class));
    }

    @Test
    void startRejectsNonTodoStatus() {
        when(taskMapper.selectById(5001L)).thenReturn(task(1, 11L, 11L, 2L));

        assertThatThrownBy(() -> service.start(5001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void completeSetsProgressAndActualEnd() {
        CollTask task = task(0, 11L, 11L, 2L);
        when(taskMapper.selectById(5001L)).thenReturn(task);

        Map<String, Object> result = service.complete(5001L);

        assertThat(result.get("status")).isEqualTo(2);
        assertThat(task.getProgress()).isEqualTo(100);
        assertThat(task.getActualEnd()).isNotNull();
        assertThat(task.getActualStart()).isNotNull();
    }

    @Test
    void completeRejectsCancelledTask() {
        when(taskMapper.selectById(5001L)).thenReturn(task(3, 11L, 11L, 2L));

        assertThatThrownBy(() -> service.complete(5001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void cancelMovesDoingToCancelled() {
        CollTask task = task(1, 3L, 11L, 2L);
        when(taskMapper.selectById(5001L)).thenReturn(task);

        Map<String, Object> result = service.cancel(5001L);

        assertThat(result.get("status")).isEqualTo(3);
        verify(taskMapper).updateById(task);
    }

    @Test
    void deleteRejectsTaskOfOtherUser() {
        when(taskMapper.selectById(5001L)).thenReturn(task(0, 3L, 4L, 5L));

        assertThatThrownBy(() -> service.delete(5001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
        verify(taskMapper, never()).deleteById(5001L);
    }

    @Test
    void deleteRejectsTaskWithChildren() {
        when(taskMapper.selectById(5001L)).thenReturn(task(0, 11L, 11L, 2L));
        when(taskMapper.countChildren(5001L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(5001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
        verify(taskMapper, never()).deleteById(5001L);
    }

    @Test
    void deleteByCreatorRemovesTask() {
        when(taskMapper.selectById(5001L)).thenReturn(task(0, 11L, 11L, 2L));
        when(taskMapper.countChildren(5001L)).thenReturn(0L);

        service.delete(5001L);

        verify(taskMapper).deleteById(5001L);
    }

    @Test
    void overdueFlagIsComputedAtQueryTime() {
        CollTask past = task(1, 11L, 11L, 2L);
        past.setPlanEnd(LocalDate.now().minusDays(1));
        CollTask done = task(2, 11L, 11L, 2L);
        done.setId(5002L);
        done.setPlanEnd(LocalDate.now().minusDays(1));

        assertThat(service.isOverdue(past, LocalDate.now())).isTrue();
        assertThat(service.isOverdue(done, LocalDate.now())).isFalse();
    }

    @Test
    void remindInsertsTaskMessageToAssignee() {
        when(taskMapper.selectById(5001L)).thenReturn(task(0, 3L, 11L, 2L));
        doAnswer(invocation -> {
            CollMessage message = invocation.getArgument(0);
            message.setId(9001L);
            return 1;
        }).when(messageMapper).insert(any(CollMessage.class));

        Map<String, Object> result = service.remind(5001L);

        assertThat(result.get("receiverId")).isEqualTo(11L);
        assertThat(result.get("messageType")).isEqualTo(3);
        assertThat(result.get("bizType")).isEqualTo("REMIND");
        verify(messageMapper).insert(any(CollMessage.class));
    }

    @Test
    void remindRejectsUnrelatedUser() {
        when(taskMapper.selectById(5001L)).thenReturn(task(0, 3L, 4L, 5L));

        assertThatThrownBy(() -> service.remind(5001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
        verify(messageMapper, never()).insert(any(CollMessage.class));
    }
}

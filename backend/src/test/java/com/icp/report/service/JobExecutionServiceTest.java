package com.icp.report.service;

import com.icp.collab.entity.CollNotice;
import com.icp.collab.entity.CollTask;
import com.icp.collab.mapper.CollabMessageMapper;
import com.icp.collab.mapper.CollNoticeMapper;
import com.icp.collab.mapper.CollTaskMapper;
import com.icp.collab.service.MessageService;
import com.icp.icp.entity.IcpPolicy;
import com.icp.icp.mapper.IcpEvaluationTaskMapper;
import com.icp.icp.mapper.IcpPolicyMapper;
import com.icp.icp.mapper.IcpRectifyMapper;
import com.icp.system.entity.SysJob;
import com.icp.system.entity.SysOperLog;
import com.icp.system.mapper.SysOperLogMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobExecutionServiceTest {

    @BeforeAll
    static void initMybatisMetadata() {
        com.icp.testsupport.MybatisTestSupport.init();
    }

    @Mock
    private MessageService messageService;
    @Mock
    private CollabMessageMapper messageMapper;
    @Mock
    private SysOperLogMapper operLogMapper;
    @Mock
    private IcpPolicyMapper policyMapper;
    @Mock
    private IcpEvaluationTaskMapper evaluationTaskMapper;
    @Mock
    private CollTaskMapper taskMapper;
    @Mock
    private IcpRectifyMapper rectifyMapper;
    @Mock
    private CollNoticeMapper noticeMapper;

    private JobExecutionService service;

    @BeforeEach
    void setUp() {
        service = new JobExecutionService(messageService, messageMapper, operLogMapper, policyMapper,
                evaluationTaskMapper, taskMapper, rectifyMapper, noticeMapper);
    }

    private SysJob job(long id, String name, int status, String target) {
        SysJob job = new SysJob();
        job.setId(id);
        job.setJobName(name);
        job.setStatus(status);
        job.setInvokeTarget(target);
        return job;
    }

    private CollTask task(long id, long assigneeId, int status, LocalDate planEnd) {
        CollTask task = new CollTask();
        task.setId(id);
        task.setTaskNo("RW-2026-0090");
        task.setTaskTitle("任务标题");
        task.setAssigneeId(assigneeId);
        task.setStatus(status);
        task.setPlanEnd(planEnd);
        return task;
    }

    @Test
    void disabledJobReturnsSkippedWithoutExecution() {
        SysJob job = job(1L, "任务到期提醒", 0, "collTaskJob.remind");
        Map<String, Object> result = service.run(job, true);
        assertThat(result.get("executed")).isEqualTo(false);
        assertThat(result.get("skipped")).isEqualTo(true);
        assertThat(result.get("matched")).isEqualTo(0);
        verify(messageService, never()).push(anyLong(), anyLong(), any(Integer.class), anyString(),
                anyString(), anyString(), anyLong());
        verify(operLogMapper).insert(any(SysOperLog.class));
    }

    @Test
    void unknownTargetReturnsZeroWithoutMessage() {
        Map<String, Object> result = service.run(job(9L, "未知任务", 1, "foo.bar"), false);
        assertThat(result.get("executed")).isEqualTo(true);
        assertThat(result.get("matched")).isEqualTo(0);
        assertThat(String.valueOf(result.get("message"))).contains("未识别");
        verify(messageService, never()).push(anyLong(), anyLong(), any(Integer.class), anyString(),
                anyString(), anyString(), anyLong());
        verify(operLogMapper).insert(any(SysOperLog.class));
    }

    @Test
    void collTaskRemindSendsMessageAndOperLog() {
        when(taskMapper.selectList(any())).thenReturn(List.of(task(3090L, 5L, 0, LocalDate.now().plusDays(1))));
        when(messageMapper.selectCount(any())).thenReturn(0L);

        Map<String, Object> result = service.run(job(3L, "任务到期提醒", 1, "collTaskJob.remind"), true);

        assertThat(result.get("matched")).isEqualTo(1);
        assertThat(result.get("deduped")).isEqualTo(0);
        assertThat(result.get("messages")).isEqualTo(1);
        verify(messageService).push(eq(5L), eq(1L), eq(3), eq("任务到期提醒"), anyString(),
                eq("REMIND"), eq(3090L));
        verify(operLogMapper).insert(any(SysOperLog.class));
    }

    @Test
    void dedupeSameDayPreventsDuplicateMessage() {
        when(taskMapper.selectList(any())).thenReturn(List.of(task(3090L, 5L, 1, LocalDate.now())));
        when(messageMapper.selectCount(any())).thenReturn(1L);

        Map<String, Object> result = service.run(job(3L, "任务到期提醒", 1, "collTaskJob.remind"), true);

        assertThat(result.get("matched")).isEqualTo(1);
        assertThat(result.get("deduped")).isEqualTo(1);
        assertThat(result.get("messages")).isEqualTo(0);
        verify(messageService, never()).push(anyLong(), anyLong(), any(Integer.class), anyString(),
                anyString(), anyString(), anyLong());
    }

    @Test
    void policyExpireFallsBackToAdminReceiver() {
        IcpPolicy policy = new IcpPolicy();
        policy.setId(1001L);
        policy.setPolicyCode("ZD-2026-0001");
        policy.setPolicyName("内控制度");
        policy.setExpireDate(LocalDate.now().plusDays(20));
        policy.setStatus(2);
        when(policyMapper.selectList(any())).thenReturn(List.of(policy));
        when(messageMapper.selectCount(any())).thenReturn(0L);

        Map<String, Object> result = service.run(job(1L, "制度到期扫描", 1, "policyExpireJob.scan"), true);

        assertThat(result.get("matched")).isEqualTo(1);
        assertThat(result.get("messages")).isEqualTo(1);
        verify(messageService).push(eq(1L), eq(1L), eq(3), eq("制度到期提醒"), anyString(),
                eq("ALERT"), eq(1001L));
    }

    @Test
    void noticePublishUpdatesStatusAndNotifiesPublisher() {
        CollNotice notice = new CollNotice();
        notice.setId(4001L);
        notice.setNoticeTitle("定时公告");
        notice.setStatus(0);
        notice.setPublisherId(3L);
        notice.setPublishTime(LocalDateTime.now().minusMinutes(5));
        when(noticeMapper.selectList(any())).thenReturn(List.of(notice));
        when(messageMapper.selectCount(any())).thenReturn(0L);

        Map<String, Object> result = service.run(job(5L, "公告定时发布", 1, "noticeJob.publishScheduled"), false);

        assertThat(result.get("matched")).isEqualTo(1);
        assertThat(result.get("messages")).isEqualTo(1);
        verify(noticeMapper).update(any(), any());
        verify(messageService).push(eq(3L), eq(1L), eq(3), eq("公告已发布"), anyString(),
                eq("ALERT"), eq(4001L));
    }
}

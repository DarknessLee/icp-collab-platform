package com.icp.collab.service;

import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollSchedule;
import com.icp.collab.mapper.CollScheduleMapper;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** M2d 日程时间校验 / 取消状态单测。 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @BeforeAll
    static void initMybatisMetadata() {
        MybatisTestSupport.init();
    }

    @Mock
    private CollScheduleMapper scheduleMapper;

    @Mock
    private CollabLookupService lookup;

    @Mock
    private DataScopeService dataScopeService;

    private ScheduleService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleService(scheduleMapper, lookup, dataScopeService);
        lenient().when(lookup.currentUserId()).thenReturn(11L);
        lenient().when(lookup.currentDeptId()).thenReturn(2L);
        lenient().when(lookup.userNames(any())).thenReturn(Map.of());
        lenient().when(dataScopeService.current())
                .thenReturn(new DataScopeInfo(11L, 2L, DataScopeInfo.SELF, List.of()));
    }

    private CollSchedule schedule(int status) {
        CollSchedule schedule = new CollSchedule();
        schedule.setId(7001L);
        schedule.setScheduleTitle("测试日程");
        schedule.setScheduleType(3);
        schedule.setStartTime(LocalDateTime.of(2026, 11, 10, 9, 0));
        schedule.setCreatorId(11L);
        schedule.setDeptId(2L);
        schedule.setStatus(status);
        return schedule;
    }

    @Test
    void createRejectsEndBeforeStart() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 10, 9, 0);
        assertThatThrownBy(() -> service.create(new CollabRequests.ScheduleCreate(
                "日程", 3, start, start.minusHours(1), null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void createDefaultsStatusPendingAndCreator() {
        when(scheduleMapper.insert(any(CollSchedule.class))).thenAnswer(invocation -> {
            CollSchedule created = invocation.getArgument(0);
            created.setId(7002L);
            return 1;
        });

        CollSchedule created = service.create(new CollabRequests.ScheduleCreate(
                "会议", 1, LocalDateTime.of(2026, 11, 10, 9, 0),
                LocalDateTime.of(2026, 11, 10, 10, 0), "会议室A", List.of(), null, null, null, null));

        assertThat(created.getStatus()).isZero();
        assertThat(created.getCreatorId()).isEqualTo(11L);
        assertThat(created.getScheduleType()).isEqualTo(1);
        verify(scheduleMapper).insert(any(CollSchedule.class));
    }

    @Test
    void cancelMovesPendingToCancelled() {
        when(scheduleMapper.selectById(7001L)).thenReturn(schedule(0));

        Map<String, Object> result = service.cancel(7001L);

        assertThat(result.get("status")).isEqualTo(3);
        verify(scheduleMapper).updateById(any(CollSchedule.class));
    }

    @Test
    void cancelRejectsFinishedSchedule() {
        when(scheduleMapper.selectById(7001L)).thenReturn(schedule(2));

        assertThatThrownBy(() -> service.cancel(7001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void cancelRejectsNonCreator() {
        CollSchedule other = schedule(0);
        other.setCreatorId(3L);
        when(scheduleMapper.selectById(7001L)).thenReturn(other);

        assertThatThrownBy(() -> service.cancel(7001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void calendarRejectsInvalidMonth() {
        assertThatThrownBy(() -> service.calendar("2026/11"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void calendarReturnsMonthItems() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of(schedule(0)));

        Map<String, Object> result = service.calendar("2026-11");

        assertThat(result.get("month")).isEqualTo("2026-11");
        assertThat((List<?>) result.get("items")).hasSize(1);
    }
}

package com.icp.collab.service;

import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollMeeting;
import com.icp.collab.mapper.CollMeetingMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** W3-a 会议纪要：编号生成 / 状态流转 / 数据权限单测。 */
@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @BeforeAll
    static void initMybatisMetadata() {
        MybatisTestSupport.init();
    }

    @Mock
    private CollMeetingMapper meetingMapper;

    @Mock
    private CollabLookupService lookup;

    @Mock
    private DataScopeService dataScopeService;

    private MeetingService service;

    @BeforeEach
    void setUp() {
        service = new MeetingService(meetingMapper, lookup, dataScopeService);
        lenient().when(lookup.currentUserId()).thenReturn(11L);
        lenient().when(lookup.userName(anyLong())).thenReturn("孙丽");
        lenient().when(lookup.userDeptId(anyLong())).thenReturn(2L);
        lenient().when(lookup.deptName(anyLong())).thenReturn("内控合规部");
        lenient().when(lookup.isAdmin()).thenReturn(false);
        lenient().when(dataScopeService.current())
                .thenReturn(new DataScopeInfo(11L, 2L, DataScopeInfo.SELF, List.of()));
        lenient().when(dataScopeService.canAccess(any(), any(), any())).thenReturn(true);
    }

    private CollMeeting meeting(Integer status) {
        CollMeeting meeting = new CollMeeting();
        meeting.setId(3501L);
        meeting.setMeetingNo("HY-2026-001");
        meeting.setTitle("内控工作例会");
        meeting.setMeetingType("0");
        meeting.setStartTime(LocalDateTime.of(2026, 11, 10, 9, 0));
        meeting.setEndTime(LocalDateTime.of(2026, 11, 10, 10, 30));
        meeting.setOrganizerId(11L);
        meeting.setOrganizerName("孙丽");
        meeting.setDeptId(2L);
        meeting.setDeptName("内控合规部");
        meeting.setStatus(status);
        return meeting;
    }

    @Test
    void createGeneratesMeetingNoAndPendingStatus() {
        when(meetingMapper.selectMaxNo()).thenReturn("HY-2026-019");
        CollMeeting created = service.create(new CollabRequests.MeetingCreate(
                "第四季度内控工作例会", "0",
                LocalDateTime.of(2026, 11, 10, 14, 0), LocalDateTime.of(2026, 11, 10, 15, 30),
                "总部 3 层会议室", null, null, null, null, "赵敏、张伟、王芳", null, null, null));

        assertThat(created.getMeetingNo()).isEqualTo("HY-2026-020");
        assertThat(created.getStatus()).isEqualTo(MeetingService.STATUS_PENDING);
        assertThat(created.getOrganizerId()).isEqualTo(11L);
        assertThat(created.getOrganizerName()).isEqualTo("孙丽");
        assertThat(created.getDeptId()).isEqualTo(2L);
        assertThat(created.getDeptName()).isEqualTo("内控合规部");
        assertThat(created.getAttendeeCount()).isEqualTo(3);
        verify(meetingMapper).insert(any(CollMeeting.class));
    }

    @Test
    void createRejectsEndBeforeStart() {
        assertThatThrownBy(() -> service.create(new CollabRequests.MeetingCreate(
                "专题会", "1",
                LocalDateTime.of(2026, 11, 10, 9, 0), LocalDateTime.of(2026, 11, 10, 8, 0),
                null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void createRejectsIllegalMeetingType() {
        assertThatThrownBy(() -> service.create(new CollabRequests.MeetingCreate(
                "专题会", "9",
                LocalDateTime.of(2026, 11, 10, 9, 0), null,
                null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void writeMinutesSetsHeldStatus() {
        CollMeeting pending = meeting(MeetingService.STATUS_PENDING);
        when(meetingMapper.selectById(3501L)).thenReturn(pending);

        CollMeeting updated = service.writeMinutes(3501L,
                new CollabRequests.MeetingMinutes("会议通报了本月内控执行情况。议定事项：一、下月检查采购流程。"));

        assertThat(updated.getStatus()).isEqualTo(MeetingService.STATUS_HELD);
        assertThat(updated.getMinutes()).contains("议定事项");
        verify(meetingMapper).updateById(pending);
    }

    @Test
    void writeMinutesRejectsCancelledMeeting() {
        when(meetingMapper.selectById(3501L)).thenReturn(meeting(MeetingService.STATUS_CANCELLED));

        assertThatThrownBy(() -> service.writeMinutes(3501L,
                new CollabRequests.MeetingMinutes("补录纪要内容")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void deleteByNonOrganizerRejected() {
        CollMeeting other = meeting(MeetingService.STATUS_PENDING);
        other.setOrganizerId(5L);
        other.setDeptId(2L);
        when(meetingMapper.selectById(3501L)).thenReturn(other);

        assertThatThrownBy(() -> service.delete(3501L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void getOutOfDeptScopeRejected() {
        CollMeeting otherDept = meeting(MeetingService.STATUS_PENDING);
        otherDept.setOrganizerId(5L);
        otherDept.setDeptId(8L);
        when(meetingMapper.selectById(3501L)).thenReturn(otherDept);
        lenient().when(dataScopeService.canAccess(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.get(3501L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }
}

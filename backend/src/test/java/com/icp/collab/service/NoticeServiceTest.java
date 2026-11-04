package com.icp.collab.service;

import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollNotice;
import com.icp.collab.mapper.CollNoticeMapper;
import com.icp.collab.mapper.CollNoticeReadMapper;
import com.icp.collab.support.CollabLookupService;
import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import com.icp.testsupport.MybatisTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** M2d 公告范围 / 已读 / 状态机单测。 */
@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @BeforeAll
    static void initMybatisMetadata() {
        MybatisTestSupport.init();
    }

    @Mock
    private CollNoticeMapper noticeMapper;

    @Mock
    private CollNoticeReadMapper readMapper;

    @Mock
    private CollabLookupService lookup;

    private NoticeService service;

    @BeforeEach
    void setUp() {
        service = new NoticeService(noticeMapper, readMapper, lookup);
        lenient().when(lookup.currentUserId()).thenReturn(11L);
        lenient().when(lookup.currentDeptId()).thenReturn(2L);
        lenient().when(lookup.currentRoles()).thenReturn(List.of("EMPLOYEE"));
        lenient().when(lookup.isAdmin()).thenReturn(false);
        lenient().when(lookup.userNames(any())).thenReturn(Map.of());
        lenient().when(readMapper.selectReadNoticeIds(any())).thenReturn(List.of());
    }

    private CollNotice notice(Long id, int status, String scope) {
        CollNotice notice = new CollNotice();
        notice.setId(id);
        notice.setNoticeTitle("公告" + id);
        notice.setStatus(status);
        notice.setPublisherId(11L);
        notice.setDeptScope(scope);
        return notice;
    }

    @Test
    void allCompanyScopeAlwaysVisible() {
        assertThat(service.inScope(notice(1L, 1, "全公司"))).isTrue();
        assertThat(service.inScope(notice(2L, 1, null))).isTrue();
    }

    @Test
    void deptScopeMatchesCurrentDept() {
        assertThat(service.inScope(notice(1L, 1, "DEPT:2,3"))).isTrue();
        assertThat(service.inScope(notice(2L, 1, "DEPT:4,5"))).isFalse();
    }

    @Test
    void roleScopeMatchesCurrentRoles() {
        assertThat(service.inScope(notice(1L, 1, "ROLE:EMPLOYEE,AUDITOR"))).isTrue();
        assertThat(service.inScope(notice(2L, 1, "ROLE:AUDITOR"))).isFalse();
    }

    @Test
    void createEncodesDeptScope() {
        when(noticeMapper.insert(any(CollNotice.class))).thenAnswer(invocation -> {
            CollNotice created = invocation.getArgument(0);
            created.setId(6001L);
            return 1;
        });

        CollNotice created = service.create(new CollabRequests.NoticeCreate(
                "部门公告", 2, "内容", 2, List.of(2L, 3L), null, 0, null));

        assertThat(created.getDeptScope()).isEqualTo("DEPT:2,3");
        assertThat(created.getStatus()).isZero();
        assertThat(created.getPublisherId()).isEqualTo(11L);
    }

    @Test
    void createDefaultsToAllCompanyScope() {
        when(noticeMapper.insert(any(CollNotice.class))).thenAnswer(invocation -> {
            CollNotice created = invocation.getArgument(0);
            created.setId(6002L);
            return 1;
        });

        CollNotice created = service.create(new CollabRequests.NoticeCreate(
                "全员公告", null, null, null, null, null, null, null));

        assertThat(created.getDeptScope()).isEqualTo("全公司");
        assertThat(created.getNoticeType()).isEqualTo(2);
    }

    @Test
    void createRejectsDeptScopeWithoutDepts() {
        assertThatThrownBy(() -> service.create(new CollabRequests.NoticeCreate(
                "部门公告", 2, "内容", 2, List.of(), null, 0, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void createRejectsRoleScopeWithoutRoles() {
        assertThatThrownBy(() -> service.create(new CollabRequests.NoticeCreate(
                "角色公告", 2, "内容", 3, null, List.of(), 0, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void publishMovesDraftToPublished() {
        when(noticeMapper.selectById(1L)).thenReturn(notice(1L, 0, "全公司"));

        Map<String, Object> result = service.publish(1L);

        assertThat(result.get("status")).isEqualTo(1);
        verify(noticeMapper).updateById(any(CollNotice.class));
    }

    @Test
    void publishRejectsAlreadyPublished() {
        when(noticeMapper.selectById(1L)).thenReturn(notice(1L, 1, "全公司"));

        assertThatThrownBy(() -> service.publish(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void publishRejectsNonPublisher() {
        CollNotice other = notice(1L, 0, "全公司");
        other.setPublisherId(3L);
        when(noticeMapper.selectById(1L)).thenReturn(other);

        assertThatThrownBy(() -> service.publish(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void readIsIdempotentAndReturnsReaders() {
        when(noticeMapper.selectById(1L)).thenReturn(notice(1L, 1, "全公司"));
        when(readMapper.countReaders(1L)).thenReturn(1L);

        Map<String, Object> result = service.read(1L);

        assertThat(result.get("read")).isEqualTo(true);
        assertThat(result.get("readers")).isEqualTo(1L);
        verify(readMapper).insertIgnore(any(com.icp.collab.entity.CollNoticeRead.class));
    }

    @Test
    void unreadCountExcludesReadNotices() {
        when(noticeMapper.selectList(any())).thenReturn(List.of(
                notice(1L, 1, "全公司"),
                notice(2L, 1, "DEPT:2,3"),
                notice(3L, 1, "ROLE:AUDITOR")));
        when(readMapper.selectReadNoticeIds(11L)).thenReturn(List.of(1L));

        Map<String, Object> result = service.unreadCount();

        assertThat(result.get("unread")).isEqualTo(1L);
        assertThat(result.get("read")).isEqualTo(1);
    }
}

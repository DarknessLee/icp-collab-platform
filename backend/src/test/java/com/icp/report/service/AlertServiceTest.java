package com.icp.report.service;

import com.icp.collab.entity.CollTask;
import com.icp.collab.mapper.CollTaskMapper;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.icp.entity.IcpDefect;
import com.icp.icp.entity.IcpPolicy;
import com.icp.icp.mapper.IcpDefectMapper;
import com.icp.icp.mapper.IcpPolicyMapper;
import com.icp.report.support.ReportScope;
import com.icp.system.service.ConfigService;
import com.icp.workflow.entity.WfBusinessLink;
import com.icp.workflow.mapper.WfBusinessLinkMapper;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @BeforeAll
    static void initMybatisMetadata() {
        com.icp.testsupport.MybatisTestSupport.init();
    }

    @Mock
    private ReportScope scope;
    @Mock
    private ConfigService configService;
    @Mock
    private CollTaskMapper taskMapper;
    @Mock
    private IcpPolicyMapper policyMapper;
    @Mock
    private IcpDefectMapper defectMapper;
    @Mock
    private WfBusinessLinkMapper linkMapper;

    private AlertService service;
    private final DataScopeInfo info = new DataScopeInfo(1L, 2L, DataScopeInfo.ALL, List.of());

    @BeforeEach
    void setUp() {
        service = new AlertService(scope, configService, taskMapper, policyMapper, defectMapper, linkMapper);
        lenient().when(scope.current()).thenReturn(info);
        lenient().when(scope.visibleTask(any(), any(), any(), any())).thenReturn(true);
        lenient().when(scope.visibleByDeptUser(any(), any(), any())).thenReturn(true);
        lenient().when(scope.visibleLink(any(), any())).thenReturn(true);
        lenient().when(configService.getInt(anyString(), anyInt())).thenReturn(48);
    }

    private CollTask task(long id, int status, LocalDate planEnd) {
        CollTask task = new CollTask();
        task.setId(id);
        task.setTaskTitle("逾期任务" + id);
        task.setStatus(status);
        task.setPlanEnd(planEnd);
        return task;
    }

    @Test
    void invalidTypeRejected() {
        assertThatThrownBy(() -> service.page("foo", 1, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("type");
    }

    @Test
    void countAggregatesAllFourTypes() {
        when(taskMapper.selectList(any())).thenReturn(List.of(
                task(1L, 0, LocalDate.now().minusDays(1)),
                task(2L, 1, LocalDate.now().minusDays(5))));
        IcpPolicy policy = new IcpPolicy();
        policy.setId(100L);
        policy.setPolicyName("制度");
        policy.setStatus(2);
        policy.setExpireDate(LocalDate.now().plusDays(10));
        when(policyMapper.selectList(any())).thenReturn(List.of(policy));
        IcpDefect defect = new IcpDefect();
        defect.setId(200L);
        defect.setDefectName("缺陷");
        defect.setSeverity(1);
        defect.setStatus(1);
        when(defectMapper.selectList(any())).thenReturn(List.of(defect));
        WfBusinessLink overdue = new WfBusinessLink();
        overdue.setId(300L);
        overdue.setTitle("流程");
        overdue.setStatus(0);
        overdue.setStartTime(LocalDateTime.now().minusHours(72));
        when(linkMapper.selectList(any())).thenReturn(List.of(overdue));

        Map<String, Object> data = service.count();

        assertThat(data.get("taskOverdue")).isEqualTo(2L);
        assertThat(data.get("policyExpiring")).isEqualTo(1L);
        assertThat(data.get("defectHighRisk")).isEqualTo(1L);
        assertThat(data.get("processOverdue")).isEqualTo(1L);
        assertThat(data.get("total")).isEqualTo(5L);
    }

    @Test
    void pageFiltersByTypeAndPaginates() {
        when(taskMapper.selectList(any())).thenReturn(List.of(
                task(1L, 0, LocalDate.now().minusDays(2)),
                task(2L, 1, LocalDate.now().minusDays(4))));

        Map<String, Object> data = service.page("task_overdue", 1, 1);

        assertThat(data.get("total")).isEqualTo(2L);
        assertThat(data.get("pageNum")).isEqualTo(1L);
        assertThat(data.get("pageSize")).isEqualTo(1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("type")).isEqualTo(AlertService.TYPE_TASK_OVERDUE);
        assertThat(list.get(0).get("level")).isEqualTo("high");
        assertThat(list.get(0).get("overdueDays")).isEqualTo(4L);
    }

    @Test
    void policyExpiringUsesDaysLeftAndLevel() {
        IcpPolicy policy = new IcpPolicy();
        policy.setId(100L);
        policy.setPolicyName("制度A");
        policy.setStatus(2);
        policy.setExpireDate(LocalDate.now().plusDays(5));
        when(policyMapper.selectList(any())).thenReturn(List.of(policy));

        Map<String, Object> data = service.page("policy_expiring", 1, 10);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("level")).isEqualTo("high");
        assertThat(list.get(0).get("daysLeft")).isEqualTo(5L);
    }

    @Test
    void highRiskDefectAppearsWithHighLevel() {
        IcpDefect defect = new IcpDefect();
        defect.setId(200L);
        defect.setDefectName("重大缺陷");
        defect.setSeverity(1);
        defect.setStatus(1);
        defect.setFindDate(LocalDate.now().minusDays(10));
        when(defectMapper.selectList(any())).thenReturn(List.of(defect));

        Map<String, Object> data = service.page("defect_high_risk", 1, 10);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("level")).isEqualTo("high");
        assertThat(list.get(0).get("overdueDays")).isEqualTo(10L);
    }
}

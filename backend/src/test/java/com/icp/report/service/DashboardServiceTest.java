package com.icp.report.service;

import com.icp.collab.entity.CollTask;
import com.icp.collab.mapper.CollTaskMapper;
import com.icp.collab.service.MessageService;
import com.icp.common.api.PageResult;
import com.icp.common.cache.CacheService;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.icp.entity.IcpDefect;
import com.icp.icp.entity.IcpMatrix;
import com.icp.icp.entity.IcpPolicy;
import com.icp.icp.entity.IcpRectify;
import com.icp.icp.entity.IcpRisk;
import com.icp.icp.mapper.IcpDefectMapper;
import com.icp.icp.mapper.IcpMatrixMapper;
import com.icp.icp.mapper.IcpPolicyMapper;
import com.icp.icp.mapper.IcpRectifyMapper;
import com.icp.icp.mapper.IcpRiskMapper;
import com.icp.report.support.ReportScope;
import com.icp.system.service.ConfigService;
import com.icp.workflow.mapper.WfBusinessLinkMapper;
import com.icp.workflow.service.WorkflowTaskService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @BeforeAll
    static void initMybatisMetadata() {
        com.icp.testsupport.MybatisTestSupport.init();
    }

    @Mock
    private ReportScope scope;
    @Mock
    private CacheService cacheService;
    @Mock
    private ConfigService configService;
    @Mock
    private WorkflowTaskService workflowTaskService;
    @Mock
    private MessageService messageService;
    @Mock
    private IcpPolicyMapper policyMapper;
    @Mock
    private IcpRiskMapper riskMapper;
    @Mock
    private IcpMatrixMapper matrixMapper;
    @Mock
    private IcpDefectMapper defectMapper;
    @Mock
    private IcpRectifyMapper rectifyMapper;
    @Mock
    private CollTaskMapper taskMapper;
    @Mock
    private WfBusinessLinkMapper linkMapper;

    private DashboardService service;
    private final DataScopeInfo info = new DataScopeInfo(1L, 2L, DataScopeInfo.ALL, List.of());

    @BeforeEach
    void setUp() {
        service = new DashboardService(scope, cacheService, configService, workflowTaskService, messageService,
                policyMapper, riskMapper, matrixMapper, defectMapper, rectifyMapper, taskMapper, linkMapper);
    }

    @Test
    void summaryReturnsCachedValueWhenPresent() {
        Map<String, Object> cached = Map.of("policies", Map.of("total", 40));
        when(scope.current()).thenReturn(info);
        when(scope.cacheKey(info)).thenReturn("1");
        when(cacheService.get("report:summary:1")).thenReturn(Optional.of(cached));

        Map<String, Object> data = service.summary();

        assertThat(data).isSameAs(cached);
        verify(policyMapper, never()).selectList(any());
        verify(cacheService, never()).put(anyString(), any(), anyLong());
    }

    @Test
    void summaryComputesAndCachesFor60Seconds() {
        when(scope.current()).thenReturn(info);
        when(scope.cacheKey(info)).thenReturn("1");
        when(cacheService.get("report:summary:1")).thenReturn(Optional.empty());
        lenient().when(scope.visibleByDeptUser(any(), any(), any())).thenReturn(true);
        lenient().when(scope.visibleTask(any(), any(), any(), any())).thenReturn(true);
        lenient().when(scope.visibleLink(any(), any())).thenReturn(true);
        when(policyMapper.selectList(any())).thenReturn(List.of(policy(1L, 2), policy(2L, 0)));
        when(riskMapper.selectList(any())).thenReturn(List.of(risk(1L, 1), risk(2L, 3)));
        IcpMatrix matrix = new IcpMatrix();
        matrix.setId(9L);
        matrix.setRiskId(1L);
        matrix.setStatus(1);
        when(matrixMapper.selectList(any())).thenReturn(List.of(matrix));
        IcpDefect closed = new IcpDefect();
        closed.setId(1L);
        closed.setStatus(3);
        IcpDefect open = new IcpDefect();
        open.setId(2L);
        open.setStatus(1);
        when(defectMapper.selectList(any())).thenReturn(List.of(closed, open));
        IcpRectify approved = new IcpRectify();
        approved.setId(1L);
        approved.setStatus(2);
        when(rectifyMapper.selectList(any())).thenReturn(List.of(approved));
        CollTask overdueTask = new CollTask();
        overdueTask.setId(1L);
        overdueTask.setStatus(0);
        overdueTask.setPlanEnd(java.time.LocalDate.now().minusDays(1));
        when(taskMapper.selectList(any())).thenReturn(List.of(overdueTask));
        when(workflowTaskService.todo(1, 1, null, null)).thenReturn(new PageResult<>(List.of(), 3L, 1, 1));
        when(configService.getInt(anyString(), anyInt())).thenReturn(48);
        when(linkMapper.selectList(any())).thenReturn(List.of());
        when(messageService.unreadCount()).thenReturn(Map.of("unread", 2L));

        Map<String, Object> data = service.summary();

        assertThat(data.get("policies")).isEqualTo(Map.of("total", 2L, "effective", 1L));
        assertThat(data.get("risks")).isEqualTo(Map.of("total", 2L, "high", 1L));
        assertThat(data.get("coverage")).isEqualTo(Map.of("totalRisks", 2L, "coveredRisks", 1L, "coverageRate", 50.0D));
        assertThat(data.get("defects")).isEqualTo(Map.of("total", 2L, "unclosed", 1L));
        assertThat(data.get("rectify")).isEqualTo(Map.of("total", 1L, "approved", 1L, "rate", 100.0D));
        assertThat(data.get("workflow")).isEqualTo(Map.of("todo", 3L, "overdue", 0L));
        assertThat(data.get("tasks")).isEqualTo(Map.of("overdue", 1L));
        assertThat(data.get("messages")).isEqualTo(Map.of("unread", 2L));
        verify(cacheService).put(eq("report:summary:1"), any(), eq(60L));
    }

    @Test
    void trendRejectsMonthsOutOfRange() {
        assertThatThrownBy(() -> service.trend(13))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("months");
    }

    @Test
    void trendReturnsEqualLengthArrays() {
        when(scope.current()).thenReturn(info);
        when(policyMapper.selectList(any())).thenReturn(List.of());
        when(defectMapper.selectList(any())).thenReturn(List.of());
        when(rectifyMapper.selectList(any())).thenReturn(List.of());
        when(linkMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> data = service.trend(6);

        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) data.get("labels");
        @SuppressWarnings("unchecked")
        List<Long> newPolicies = (List<Long>) data.get("newPolicies");
        @SuppressWarnings("unchecked")
        List<Long> newDefects = (List<Long>) data.get("newDefects");
        @SuppressWarnings("unchecked")
        List<Long> rectifyClosed = (List<Long>) data.get("rectifyClosed");
        @SuppressWarnings("unchecked")
        List<Double> avgProcessHours = (List<Double>) data.get("avgProcessHours");
        assertThat(labels).hasSize(6);
        assertThat(newPolicies).hasSize(6);
        assertThat(newDefects).hasSize(6);
        assertThat(rectifyClosed).hasSize(6);
        assertThat(avgProcessHours).hasSize(6);
    }

    private IcpPolicy policy(long id, int status) {
        IcpPolicy policy = new IcpPolicy();
        policy.setId(id);
        policy.setPolicyName("制度" + id);
        policy.setStatus(status);
        return policy;
    }

    private IcpRisk risk(long id, int level) {
        IcpRisk risk = new IcpRisk();
        risk.setId(id);
        risk.setRiskName("风险" + id);
        risk.setRiskLevel(level);
        return risk;
    }
}

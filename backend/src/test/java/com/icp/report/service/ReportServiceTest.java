package com.icp.report.service;

import com.icp.collab.entity.CollTask;
import com.icp.collab.mapper.CollTaskMapper;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.icp.entity.IcpDefect;
import com.icp.icp.entity.IcpMatrix;
import com.icp.icp.entity.IcpRectify;
import com.icp.icp.entity.IcpRisk;
import com.icp.icp.mapper.IcpDefectMapper;
import com.icp.icp.mapper.IcpMatrixMapper;
import com.icp.icp.mapper.IcpRectifyMapper;
import com.icp.icp.mapper.IcpRiskMapper;
import com.icp.icp.support.IcpLookupService;
import com.icp.report.support.ReportScope;
import com.icp.workflow.entity.WfBusinessLink;
import com.icp.workflow.mapper.WfBusinessLinkMapper;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @BeforeAll
    static void initMybatisMetadata() {
        com.icp.testsupport.MybatisTestSupport.init();
    }

    @Mock
    private ReportScope scope;
    @Mock
    private IcpLookupService lookupService;
    @Mock
    private IcpRiskMapper riskMapper;
    @Mock
    private IcpMatrixMapper matrixMapper;
    @Mock
    private IcpRectifyMapper rectifyMapper;
    @Mock
    private IcpDefectMapper defectMapper;
    @Mock
    private WfBusinessLinkMapper linkMapper;
    @Mock
    private CollTaskMapper taskMapper;

    private ReportService service;
    private final DataScopeInfo info = new DataScopeInfo(1L, 2L, DataScopeInfo.ALL, List.of());

    @BeforeEach
    void setUp() {
        service = new ReportService(scope, lookupService, riskMapper, matrixMapper, rectifyMapper,
                defectMapper, linkMapper, taskMapper);
        org.mockito.Mockito.lenient().when(scope.current()).thenReturn(info);
        org.mockito.Mockito.lenient().when(scope.effectiveDeptFilter(any(), any())).thenReturn(null);
    }

    private IcpRisk risk(long id, int level, Long deptId, Long categoryId) {
        IcpRisk risk = new IcpRisk();
        risk.setId(id);
        risk.setRiskName("风险" + id);
        risk.setRiskCode("FX-2026-000" + id);
        risk.setRiskLevel(level);
        risk.setOwnerDeptId(deptId);
        risk.setCategoryId(categoryId);
        return risk;
    }

    @Test
    void invalidDimRejected() {
        assertThatThrownBy(() -> service.riskDistribution("foo"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dim");
    }

    @Test
    void riskDistributionByLevelCountsThreeBuckets() {
        when(scope.visibleByDeptUser(any(), any(), any())).thenReturn(true);
        when(riskMapper.selectList(any())).thenReturn(List.of(
                risk(1L, 1, 2L, 10L), risk(2L, 2, 2L, 10L), risk(3L, 2, 3L, 11L), risk(4L, 3, 4L, 11L)));

        Map<String, Object> data = service.riskDistribution("level");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("value")).isEqualTo(1L);
        assertThat(items.get(1).get("value")).isEqualTo(2L);
        assertThat(items.get(2).get("value")).isEqualTo(1L);
        assertThat(data.get("total")).isEqualTo(4);
    }

    @Test
    void icpCoverageComputesCoveredRisksAndRates() {
        when(scope.visibleByDeptUser(any(), any(), any())).thenReturn(true);
        when(riskMapper.selectList(any())).thenReturn(List.of(risk(1L, 1, 4L, 10L), risk(2L, 2, 4L, 11L)));
        IcpMatrix matrix = new IcpMatrix();
        matrix.setId(1L);
        matrix.setRiskId(1L);
        matrix.setStatus(1);
        when(matrixMapper.selectList(any())).thenReturn(List.of(matrix));
        when(lookupService.deptNames(any())).thenReturn(Map.of());
        when(lookupService.riskCategoryNames(any())).thenReturn(Map.of());

        Map<String, Object> data = service.icpCoverage(null, null);

        assertThat(data.get("totalRisks")).isEqualTo(2);
        assertThat(data.get("coveredRisks")).isEqualTo(1);
        assertThat(data.get("coverageRate")).isEqualTo(50.0D);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byDept = (List<Map<String, Object>>) data.get("byDept");
        assertThat(byDept).hasSize(1);
        assertThat(byDept.get(0).get("rate")).isEqualTo(50.0D);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byCategory = (List<Map<String, Object>>) data.get("byCategory");
        assertThat(byCategory).hasSize(2);
    }

    @Test
    void rectifyRateUsesApprovedOverTotal() {
        when(scope.visibleByDeptUser(any(), any(), any())).thenReturn(true);
        IcpRectify approved = new IcpRectify();
        approved.setId(1L);
        approved.setDeptId(4L);
        approved.setStatus(2);
        approved.setCreateTime(java.time.LocalDateTime.now());
        IcpRectify pending = new IcpRectify();
        pending.setId(2L);
        pending.setDeptId(4L);
        pending.setStatus(0);
        pending.setCreateTime(java.time.LocalDateTime.now());
        when(rectifyMapper.selectList(any())).thenReturn(List.of(approved, pending));
        when(lookupService.deptNames(any())).thenReturn(Map.of());

        Map<String, Object> data = service.rectifyRate(null, null);

        assertThat(data.get("total")).isEqualTo(2);
        assertThat(data.get("approved")).isEqualTo(1L);
        assertThat(data.get("rate")).isEqualTo(50.0D);
    }

    @Test
    void taskStatisticsGroupsStatusesAndOverdue() {
        when(scope.visibleTask(any(), any(), any(), any())).thenReturn(true);
        CollTask t0 = new CollTask();
        t0.setId(1L);
        t0.setStatus(0);
        t0.setPlanEnd(LocalDate.now().minusDays(2));
        CollTask t1 = new CollTask();
        t1.setId(2L);
        t1.setStatus(1);
        t1.setPlanEnd(LocalDate.now().plusDays(3));
        CollTask t2 = new CollTask();
        t2.setId(3L);
        t2.setStatus(2);
        CollTask t3 = new CollTask();
        t3.setId(4L);
        t3.setStatus(3);
        when(taskMapper.selectList(any())).thenReturn(List.of(t0, t1, t2, t3));

        Map<String, Object> data = service.taskStatistics();

        assertThat(data.get("total")).isEqualTo(4L);
        assertThat(data.get("overdue")).isEqualTo(1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertThat(items).hasSize(4);
        assertThat(items.get(0).get("count")).isEqualTo(1L);
    }

    @Test
    void defectsGroupsSeverityAndStatusPairs() {
        when(scope.visibleByDeptUser(any(), any(), any())).thenReturn(true);
        IcpDefect first = new IcpDefect();
        first.setId(1L);
        first.setSeverity(1);
        first.setStatus(0);
        IcpDefect second = new IcpDefect();
        second.setId(2L);
        second.setSeverity(1);
        second.setStatus(0);
        IcpDefect third = new IcpDefect();
        third.setId(3L);
        third.setSeverity(2);
        third.setStatus(1);
        when(defectMapper.selectList(any())).thenReturn(List.of(first, second, third));

        Map<String, Object> data = service.defects(null, null);

        assertThat(data.get("total")).isEqualTo(3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("count")).isEqualTo(2L);
    }

    @Test
    void processDurationOnlyCountsCompletedLinks() {
        WfBusinessLink done = new WfBusinessLink();
        done.setId(1L);
        done.setProcessKey("doc_borrow");
        done.setProcessInstanceId("pi-1");
        done.setStatus(1);
        done.setStartTime(java.time.LocalDateTime.now().minusHours(4));
        done.setEndTime(java.time.LocalDateTime.now());
        when(scope.visibleLink(any(), any())).thenReturn(true);
        when(linkMapper.selectList(any())).thenReturn(List.of(done));

        Map<String, Object> data = service.processDuration(null, 6);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        assertThat(items).hasSize(6);
        Map<String, Object> borrow = items.stream()
                .filter(row -> "doc_borrow".equals(row.get("processKey"))).findFirst().orElseThrow();
        assertThat(borrow.get("total")).isEqualTo(1);
        assertThat((Double) borrow.get("avgHours")).isEqualTo(4.0D);
        assertThat((Double) borrow.get("p95Hours")).isEqualTo(4.0D);
    }
}

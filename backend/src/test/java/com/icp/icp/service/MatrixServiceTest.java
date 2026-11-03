package com.icp.icp.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import com.icp.icp.entity.IcpControl;
import com.icp.icp.entity.IcpMatrix;
import com.icp.icp.entity.IcpRisk;
import com.icp.icp.mapper.IcpControlMapper;
import com.icp.icp.mapper.IcpMatrixMapper;
import com.icp.icp.mapper.IcpPolicyMapper;
import com.icp.icp.mapper.IcpRiskMapper;
import com.icp.icp.support.IcpLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 风险矩阵单测：风险+控制点唯一、覆盖率口径（已配置至少一条矩阵的风险 / 启用风险总数）。
 */
@ExtendWith(MockitoExtension.class)
class MatrixServiceTest {

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisMetadata() {
        com.icp.testsupport.MybatisTestSupport.init();
    }

    @Mock
    private IcpMatrixMapper matrixMapper;
    @Mock
    private IcpRiskMapper riskMapper;
    @Mock
    private IcpControlMapper controlMapper;
    @Mock
    private IcpPolicyMapper policyMapper;
    @Mock
    private IcpLookupService lookupService;

    private MatrixService matrixService;

    @BeforeEach
    void setUp() {
        matrixService = new MatrixService(matrixMapper, riskMapper, controlMapper, policyMapper, lookupService);
    }

    @Test
    void duplicateRiskControlPairRejected() {
        IcpMatrix request = matrix(1301L, 1401L);
        when(riskMapper.selectById(1301L)).thenReturn(new IcpRisk());
        when(controlMapper.selectById(1401L)).thenReturn(new IcpControl());
        when(matrixMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> matrixService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("矩阵关系已存在")
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void createStoresGeneratedMatrixCode() {
        IcpMatrix request = matrix(1301L, 1401L);
        when(riskMapper.selectById(1301L)).thenReturn(new IcpRisk());
        when(controlMapper.selectById(1401L)).thenReturn(new IcpControl());
        when(matrixMapper.selectCount(any())).thenReturn(0L);
        when(matrixMapper.selectMaxCode()).thenReturn("JC-2026-060");
        when(matrixMapper.insert(any(IcpMatrix.class))).thenReturn(1);

        IcpMatrix created = matrixService.create(request);

        assertThat(created.getMatrixCode()).isEqualTo("JC-2026-061");
        assertThat(created.getStatus()).isEqualTo(1);
    }

    @Test
    void coverageRateComputesCoveredOverTotal() {
        IcpRisk risk1 = risk(1301L);
        IcpRisk risk2 = risk(1302L);
        IcpRisk risk3 = risk(1303L);
        IcpMatrix matrix = new IcpMatrix();
        matrix.setId(1501L);
        matrix.setRiskId(1301L);
        matrix.setStatus(1);
        when(riskMapper.selectList(any())).thenReturn(List.of(risk1, risk2, risk3));
        when(matrixMapper.selectList(any())).thenReturn(List.of(matrix));
        lenient().when(lookupService.riskCategoryNames(any())).thenReturn(Map.of());
        lenient().when(lookupService.deptNames(any())).thenReturn(Map.of());

        Map<String, Object> result = matrixService.coverage();

        assertThat(result.get("totalRisks")).isEqualTo(3);
        assertThat(result.get("coveredRisks")).isEqualTo(1);
        assertThat(result.get("uncoveredRisks")).isEqualTo(2);
        assertThat(result.get("coverageRate")).isEqualTo(33.33);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("covered")).isEqualTo(true);
        assertThat(items.get(0).get("controlCount")).isEqualTo(1);
        assertThat(items.get(1).get("covered")).isEqualTo(false);
    }

    @Test
    void coverageWithNoRisksIsZero() {
        when(riskMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(matrixMapper.selectList(any())).thenReturn(new ArrayList<>());

        Map<String, Object> result = matrixService.coverage();

        assertThat(result.get("totalRisks")).isEqualTo(0);
        assertThat(result.get("coverageRate")).isEqualTo(0D);
    }

    private IcpMatrix matrix(Long riskId, Long controlId) {
        IcpMatrix request = new IcpMatrix();
        request.setRiskId(riskId);
        request.setControlId(controlId);
        request.setCoverage(1);
        return request;
    }

    private IcpRisk risk(Long id) {
        IcpRisk risk = new IcpRisk();
        risk.setId(id);
        risk.setRiskCode("FX-2026-00" + (id - 1300));
        risk.setRiskName("风险" + id);
        risk.setRiskLevel(1);
        risk.setStatus(1);
        risk.setOwnerDeptId(2L);
        return risk;
    }
}

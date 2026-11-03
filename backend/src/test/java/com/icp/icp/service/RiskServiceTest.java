package com.icp.icp.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.icp.entity.IcpRisk;
import com.icp.icp.entity.IcpRiskCategory;
import com.icp.icp.mapper.IcpRiskCategoryMapper;
import com.icp.icp.mapper.IcpRiskMapper;
import com.icp.icp.support.IcpLookupService;
import com.icp.system.service.ConfigService;
import com.icp.system.service.DataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 风险库单测：评分 = 可能性 × 影响，等级阈值边界（<8 低 / 8-14 中 / >=15 高）。
 */
@ExtendWith(MockitoExtension.class)
class RiskServiceTest {

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisMetadata() {
        com.icp.testsupport.MybatisTestSupport.init();
    }

    @Mock
    private IcpRiskMapper riskMapper;
    @Mock
    private IcpRiskCategoryMapper categoryMapper;
    @Mock
    private IcpLookupService lookupService;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private ConfigService configService;

    private RiskService riskService;

    @BeforeEach
    void setUp() {
        riskService = new RiskService(riskMapper, categoryMapper, lookupService, dataScopeService, configService);
        lenient().when(dataScopeService.current())
                .thenReturn(new DataScopeInfo(1L, 2L, DataScopeInfo.ALL, Collections.emptyList()));
        lenient().when(configService.getInt(eq("icp.risk.highScore"), any(Integer.class))).thenReturn(15);
        lenient().when(configService.getInt(eq("icp.risk.midScore"), any(Integer.class))).thenReturn(8);
    }

    @Test
    void scoreAndLevelFollowThresholds() {
        assertScore(2, 3, 6, 3);
        assertScore(2, 4, 8, 2);
        assertScore(3, 4, 12, 2);
        assertScore(3, 5, 15, 1);
        assertScore(5, 5, 25, 1);
    }

    @Test
    void probabilityOutOfRangeRejected() {
        IcpRisk request = new IcpRisk();
        request.setRiskName("越界风险");
        request.setProbability(6);
        request.setImpact(2);

        assertThatThrownBy(() -> riskService.create(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void probabilityMissingRejected() {
        IcpRisk request = new IcpRisk();
        request.setRiskName("缺参数风险");
        request.setImpact(3);

        assertThatThrownBy(() -> riskService.create(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    void deleteBlockedWhenReferencedByMatrix() {
        when(riskMapper.selectById(1301L)).thenReturn(risk(1301L, 1));
        when(riskMapper.countMatrixRefs(1301L)).thenReturn(2L);

        assertThatThrownBy(() -> riskService.delete(1301L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("风险矩阵");
    }

    @Test
    void changeStatusOnlyAllowsZeroOrOne() {
        when(riskMapper.selectById(1301L)).thenReturn(risk(1301L, 1));

        assertThatThrownBy(() -> riskService.changeStatus(1301L, 2))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));

        IcpRisk updated = riskService.changeStatus(1301L, 0);
        assertThat(updated.getStatus()).isEqualTo(0);
        verify(riskMapper).updateById(any(IcpRisk.class));
    }

    private void assertScore(int probability, int impact, int expectedScore, int expectedLevel) {
        IcpRisk request = new IcpRisk();
        request.setRiskName("风险-" + probability + "x" + impact);
        request.setProbability(probability);
        request.setImpact(impact);
        when(riskMapper.selectMaxCode()).thenReturn(null);
        when(riskMapper.insert(any(IcpRisk.class))).thenReturn(1);

        IcpRisk created = riskService.create(request);

        assertThat(created.getRiskScore()).as("score for %sx%s", probability, impact).isEqualTo(expectedScore);
        assertThat(created.getRiskLevel()).as("level for score %s", expectedScore).isEqualTo(expectedLevel);
        assertThat(created.getRiskCode()).startsWith("FX-");
    }

    private IcpRisk risk(Long id, int status) {
        IcpRisk risk = new IcpRisk();
        risk.setId(id);
        risk.setRiskCode("FX-2026-001");
        risk.setRiskName("种子风险");
        risk.setOwnerDeptId(2L);
        risk.setOwnerUserId(3L);
        risk.setStatus(status);
        return risk;
    }
}

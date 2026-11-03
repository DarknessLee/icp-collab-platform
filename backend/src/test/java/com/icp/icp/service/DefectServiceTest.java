package com.icp.icp.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.icp.dto.IcpRequests;
import com.icp.icp.entity.IcpDefect;
import com.icp.icp.entity.IcpRectify;
import com.icp.icp.mapper.IcpDefectMapper;
import com.icp.icp.mapper.IcpRectifyMapper;
import com.icp.icp.support.IcpLookupService;
import com.icp.system.service.DataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 缺陷单测：整改发起生成 ZG 记录并推进缺陷状态、关闭缺陷须有待复核状态与整改通过记录。
 */
@ExtendWith(MockitoExtension.class)
class DefectServiceTest {

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisMetadata() {
        com.icp.testsupport.MybatisTestSupport.init();
    }

    @Mock
    private IcpDefectMapper defectMapper;
    @Mock
    private IcpRectifyMapper rectifyMapper;
    @Mock
    private IcpLookupService lookupService;
    @Mock
    private DataScopeService dataScopeService;

    private DefectService defectService;

    @BeforeEach
    void setUp() {
        defectService = new DefectService(defectMapper, rectifyMapper, lookupService, dataScopeService);
        lenient().when(dataScopeService.current())
                .thenReturn(new DataScopeInfo(1L, 2L, DataScopeInfo.ALL, Collections.emptyList()));
    }

    @Test
    void rectifyGeneratesRectifyAndAdvancesDefect() {
        when(defectMapper.selectById(1701L)).thenReturn(defect(1701L, 0));
        when(rectifyMapper.countOpenByDefect(1701L)).thenReturn(0L);
        when(rectifyMapper.selectMaxCode()).thenReturn("ZG-2026-025");
        when(rectifyMapper.insert(any(IcpRectify.class))).thenReturn(1);

        Map<String, Object> result = defectService.rectify(1701L,
                new IcpRequests.DefectRectify("补充双人复核", 3L, LocalDate.of(2026, 12, 31)));

        assertThat(result.get("defectStatus")).isEqualTo(1);
        assertThat(result.get("rectifyCode")).isEqualTo("ZG-2026-026");
        verify(rectifyMapper).insert(any(IcpRectify.class));
        verify(defectMapper).updateById(any(IcpDefect.class));
    }

    @Test
    void rectifyRejectedWhenOpenRectifyExists() {
        when(defectMapper.selectById(1701L)).thenReturn(defect(1701L, 1));
        when(rectifyMapper.countOpenByDefect(1701L)).thenReturn(1L);

        assertThatThrownBy(() -> defectService.rectify(1701L,
                new IcpRequests.DefectRectify("重复整改", 3L, LocalDate.of(2026, 12, 31))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未完成的整改记录");
    }

    @Test
    void rectifyRejectedWhenDefectClosed() {
        when(defectMapper.selectById(1702L)).thenReturn(defect(1702L, 3));

        assertThatThrownBy(() -> defectService.rectify(1702L,
                new IcpRequests.DefectRectify("已关闭", 3L, LocalDate.of(2026, 12, 31))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void rectifyRequiresPlanAndDeadline() {
        when(defectMapper.selectById(1701L)).thenReturn(defect(1701L, 0));
        when(rectifyMapper.countOpenByDefect(1701L)).thenReturn(0L);

        assertThatThrownBy(() -> defectService.rectify(1701L,
                new IcpRequests.DefectRectify(" ", 3L, LocalDate.of(2026, 12, 31))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("整改方案");

        assertThatThrownBy(() -> defectService.rectify(1701L,
                new IcpRequests.DefectRectify("方案", 3L, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("整改期限");
    }

    @Test
    void closeRequiresReviewingStatus() {
        when(defectMapper.selectById(1701L)).thenReturn(defect(1701L, 1));

        assertThatThrownBy(() -> defectService.close(1701L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void closeRequiresApprovedRectify() {
        when(defectMapper.selectById(1703L)).thenReturn(defect(1703L, 2));
        when(rectifyMapper.countApprovedByDefect(1703L)).thenReturn(0L);

        assertThatThrownBy(() -> defectService.close(1703L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("整改通过");
    }

    @Test
    void closeSuccessSetsCloseTime() {
        when(defectMapper.selectById(1703L)).thenReturn(defect(1703L, 2));
        when(rectifyMapper.countApprovedByDefect(1703L)).thenReturn(1L);

        IcpDefect closed = defectService.close(1703L);

        assertThat(closed.getStatus()).isEqualTo(3);
        assertThat(closed.getCloseTime()).isNotNull();
        verify(defectMapper).updateById(any(IcpDefect.class));
    }

    private IcpDefect defect(Long id, int status) {
        IcpDefect defect = new IcpDefect();
        defect.setId(id);
        defect.setDefectCode("QX-2026-001");
        defect.setDefectName("种子缺陷");
        defect.setSeverity(2);
        defect.setDeptId(2L);
        defect.setOwnerUserId(3L);
        defect.setStatus(status);
        return defect;
    }
}

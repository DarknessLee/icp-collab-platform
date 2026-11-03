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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 整改单测：状态机 0->1->2->4（驳回 1->3），提交需证据、通过推进缺陷到待复核、关闭仅限已通过。
 */
@ExtendWith(MockitoExtension.class)
class RectifyServiceTest {

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisMetadata() {
        com.icp.testsupport.MybatisTestSupport.init();
    }

    @Mock
    private IcpRectifyMapper rectifyMapper;
    @Mock
    private IcpDefectMapper defectMapper;
    @Mock
    private IcpLookupService lookupService;
    @Mock
    private DataScopeService dataScopeService;

    private RectifyService rectifyService;

    @BeforeEach
    void setUp() {
        rectifyService = new RectifyService(rectifyMapper, defectMapper, lookupService, dataScopeService);
        lenient().when(dataScopeService.current())
                .thenReturn(new DataScopeInfo(1L, 2L, DataScopeInfo.ALL, Collections.emptyList()));
    }

    @Test
    void submitRequiresEvidence() {
        when(rectifyMapper.selectById(1751L)).thenReturn(rectify(1751L, 0, null));

        assertThatThrownBy(() -> rectifyService.submit(1751L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("整改证据");
    }

    @Test
    void submitMovesToReviewingAndSetsFinishDate() {
        when(rectifyMapper.selectById(1751L)).thenReturn(rectify(1751L, 0, null));

        IcpRectify submitted = rectifyService.submit(1751L, new IcpRequests.RectifySubmit("4001,4002"));

        assertThat(submitted.getStatus()).isEqualTo(1);
        assertThat(submitted.getFinishDate()).isNotNull();
        assertThat(submitted.getEvidence()).isEqualTo("4001,4002");
        verify(rectifyMapper).updateById(any(IcpRectify.class));
    }

    @Test
    void submitFromApprovedRejected() {
        when(rectifyMapper.selectById(1752L)).thenReturn(rectify(1752L, 2, "4001"));

        assertThatThrownBy(() -> rectifyService.submit(1752L, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void approveMovesToApprovedAndAdvancesDefect() {
        when(rectifyMapper.selectById(1753L)).thenReturn(rectify(1753L, 1, "4001"));
        IcpDefect defect = new IcpDefect();
        defect.setId(1701L);
        defect.setStatus(1);
        when(defectMapper.selectById(1701L)).thenReturn(defect);

        IcpRectify approved = rectifyService.approve(1753L, new IcpRequests.RectifyReview("同意整改"));

        assertThat(approved.getStatus()).isEqualTo(2);
        assertThat(approved.getReviewTime()).isNotNull();
        assertThat(approved.getReviewOpinion()).isEqualTo("同意整改");
        assertThat(defect.getStatus()).isEqualTo(2);
        verify(defectMapper).updateById(any(IcpDefect.class));
    }

    @Test
    void rejectRequiresOpinion() {
        when(rectifyMapper.selectById(1754L)).thenReturn(rectify(1754L, 1, "4001"));

        assertThatThrownBy(() -> rectifyService.reject(1754L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("复核意见");
    }

    @Test
    void rejectMovesToRejected() {
        when(rectifyMapper.selectById(1754L)).thenReturn(rectify(1754L, 1, "4001"));

        IcpRectify rejected = rectifyService.reject(1754L, new IcpRequests.RectifyReview("证据不足"));

        assertThat(rejected.getStatus()).isEqualTo(3);
        assertThat(rejected.getReviewOpinion()).isEqualTo("证据不足");
    }

    @Test
    void closeRequiresApproved() {
        when(rectifyMapper.selectById(1755L)).thenReturn(rectify(1755L, 1, "4001"));

        assertThatThrownBy(() -> rectifyService.close(1755L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void closeApprovedMovesToClosed() {
        when(rectifyMapper.selectById(1756L)).thenReturn(rectify(1756L, 2, "4001"));

        IcpRectify closed = rectifyService.close(1756L);

        assertThat(closed.getStatus()).isEqualTo(4);
    }

    private IcpRectify rectify(Long id, int status, String evidence) {
        IcpRectify rectify = new IcpRectify();
        rectify.setId(id);
        rectify.setRectifyCode("ZG-2026-001");
        rectify.setDefectId(1701L);
        rectify.setRectifyPlan("补充复核环节");
        rectify.setDeadline(LocalDate.of(2026, 12, 31));
        rectify.setResponsibleUserId(3L);
        rectify.setDeptId(2L);
        rectify.setEvidence(evidence);
        rectify.setStatus(status);
        return rectify;
    }
}

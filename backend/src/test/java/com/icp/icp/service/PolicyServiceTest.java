package com.icp.icp.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.icp.dto.IcpRequests;
import com.icp.icp.entity.IcpPolicy;
import com.icp.icp.mapper.IcpPolicyCategoryMapper;
import com.icp.icp.mapper.IcpPolicyMapper;
import com.icp.icp.support.IcpLookupService;
import com.icp.system.service.DataScopeService;
import com.icp.workflow.dto.WorkflowRequests;
import com.icp.workflow.service.WorkflowInstanceService;
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
import static org.mockito.Mockito.when;

/**
 * 制度单测：状态机 0->1->2->3，非法流转返回 40001，送审/发布暂不接 Flowable（pendingWorkflow=true）。
 */
@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisMetadata() {
        com.icp.testsupport.MybatisTestSupport.init();
    }

    @Mock
    private IcpPolicyMapper policyMapper;
    @Mock
    private IcpPolicyCategoryMapper categoryMapper;
    @Mock
    private IcpLookupService lookupService;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private WorkflowInstanceService workflowInstanceService;

    private PolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = new PolicyService(policyMapper, categoryMapper, lookupService, dataScopeService,
                workflowInstanceService);
        lenient().when(dataScopeService.current())
                .thenReturn(new DataScopeInfo(1L, 2L, DataScopeInfo.ALL, Collections.emptyList()));
        lenient().when(workflowInstanceService.start(any(WorkflowRequests.StartRequest.class), any()))
                .thenReturn(Map.of("processInstanceId", "pi-policy-1", "taskId", "task-policy-1",
                        "businessNo", "ZD-2026-0001"));
    }

    @Test
    void submitDraftStartsWorkflowAndReturnsInstance() {
        when(policyMapper.selectById(1101L)).thenReturn(policy(1101L, 0), policy(1101L, 1));

        Map<String, Object> result = policyService.submit(1101L);

        assertThat(result.get("status")).isEqualTo(1);
        assertThat(result.get("pendingWorkflow")).isEqualTo(false);
        assertThat(result.get("processInstanceId")).isEqualTo("pi-policy-1");
        assertThat(result.get("taskId")).isEqualTo("task-policy-1");
        org.mockito.Mockito.verify(workflowInstanceService)
                .start(any(WorkflowRequests.StartRequest.class), any());
    }

    @Test
    void submitNonDraftRejected() {
        when(policyMapper.selectById(1101L)).thenReturn(policy(1101L, 2));

        assertThatThrownBy(() -> policyService.submit(1101L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void publishApprovingSetsEffectiveDateAndPublishTime() {
        when(policyMapper.selectById(1102L)).thenReturn(policy(1102L, 1));
        IcpRequests.Publish request = new IcpRequests.Publish(LocalDate.of(2026, 11, 1), null);

        Map<String, Object> result = policyService.publish(1102L, request);

        assertThat(result.get("status")).isEqualTo(2);
        assertThat(result.get("publishTime")).isNotNull();
        assertThat(result.get("effectiveDate")).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(result.get("pendingWorkflow")).isEqualTo(true);
    }

    @Test
    void publishDraftRejected() {
        when(policyMapper.selectById(1101L)).thenReturn(policy(1101L, 0));

        assertThatThrownBy(() -> policyService.publish(1101L, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void abolishEffectiveMovesToAbolished() {
        when(policyMapper.selectById(1103L)).thenReturn(policy(1103L, 2));

        Map<String, Object> result = policyService.abolish(1103L);

        assertThat(result.get("status")).isEqualTo(3);
    }

    @Test
    void abolishDraftRejected() {
        when(policyMapper.selectById(1101L)).thenReturn(policy(1101L, 0));

        assertThatThrownBy(() -> policyService.abolish(1101L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void deleteDraftAllowedButEffectiveRejected() {
        when(policyMapper.selectById(1101L)).thenReturn(policy(1101L, 0));
        policyService.delete(1101L);

        when(policyMapper.selectById(1103L)).thenReturn(policy(1103L, 2));
        assertThatThrownBy(() -> policyService.delete(1103L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateStatusDirectlyRejected() {
        when(policyMapper.selectById(1101L)).thenReturn(policy(1101L, 0));
        IcpPolicy request = new IcpPolicy();
        request.setStatus(2);

        assertThatThrownBy(() -> policyService.update(1101L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.STATE_INVALID));
    }

    @Test
    void createDraftGeneratesPolicyCode() {
        when(policyMapper.selectMaxCode()).thenReturn("ZD-2026-0040");
        when(policyMapper.insert(any(IcpPolicy.class))).thenReturn(1);
        IcpPolicy request = new IcpPolicy();
        request.setPolicyName("测试制度");

        IcpPolicy created = policyService.create(request);

        assertThat(created.getStatus()).isEqualTo(0);
        assertThat(created.getPolicyCode()).isEqualTo("ZD-2026-0041");
        assertThat(created.getOwnerDeptId()).isEqualTo(2L);
    }

    private IcpPolicy policy(Long id, int status) {
        IcpPolicy policy = new IcpPolicy();
        policy.setId(id);
        policy.setPolicyCode("ZD-2026-0001");
        policy.setPolicyName("种子制度");
        policy.setOwnerDeptId(2L);
        policy.setOwnerUserId(3L);
        policy.setStatus(status);
        policy.setPublishTime(status == 2 ? java.time.LocalDateTime.now() : null);
        policy.setEffectiveDate(status == 2 ? LocalDate.of(2026, 1, 1) : null);
        return policy;
    }
}

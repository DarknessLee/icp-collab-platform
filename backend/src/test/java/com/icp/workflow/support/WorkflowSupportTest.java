package com.icp.workflow.support;

import com.icp.common.security.UserPrincipal;
import com.icp.system.mapper.SysDeptMapper;
import com.icp.system.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2c 流程常量与参与人解析单元测试。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowSupportTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SysDeptMapper sysDeptMapper;

    @Mock
    private com.icp.system.mapper.SysUserRoleMapper sysUserRoleMapper;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sixProcessKeysHaveNamesTitlesTypesAndApproverGroups() {
        assertThat(WorkflowConstants.PROCESS_KEYS).hasSize(6).containsExactly(
                WorkflowConstants.POLICY_PUBLISH, WorkflowConstants.DEFECT_RECTIFY,
                WorkflowConstants.EVALUATION_PLAN, WorkflowConstants.DOC_BORROW,
                WorkflowConstants.PURCHASE_APPLY, WorkflowConstants.SEAL_APPLY);
        for (String key : WorkflowConstants.PROCESS_KEYS) {
            assertThat(WorkflowConstants.PROCESS_NAMES.get(key)).isNotBlank();
            assertThat(WorkflowConstants.TITLE_PREFIX.get(key)).isNotBlank();
            assertThat(WorkflowConstants.BUSINESS_TYPES.get(key)).isNotBlank();
            List<String> groups = WorkflowConstants.APPROVER_GROUPS.get(key);
            assertThat(groups).hasSize(2);
            for (String group : groups) {
                assertThat(group).startsWith("ROLE_");
            }
        }
        assertThat(WorkflowConstants.DEFAULT_TIMEOUT_HOURS).isEqualTo(48L);
        assertThat(WorkflowConstants.CONFIG_TIMEOUT_HOURS).isEqualTo("workflow.timeout.hours");
    }

    @Test
    void userResolverMapsRolesToUppercaseCandidateGroups() {
        UserPrincipal principal = new UserPrincipal(3L, "icpadmin", "李静", 2L,
                List.of("ICP_MANAGER", "dept_manager"), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        WorkflowUserResolver resolver = new WorkflowUserResolver(sysUserMapper, sysDeptMapper, sysUserRoleMapper);
        Set<String> groups = resolver.currentRoleGroups();
        assertThat(groups).containsExactly("ROLE_ICP_MANAGER", "ROLE_DEPT_MANAGER");
        assertThat(resolver.currentUserId()).isEqualTo(3L);
        assertThat(resolver.currentRealName()).isEqualTo("李静");
    }
}

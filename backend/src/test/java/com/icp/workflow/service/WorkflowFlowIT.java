package com.icp.workflow.service;

import com.icp.common.security.UserPrincipal;
import com.icp.workflow.dto.WorkflowRequests;
import com.icp.workflow.entity.WfBusinessLink;
import com.icp.workflow.support.WorkflowConstants;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M2c 流程全链路集成测试（真实 Flowable 7 + MySQL 3307）。
 *
 * <p>名称以 IT 结尾，默认 surefire 不执行；需在本地 MySQL 与种子库就绪后执行：
 * mvn -B -Dtest=WorkflowFlowIT test</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class WorkflowFlowIT {

    private static final long ICP_ADMIN_ID = 3L;
    private static final long DEPT01_ID = 5L;

    @Autowired
    private WorkflowInstanceService instanceService;

    @Autowired
    private WorkflowTaskService taskService;

    @Autowired
    private RuntimeService runtimeService;

    @BeforeEach
    void setUp() {
        authenticate(ICP_ADMIN_ID, "icpadmin", "ICP_MANAGER", 2L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(long userId, String username, String role, Long deptId) {
        UserPrincipal principal = new UserPrincipal(userId, username, username, deptId,
                List.of(role), List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void purchaseFlowApproveRejectResubmitAndOverdue() {
        Map<String, Object> form = new HashMap<>();
        form.put("title", "IT purchase flow");
        form.put("amount", 1234.56);
        form.put("budgetAmount", 5000);
        form.put("supplier", "IT supplier");
        form.put("purchaseType", 1);
        form.put("purpose", "it");
        Map<String, Object> started = instanceService.start(
                new WorkflowRequests.StartRequest(WorkflowConstants.PURCHASE_APPLY, null, "purchase", form, null), null);
        String processInstanceId = String.valueOf(started.get("processInstanceId"));
        assertThat(processInstanceId).isNotBlank();
        assertThat(started.get("pendingWorkflow")).isEqualTo(false);

        // 候选组以逗号串注入，引擎应按 2 个候选组解析
        Object groups = runtimeService.getVariable(processInstanceId, WorkflowConstants.VAR_APPROVER_GROUPS);
        assertThat(groups).isInstanceOf(List.class);
        assertThat((List<?>) groups).hasSize(2);

        // 发起节点任务由发起人办理
        Map<String, Object> submitTask = findTodoFor(processInstanceId);
        assertThat(submitTask).isNotNull();
        taskService.approve(String.valueOf(submitTask.get("taskId")), new WorkflowRequests.ApproveRequest("submit", null, null));
        assertThat(findTodoFor(processInstanceId)).isNull();

        // 审批节点由部门负责人办理
        authenticate(DEPT01_ID, "dept01", "DEPT_MANAGER", 4L);
        Map<String, Object> approveTask = findTodoFor(processInstanceId);
        assertThat(approveTask).isNotNull();
        Map<String, Object> result = taskService.approve(
                String.valueOf(approveTask.get("taskId")), new WorkflowRequests.ApproveRequest("agree", null, null));
        assertThat(result.get("finished")).isEqualTo(true);
        assertThat(result.get("linkStatus")).isEqualTo(WorkflowConstants.LINK_APPROVED);

        // 驳回时业务单据回退，可重新提交
        authenticate(ICP_ADMIN_ID, "icpadmin", "ICP_MANAGER", 2L);
        Map<String, Object> form2 = new HashMap<>(form);
        form2.put("title", "IT reject flow");
        Map<String, Object> started2 = instanceService.start(
                new WorkflowRequests.StartRequest(WorkflowConstants.PURCHASE_APPLY, null, "purchase", form2, null), null);
        String pi2 = String.valueOf(started2.get("processInstanceId"));
        Map<String, Object> submit2 = findTodoFor(pi2);
        taskService.approve(String.valueOf(submit2.get("taskId")), new WorkflowRequests.ApproveRequest("submit", null, null));
        authenticate(DEPT01_ID, "dept01", "DEPT_MANAGER", 4L);
        Map<String, Object> approve2 = findTodoFor(pi2);
        Map<String, Object> rejected = taskService.reject(
                String.valueOf(approve2.get("taskId")), new WorkflowRequests.RejectRequest("budget not enough"));
        assertThat(rejected.get("linkStatus")).isEqualTo(WorkflowConstants.LINK_REJECTED);

        authenticate(ICP_ADMIN_ID, "icpadmin", "ICP_MANAGER", 2L);
        Map<String, Object> resubmitted = instanceService.resubmit(pi2, "resubmit again");
        assertThat(resubmitted.get("status")).isEqualTo(WorkflowConstants.LINK_RUNNING);

        // 超时计算：48 小时内未超时，构造 49 小时前发起判定超时
        WfBusinessLink fresh = new WfBusinessLink();
        fresh.setStatus(WorkflowConstants.LINK_RUNNING);
        fresh.setStartTime(LocalDateTime.now().minusHours(1));
        assertThat(instanceService.isOverdue(fresh)).isFalse();
        WfBusinessLink stale = new WfBusinessLink();
        stale.setStatus(WorkflowConstants.LINK_RUNNING);
        stale.setStartTime(LocalDateTime.now().minusHours(49));
        assertThat(instanceService.isOverdue(stale)).isTrue();
        assertThat(instanceService.isOverdue(stale, 72)).isFalse();

        // 未办理人不能查看任务详情
        authenticate(DEPT01_ID, "dept01", "DEPT_MANAGER", 4L);
        final String someTaskId = String.valueOf(findTodoFor(pi2).get("taskId"));
        authenticate(ICP_ADMIN_ID + 1000, "nobody", "EMPLOYEE", 8L);
        assertThatThrownBy(() -> taskService.taskDetail(someTaskId))
                .isInstanceOf(com.icp.common.exception.BusinessException.class);
    }

    private Map<String, Object> findTodoFor(String processInstanceId) {
        var page = taskService.todo(1, 500, null, null);
        for (Map<String, Object> item : page.getList()) {
            if (processInstanceId.equals(item.get("processInstanceId"))) {
                return item;
            }
        }
        return null;
    }
}

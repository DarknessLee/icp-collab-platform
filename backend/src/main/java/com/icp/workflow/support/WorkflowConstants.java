package com.icp.workflow.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程中心常量（docs/03 §5、docs/04 §3、docs/05 §3）。
 *
 * <p>6 条内置流程与 BPMN 资源一一对应，wf_form.form_code 与流程 key 相同；
 * wf_process_meta 无 candidate_groups 字段（docs/04 冻结），候选组在发起时以流程变量
 * {@code approverGroups} 注入，BPMN 使用 {@code flowable:candidateGroups="${approverGroups}"} 读取。</p>
 */
public final class WorkflowConstants {

    private WorkflowConstants() {
    }

    public static final String POLICY_PUBLISH = "policy_publish";
    public static final String DEFECT_RECTIFY = "defect_rectify";
    public static final String EVALUATION_PLAN = "evaluation_plan";
    public static final String DOC_BORROW = "doc_borrow";
    public static final String PURCHASE_APPLY = "purchase_apply";
    public static final String SEAL_APPLY = "seal_apply";

    /** 流程 key 顺序即流程定义列表展示顺序。 */
    public static final List<String> PROCESS_KEYS = List.of(
            POLICY_PUBLISH, DEFECT_RECTIFY, EVALUATION_PLAN, DOC_BORROW, PURCHASE_APPLY, SEAL_APPLY);

    public static final Map<String, String> PROCESS_NAMES = Map.of(
            POLICY_PUBLISH, "制度发布审批",
            DEFECT_RECTIFY, "缺陷整改审批",
            EVALUATION_PLAN, "评价计划审批",
            DOC_BORROW, "文档借阅审批",
            PURCHASE_APPLY, "采购申请审批",
            SEAL_APPLY, "用印申请审批");

    /** 业务单据标题前缀。 */
    public static final Map<String, String> TITLE_PREFIX = Map.of(
            POLICY_PUBLISH, "制度发布",
            DEFECT_RECTIFY, "缺陷整改",
            EVALUATION_PLAN, "评价计划",
            DOC_BORROW, "文档借阅",
            PURCHASE_APPLY, "采购申请",
            SEAL_APPLY, "用印申请");

    /** 业务类型（写入 wf_business_link.business_type，用于业务回调）。 */
    public static final Map<String, String> BUSINESS_TYPES = Map.of(
            POLICY_PUBLISH, "policy",
            DEFECT_RECTIFY, "rectify",
            EVALUATION_PLAN, "evaluation",
            DOC_BORROW, "borrow",
            PURCHASE_APPLY, "purchase",
            SEAL_APPLY, "seal");

    /** 各流程审批节点候选组（ROLE_ 前缀，与 UserPrincipal.roles 对应）。 */
    public static final Map<String, List<String>> APPROVER_GROUPS;

    static {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put(POLICY_PUBLISH, List.of("ROLE_ICP_MANAGER", "ROLE_MANAGEMENT"));
        groups.put(DEFECT_RECTIFY, List.of("ROLE_DEPT_MANAGER", "ROLE_ICP_MANAGER"));
        groups.put(EVALUATION_PLAN, List.of("ROLE_ICP_MANAGER", "ROLE_MANAGEMENT"));
        groups.put(DOC_BORROW, List.of("ROLE_DEPT_MANAGER", "ROLE_ICP_MANAGER"));
        groups.put(PURCHASE_APPLY, List.of("ROLE_DEPT_MANAGER", "ROLE_MANAGEMENT"));
        groups.put(SEAL_APPLY, List.of("ROLE_DEPT_MANAGER", "ROLE_MANAGEMENT"));
        APPROVER_GROUPS = Map.copyOf(groups);
    }

    /** 关联业务单据状态：wf_business_link.status。 */
    public static final int LINK_RUNNING = 0;
    public static final int LINK_APPROVED = 1;
    public static final int LINK_REJECTED = 2;
    public static final int LINK_WITHDRAWN = 3;

    /** 历史动作（ACT_HI_COMMENT.type）。 */
    public static final String ACTION_APPROVE = "approve";
    public static final String ACTION_REJECT = "reject";
    public static final String ACTION_SUBMIT = "submit";
    public static final String ACTION_RESUBMIT = "resubmit";

    public static final String VAR_INITIATOR = "initiator";
    public static final String VAR_INITIATOR_NAME = "initiatorName";
    public static final String VAR_APPROVER_GROUPS = "approverGroups";
    public static final String VAR_FORM_DATA = "formData";
    public static final String VAR_BUSINESS_NO = "businessNo";
    public static final String VAR_PROCESS_KEY = "processKey";
    public static final String VAR_TITLE = "title";

    /** 流程超时配置 key（sys_config），默认 48 小时。 */
    public static final String CONFIG_TIMEOUT_HOURS = "workflow.timeout.hours";
    public static final long DEFAULT_TIMEOUT_HOURS = 48L;

    public static final String DEPLOYMENT_NAME = "icp-processes";

    public static boolean isKnown(String processKey) {
        return PROCESS_KEYS.contains(processKey);
    }

    public static Set<String> knownKeys() {
        return Set.copyOf(PROCESS_KEYS);
    }
}

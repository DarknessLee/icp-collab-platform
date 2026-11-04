package com.icp.workflow.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.exception.BusinessException;
import com.icp.workflow.dto.WorkflowRequests;
import com.icp.workflow.entity.WfBusinessLink;
import com.icp.workflow.entity.WfTaskUrge;
import com.icp.workflow.mapper.CollMessageMapper;
import com.icp.workflow.mapper.WfTaskUrgeMapper;
import com.icp.workflow.support.WorkflowConstants;
import com.icp.workflow.support.WorkflowUserResolver;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程任务服务：待办 / 已办 / 任务详情 / 同意 / 驳回 / 催办（docs/03 §5）。
 */
@Service
public class WorkflowTaskService {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final WorkflowInstanceService instanceService;
    private final WfTaskUrgeMapper urgeMapper;
    private final CollMessageMapper collMessageMapper;
    private final WorkflowUserResolver userResolver;

    public WorkflowTaskService(TaskService taskService,
                               RuntimeService runtimeService,
                               HistoryService historyService,
                               WorkflowInstanceService instanceService,
                               WfTaskUrgeMapper urgeMapper,
                               CollMessageMapper collMessageMapper,
                               WorkflowUserResolver userResolver) {
        this.taskService = taskService;
        this.runtimeService = runtimeService;
        this.historyService = historyService;
        this.instanceService = instanceService;
        this.urgeMapper = urgeMapper;
        this.collMessageMapper = collMessageMapper;
        this.userResolver = userResolver;
    }

    // ------------------------------------------------------------------ 待办 / 已办

    public PageResult<Map<String, Object>> todo(int pageNum, int pageSize, String processKey, String keyword) {
        Long userId = userResolver.currentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Set<String> groups = userResolver.currentRoleGroups();
        org.flowable.task.api.TaskQuery query = taskService.createTaskQuery().active();
        if (groups.isEmpty()) {
            query.taskAssignee(String.valueOf(userId));
        } else {
            query.or().taskCandidateGroupIn(new ArrayList<>(groups)).taskAssignee(String.valueOf(userId)).endOr();
        }
        if (processKey != null && !processKey.isBlank()) {
            query.processDefinitionKey(processKey);
        }
        query.orderByTaskCreateTime().desc();
        List<Task> tasks = query.list();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Task task : tasks) {
            Map<String, Object> item = toTodoItem(task);
            if (keyword != null && !keyword.isBlank() && !matchKeyword(item, keyword)) {
                continue;
            }
            items.add(item);
        }
        return page(items, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> done(int pageNum, int pageSize, String processKey, String keyword) {
        Long userId = userResolver.currentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        org.flowable.task.api.history.HistoricTaskInstanceQuery query = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(String.valueOf(userId))
                .finished();
        if (processKey != null && !processKey.isBlank()) {
            query.processDefinitionKey(processKey);
        }
        query.orderByHistoricTaskInstanceEndTime().desc();
        List<HistoricTaskInstance> tasks = query.list();
        List<Map<String, Object>> items = new ArrayList<>();
        for (HistoricTaskInstance task : tasks) {
            WfBusinessLink link = instanceService.requireLinkQuietly(task.getProcessInstanceId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", task.getId());
            item.put("processInstanceId", task.getProcessInstanceId());
            item.put("processKey", link == null ? null : link.getProcessKey());
            item.put("title", link == null ? null : link.getTitle());
            item.put("businessNo", link == null ? null : link.getBusinessNo());
            item.put("nodeName", task.getName());
            item.put("assignee", task.getAssignee());
            item.put("assigneeName", userResolver.nameOf(WorkflowInstanceService.parseLong(task.getAssignee())));
            item.put("startTime", WorkflowInstanceService.toLocal(task.getCreateTime()));
            item.put("endTime", WorkflowInstanceService.toLocal(task.getEndTime()));
            item.put("durationSeconds", task.getDurationInMillis() == null ? null : task.getDurationInMillis() / 1000);
            item.put("action", lastAction(task.getId()));
            if (keyword != null && !keyword.isBlank() && !matchKeyword(item, keyword)) {
                continue;
            }
            items.add(item);
        }
        return page(items, pageNum, pageSize);
    }

    private String lastAction(String taskId) {
        try {
            List<org.flowable.engine.task.Comment> comments = taskService.getTaskComments(taskId);
            if (comments != null && !comments.isEmpty()) {
                return comments.get(comments.size() - 1).getType();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private Map<String, Object> toTodoItem(Task task) {
        WfBusinessLink link = instanceService.requireLinkQuietly(task.getProcessInstanceId());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("taskId", task.getId());
        item.put("processInstanceId", task.getProcessInstanceId());
        item.put("processKey", link == null ? null : link.getProcessKey());
        item.put("title", link == null ? null : link.getTitle());
        item.put("businessNo", link == null ? null : link.getBusinessNo());
        item.put("businessType", link == null ? null : link.getBusinessType());
        item.put("nodeName", task.getName());
        item.put("activityId", task.getTaskDefinitionKey());
        item.put("assignee", task.getAssignee());
        item.put("initiatorId", link == null ? null : link.getInitiatorId());
        item.put("initiatorName", link == null ? null : userResolver.nameOf(link.getInitiatorId()));
        item.put("createTime", WorkflowInstanceService.toLocal(task.getCreateTime()));
        item.put("dueTime", link == null ? null : instanceService.dueTime(link));
        item.put("overdue", link != null && instanceService.isOverdue(link));
        return item;
    }

    private boolean matchKeyword(Map<String, Object> item, String keyword) {
        String lower = keyword.toLowerCase();
        Object[] values = {item.get("title"), item.get("businessNo"), item.get("nodeName")};
        for (Object value : values) {
            if (value != null && String.valueOf(value).toLowerCase().contains(lower)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 任务详情

    public Map<String, Object> taskDetail(String taskId) {
        Task task = requireActiveTask(taskId);
        if (!instanceService.isCurrentHandler(task)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅当前办理人可查看该任务");
        }
        WfBusinessLink link = instanceService.requireLink(task.getProcessInstanceId());
        Map<String, Object> detail = instanceService.detail(task.getProcessInstanceId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.getId());
        data.put("processInstanceId", task.getProcessInstanceId());
        data.put("processKey", link.getProcessKey());
        data.put("title", link.getTitle());
        data.put("businessNo", link.getBusinessNo());
        data.put("businessType", link.getBusinessType());
        data.put("processName", detail.get("processName"));
        data.put("initiatorId", link.getInitiatorId());
        data.put("initiatorName", userResolver.nameOf(link.getInitiatorId()));
        data.put("nodeName", task.getName());
        data.put("activityId", task.getTaskDefinitionKey());
        data.put("assignee", task.getAssignee());
        data.put("assigneeName", userResolver.nameOf(WorkflowInstanceService.parseLong(task.getAssignee())));
        data.put("createTime", WorkflowInstanceService.toLocal(task.getCreateTime()));
        data.put("dueTime", instanceService.dueTime(link));
        data.put("overdue", instanceService.isOverdue(link));
        data.put("formSchema", detail.get("formSchema"));
        data.put("formData", detail.get("formData"));
        data.put("business", detail.get("business"));
        data.put("attachments", detail.get("attachments"));
        List<Map<String, Object>> actions = new ArrayList<>();
        Map<String, Object> approve = new LinkedHashMap<>();
        approve.put("code", "approve");
        approve.put("label", isSubmitNode(task) ? "提交" : "同意");
        actions.add(approve);
        if (!isSubmitNode(task)) {
            Map<String, Object> reject = new LinkedHashMap<>();
            reject.put("code", "reject");
            reject.put("label", "驳回");
            reject.put("requireComment", true);
            actions.add(reject);
        }
        data.put("actions", actions);
        return data;
    }

    private boolean isSubmitNode(Task task) {
        return "submit".equals(task.getTaskDefinitionKey());
    }

    // ------------------------------------------------------------------ 同意 / 驳回

    @Transactional
    public Map<String, Object> approve(String taskId, WorkflowRequests.ApproveRequest request) {
        Task task = requireActiveTask(taskId);
        if (!instanceService.isCurrentHandler(task)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅当前办理人可审批该任务");
        }
        WfBusinessLink link = instanceService.requireLink(task.getProcessInstanceId());
        Long userId = userResolver.currentUserId();
        if (task.getAssignee() == null && userId != null) {
            // 候选组任务办理时记录实际办理人，便于“已办”按人查询（ACT_HI_TASKINST.assignee）
            taskService.setAssignee(taskId, String.valueOf(userId));
        }
        String type = isSubmitNode(task) ? WorkflowConstants.ACTION_SUBMIT : WorkflowConstants.ACTION_APPROVE;
        String message = request == null || request.comment() == null || request.comment().isBlank()
                ? (isSubmitNode(task) ? "提交" : "同意") : request.comment();
        taskService.addComment(taskId, task.getProcessInstanceId(), type, message);
        if (request != null && request.formData() != null && !request.formData().isEmpty()) {
            Object current = instanceService.variable(task.getProcessInstanceId(), WorkflowConstants.VAR_FORM_DATA);
            Map<String, Object> merged = new LinkedHashMap<>();
            if (current instanceof String text) {
                try {
                    Map<String, Object> parsed = parseMap(text);
                    if (parsed != null) {
                        merged.putAll(parsed);
                    }
                } catch (Exception ignored) {
                    // ignore malformed existing value
                }
            }
            merged.putAll(request.formData());
            runtimeService.setVariable(task.getProcessInstanceId(), WorkflowConstants.VAR_FORM_DATA,
                    instanceService.toJson(merged));
        }
        if (request != null && request.attachmentIds() != null) {
            for (Long fileId : request.attachmentIds()) {
                if (fileId != null) {
                    taskService.createAttachment("attachment", taskId, task.getProcessInstanceId(),
                            "附件-" + fileId, null, "/api/v1/files/" + fileId + "/download");
                }
            }
        }
        taskService.complete(taskId);
        boolean finished = runtimeService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId()).count() == 0;
        if (finished) {
            instanceService.markLinkApproved(link);
            instanceService.onApproved(link, userId, message);
        }
        Task next = finished ? null : instanceService.currentTask(task.getProcessInstanceId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("processInstanceId", task.getProcessInstanceId());
        result.put("finished", finished);
        result.put("linkStatus", finished ? WorkflowConstants.LINK_APPROVED : WorkflowConstants.LINK_RUNNING);
        result.put("nextTaskId", next == null ? null : next.getId());
        result.put("nextNodeName", next == null ? null : next.getName());
        result.put("businessNo", link.getBusinessNo());
        return result;
    }

    @Transactional
    public Map<String, Object> reject(String taskId, WorkflowRequests.RejectRequest request) {
        if (request == null || request.comment() == null || request.comment().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "驳回意见不能为空");
        }
        Task task = requireActiveTask(taskId);
        if (!instanceService.isCurrentHandler(task)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅当前办理人可驳回该任务");
        }
        if (isSubmitNode(task)) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "发起节点不支持驳回");
        }
        WfBusinessLink link = instanceService.requireLink(task.getProcessInstanceId());
        Long userId = userResolver.currentUserId();
        if (task.getAssignee() == null && userId != null) {
            taskService.setAssignee(taskId, String.valueOf(userId));
        }
        taskService.addComment(taskId, task.getProcessInstanceId(), WorkflowConstants.ACTION_REJECT, request.comment());
        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(task.getProcessInstanceId())
                .moveActivityIdTo(task.getTaskDefinitionKey(), "submit")
                .changeState();
        instanceService.markLinkRejected(link);
        instanceService.onRejected(link);
        Task current = instanceService.currentTask(task.getProcessInstanceId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("processInstanceId", task.getProcessInstanceId());
        result.put("linkStatus", WorkflowConstants.LINK_REJECTED);
        result.put("rejected", true);
        result.put("nextTaskId", current == null ? null : current.getId());
        result.put("businessNo", link.getBusinessNo());
        return result;
    }

    // ------------------------------------------------------------------ 催办

    @Transactional
    public Map<String, Object> urge(String taskId, WorkflowRequests.UrgeRequest request) {
        Task task = requireActiveTask(taskId);
        Long userId = userResolver.currentUserId();
        WfBusinessLink link = instanceService.requireLink(task.getProcessInstanceId());
        boolean allowed = (userId != null && userId.equals(link.getInitiatorId())) || instanceService.isCurrentHandler(task);
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅流程参与人可催办");
        }
        String content = request == null || request.content() == null || request.content().isBlank()
                ? "请尽快处理该流程任务" : request.content();
        Long targetId = WorkflowInstanceService.parseLong(task.getAssignee());
        if (targetId == null) {
            // 候选组任务（如审批节点）没有具体办理人：取候选组中第一个有效用户作为消息接收人
            java.util.List<String> groupIds = new ArrayList<>();
            for (org.flowable.identitylink.api.IdentityLink identityLink
                    : taskService.getIdentityLinksForTask(taskId)) {
                if ("candidate".equals(identityLink.getType()) && identityLink.getGroupId() != null) {
                    groupIds.add(identityLink.getGroupId());
                }
            }
            targetId = userResolver.firstUserIdOfGroups(groupIds);
        }
        if (targetId == null) {
            targetId = link.getInitiatorId();
        }
        WfTaskUrge urge = new WfTaskUrge();
        urge.setProcessInstanceId(task.getProcessInstanceId());
        urge.setTaskId(taskId);
        urge.setBusinessNo(link.getBusinessNo());
        urge.setUrgeUserId(userId);
        urge.setTargetUserId(targetId);
        urge.setContent(content);
        urge.setUrgeTime(LocalDateTime.now());
        urgeMapper.insert(urge);
        collMessageMapper.insertMessage(5, "催办提醒：" + link.getTitle(), content, "URGE",
                parseLongSafe(taskId), userId, targetId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("urgeId", urge.getId());
        result.put("taskId", taskId);
        result.put("processInstanceId", task.getProcessInstanceId());
        result.put("content", content);
        result.put("urgeTime", urge.getUrgeTime());
        result.put("message", "催办成功");
        return result;
    }

    private Task requireActiveTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在或已办理");
        }
        return task;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static Long parseLongSafe(String value) {
        return WorkflowInstanceService.parseLong(value);
    }

    private static <T> PageResult<T> page(List<T> items, int pageNum, int pageSize) {
        long total = items.size();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(items.size(), from + pageSize);
        List<T> pageItems = from >= items.size() ? List.of() : items.subList(from, to);
        return new PageResult<>(new ArrayList<>(pageItems), total, pageNum, pageSize);
    }
}

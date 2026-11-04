package com.icp.collab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollMessage;
import com.icp.collab.entity.CollTask;
import com.icp.collab.entity.CollTaskComment;
import com.icp.collab.mapper.CollabMessageMapper;
import com.icp.collab.mapper.CollTaskCommentMapper;
import com.icp.collab.mapper.CollTaskMapper;
import com.icp.collab.support.CollabLookupService;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.icp.support.CodeSeq;
import com.icp.system.service.DataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 协同任务（M2d / docs/03 §6.1）：
 * 状态 0待处理 → 1进行中 → 2已完成 / 3已取消；逾期按 plan_end < now 且状态 0/1 查询期实时计算；
 * 子任务（parent_id）独立流转；数据权限（本人任务/本部门任务）。
 */
@Service
public class TaskService {

    public static final int STATUS_TODO = 0;
    public static final int STATUS_DOING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_CANCELLED = 3;

    private final CollTaskMapper taskMapper;
    private final CollTaskCommentMapper commentMapper;
    private final CollabMessageMapper messageMapper;
    private final CollabLookupService lookup;
    private final DataScopeService dataScopeService;

    public TaskService(CollTaskMapper taskMapper,
                       CollTaskCommentMapper commentMapper,
                       CollabMessageMapper messageMapper,
                       CollabLookupService lookup,
                       DataScopeService dataScopeService) {
        this.taskMapper = taskMapper;
        this.commentMapper = commentMapper;
        this.messageMapper = messageMapper;
        this.lookup = lookup;
        this.dataScopeService = dataScopeService;
    }

    public PageResult<CollTask> page(long pageNum, long pageSize, Long assigneeId, Integer priority,
                                     Integer status, LocalDate planEnd, String keyword) {
        DataScopeInfo info = dataScopeService.current();
        LambdaQueryWrapper<CollTask> wrapper = new LambdaQueryWrapper<>();
        applyScope(wrapper, info);
        if (assigneeId != null) {
            wrapper.eq(CollTask::getAssigneeId, assigneeId);
        }
        if (priority != null) {
            wrapper.eq(CollTask::getPriority, priority);
        }
        if (status != null) {
            wrapper.eq(CollTask::getStatus, status);
        }
        if (planEnd != null) {
            wrapper.le(CollTask::getPlanEnd, planEnd);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(CollTask::getTaskTitle, keyword)
                    .or().like(CollTask::getTaskNo, keyword)
                    .or().like(CollTask::getDescription, keyword));
        }
        wrapper.orderByAsc(CollTask::getStatus).orderByAsc(CollTask::getPlanEnd).orderByDesc(CollTask::getId);
        Page<CollTask> page = taskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillList(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    /** GET /tasks/my?scope=mine|created 固定当前用户。 */
    public PageResult<CollTask> my(String scope, long pageNum, long pageSize, Integer status, String keyword) {
        Long me = lookup.currentUserId();
        LambdaQueryWrapper<CollTask> wrapper = new LambdaQueryWrapper<>();
        if ("created".equalsIgnoreCase(scope)) {
            wrapper.eq(CollTask::getCreatorId, me);
        } else {
            wrapper.eq(CollTask::getAssigneeId, me);
        }
        if (status != null) {
            wrapper.eq(CollTask::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(CollTask::getTaskTitle, keyword)
                    .or().like(CollTask::getTaskNo, keyword));
        }
        wrapper.orderByAsc(CollTask::getStatus).orderByAsc(CollTask::getPlanEnd);
        Page<CollTask> page = taskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillList(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    /** GET /tasks/overdue：仅未完成任务中 plan_end < now。 */
    public PageResult<CollTask> overdue(long pageNum, long pageSize) {
        DataScopeInfo info = dataScopeService.current();
        LambdaQueryWrapper<CollTask> wrapper = new LambdaQueryWrapper<>();
        applyScope(wrapper, info);
        wrapper.in(CollTask::getStatus, STATUS_TODO, STATUS_DOING)
                .isNotNull(CollTask::getPlanEnd)
                .lt(CollTask::getPlanEnd, LocalDate.now())
                .orderByAsc(CollTask::getPlanEnd);
        Page<CollTask> page = taskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillList(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public CollTask get(Long id) {
        CollTask task = requireVisible(id);
        fillOne(task);
        task.setComments(comments(id));
        List<CollTask> children = taskMapper.selectList(new LambdaQueryWrapper<CollTask>()
                .eq(CollTask::getParentId, id).orderByAsc(CollTask::getId));
        fillList(children);
        task.setChildren(children);
        return task;
    }

    @Transactional
    public CollTask create(CollabRequests.TaskCreate request) {
        Long me = lookup.currentUserId();
        String title = requireText(request.taskTitle(), "任务标题", 200);
        lookup.requireUser(request.assigneeId());
        if (request.planStart() != null && request.planEnd() != null && request.planStart().isAfter(request.planEnd())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "计划开始时间不能晚于计划结束时间");
        }
        CollTask task = new CollTask();
        task.setTaskNo(CodeSeq.next(taskMapper.selectMaxNo(), "RW", 4));
        task.setTaskTitle(title);
        task.setTaskType(request.taskType() == null ? 2 : request.taskType());
        task.setPriority(request.priority() == null ? 2 : requireRange(request.priority(), 1, 3, "优先级"));
        task.setStatus(STATUS_TODO);
        task.setCreatorId(me);
        task.setAssigneeId(request.assigneeId());
        task.setDeptId(request.deptId() != null ? request.deptId() : defaultDept(request.assigneeId()));
        task.setPlanStart(request.planStart());
        task.setPlanEnd(request.planEnd());
        task.setRemindTime(request.remindTime());
        task.setDescription(request.description());
        task.setRemark(request.remark());
        task.setProgress(0);
        task.setParentId(request.parentId() == null ? 0L : request.parentId());
        if (task.getParentId() > 0) {
            CollTask parent = taskMapper.selectById(task.getParentId());
            if (parent == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "父任务不存在");
            }
            task.setTaskType(3);
        }
        taskMapper.insert(task);
        fillOne(task);
        return task;
    }

    @Transactional
    public CollTask update(Long id, CollabRequests.TaskUpdate request) {
        CollTask task = requireVisible(id);
        if (isTerminal(task)) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "已完成或已取消的任务不可编辑");
        }
        if (request.taskTitle() != null) {
            task.setTaskTitle(requireText(request.taskTitle(), "任务标题", 200));
        }
        if (request.priority() != null) {
            task.setPriority(requireRange(request.priority(), 1, 3, "优先级"));
        }
        if (request.assigneeId() != null) {
            lookup.requireUser(request.assigneeId());
            task.setAssigneeId(request.assigneeId());
            if (request.deptId() == null) {
                task.setDeptId(defaultDept(request.assigneeId()));
            }
        }
        if (request.deptId() != null) {
            task.setDeptId(request.deptId());
        }
        if (request.planStart() != null) {
            task.setPlanStart(request.planStart());
        }
        if (request.planEnd() != null) {
            task.setPlanEnd(request.planEnd());
        }
        if (task.getPlanStart() != null && task.getPlanEnd() != null && task.getPlanStart().isAfter(task.getPlanEnd())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "计划开始时间不能晚于计划结束时间");
        }
        if (request.remindTime() != null) {
            task.setRemindTime(request.remindTime());
        }
        if (request.description() != null) {
            task.setDescription(request.description());
        }
        if (request.remark() != null) {
            task.setRemark(request.remark());
        }
        if (request.parentId() != null && !Objects.equals(request.parentId(), task.getParentId())) {
            validateNewParent(task, request.parentId());
            task.setParentId(request.parentId());
            task.setTaskType(request.parentId() > 0 ? 3 : 2);
        }
        taskMapper.updateById(task);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        CollTask task = requireVisible(id);
        Long me = lookup.currentUserId();
        if (!lookup.isAdmin() && !Objects.equals(me, task.getCreatorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅任务创建人可删除任务");
        }
        if (taskMapper.countChildren(id) > 0) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "请先删除或移动子任务");
        }
        taskMapper.deleteById(id);
    }

    @Transactional
    public Map<String, Object> start(Long id) {
        CollTask task = requireVisible(id);
        if (!Objects.equals(task.getStatus(), STATUS_TODO)) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅待处理状态的任务可开始");
        }
        task.setStatus(STATUS_DOING);
        task.setActualStart(LocalDateTime.now());
        taskMapper.updateById(task);
        return stateResult(task, "actualStart");
    }

    @Transactional
    public Map<String, Object> complete(Long id) {
        CollTask task = requireVisible(id);
        if (!Objects.equals(task.getStatus(), STATUS_TODO) && !Objects.equals(task.getStatus(), STATUS_DOING)) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅待处理或进行中任务可完成");
        }
        task.setStatus(STATUS_DONE);
        task.setProgress(100);
        task.setActualEnd(LocalDateTime.now());
        if (task.getActualStart() == null) {
            task.setActualStart(task.getActualEnd());
        }
        taskMapper.updateById(task);
        notify(task.getCreatorId(), task.getAssigneeId(), "任务已完成：" + task.getTaskNo(),
                "任务「" + task.getTaskTitle() + "」已由 " + lookup.currentRealName() + " 完成", "TASK", task.getId());
        return stateResult(task, "actualEnd");
    }

    @Transactional
    public Map<String, Object> cancel(Long id) {
        CollTask task = requireVisible(id);
        if (!Objects.equals(task.getStatus(), STATUS_TODO) && !Objects.equals(task.getStatus(), STATUS_DOING)) {
            throw new BusinessException(ErrorCode.STATE_INVALID, "仅待处理或进行中任务可取消");
        }
        task.setStatus(STATUS_CANCELLED);
        taskMapper.updateById(task);
        notify(task.getAssigneeId(), lookup.currentUserId(), "任务已取消：" + task.getTaskNo(),
                "任务「" + task.getTaskTitle() + "」已被 " + lookup.currentRealName() + " 取消", "TASK", task.getId());
        return stateResult(task, null);
    }

    public List<CollTaskComment> comments(Long taskId) {
        requireVisible(taskId);
        List<CollTaskComment> list = commentMapper.selectList(new LambdaQueryWrapper<CollTaskComment>()
                .eq(CollTaskComment::getTaskId, taskId).orderByAsc(CollTaskComment::getId));
        Map<Long, String> names = lookup.userNames(list.stream().map(CollTaskComment::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        list.forEach(item -> item.setUserName(names.get(item.getUserId())));
        return list;
    }

    @Transactional
    public CollTaskComment addComment(Long taskId, String content) {
        CollTask task = requireVisible(taskId);
        String text = requireText(content, "评论内容", 1000);
        CollTaskComment comment = new CollTaskComment();
        comment.setTaskId(taskId);
        comment.setUserId(lookup.currentUserId());
        comment.setContent(text);
        commentMapper.insert(comment);
        comment.setUserName(lookup.currentRealName());
        Long me = lookup.currentUserId();
        if (!Objects.equals(me, task.getAssigneeId())) {
            notify(task.getAssigneeId(), me, "任务评论：" + task.getTaskNo(), text, "TASK_COMMENT", taskId);
        }
        if (!Objects.equals(me, task.getCreatorId())) {
            notify(task.getCreatorId(), me, "任务评论：" + task.getTaskNo(), text, "TASK_COMMENT", taskId);
        }
        return comment;
    }

    /** POST /tasks/{id}/remind → coll_message(message_type=3, biz_type=REMIND)。 */
    @Transactional
    public Map<String, Object> remind(Long id) {
        CollTask task = requireVisible(id);
        Long me = lookup.currentUserId();
        if (!Objects.equals(me, task.getCreatorId()) && !Objects.equals(me, task.getAssigneeId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅任务创建人或负责人可催办");
        }
        if (task.getAssigneeId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "任务暂无负责人，无法催办");
        }
        String content = "任务「" + task.getTaskTitle() + "」计划完成时间 "
                + (task.getPlanEnd() == null ? "未设置" : task.getPlanEnd()) + "，请及时处理";
        CollMessage message = notify(task.getAssigneeId(), me, "任务提醒：" + task.getTaskNo(), content, "REMIND", id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", id);
        result.put("messageId", message.getId());
        result.put("receiverId", task.getAssigneeId());
        result.put("messageType", message.getMessageType());
        result.put("bizType", message.getBizType());
        return result;
    }

    // ---------------------------------------------------------------- helpers

    private void applyScope(LambdaQueryWrapper<CollTask> wrapper, DataScopeInfo info) {
        if (info.isFull()) {
            return;
        }
        Long me = lookup.currentUserId();
        if (info.isSelfOnly()) {
            wrapper.and(w -> w.eq(CollTask::getAssigneeId, me).or().eq(CollTask::getCreatorId, me));
        } else if (info.isDeptRestricted()) {
            List<Long> deptIds = info.getDeptIds();
            wrapper.and(w -> w.in(CollTask::getDeptId, deptIds)
                    .or().eq(CollTask::getAssigneeId, me)
                    .or().eq(CollTask::getCreatorId, me));
        }
    }

    private CollTask requireVisible(Long id) {
        CollTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        DataScopeInfo info = dataScopeService.current();
        Long me = lookup.currentUserId();
        boolean visible = info.isFull()
                || Objects.equals(task.getCreatorId(), me)
                || Objects.equals(task.getAssigneeId(), me)
                || (info.isDeptRestricted() && task.getDeptId() != null && info.getDeptIds().contains(task.getDeptId()));
        if (!visible) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无数据权限访问该记录");
        }
        return task;
    }

    private boolean isTerminal(CollTask task) {
        return Objects.equals(task.getStatus(), STATUS_DONE) || Objects.equals(task.getStatus(), STATUS_CANCELLED);
    }

    private void validateNewParent(CollTask task, Long newParentId) {
        if (newParentId == 0L) {
            return;
        }
        CollTask parent = taskMapper.selectById(newParentId);
        if (parent == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "父任务不存在");
        }
        if (Objects.equals(newParentId, task.getId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "父任务不能是自身");
        }
        CollTask cursor = parent;
        int guard = 0;
        while (cursor != null && cursor.getParentId() != null && cursor.getParentId() > 0) {
            if (Objects.equals(cursor.getParentId(), task.getId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "不能将任务移动到其子任务下");
            }
            cursor = taskMapper.selectById(cursor.getParentId());
            if (++guard > 32) {
                break;
            }
        }
    }

    private void fillList(List<CollTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        Set<Long> userIds = new java.util.HashSet<>();
        Set<Long> deptIds = new java.util.HashSet<>();
        Set<Long> parentIds = new java.util.HashSet<>();
        for (CollTask task : tasks) {
            if (task.getAssigneeId() != null) {
                userIds.add(task.getAssigneeId());
            }
            if (task.getCreatorId() != null) {
                userIds.add(task.getCreatorId());
            }
            if (task.getDeptId() != null) {
                deptIds.add(task.getDeptId());
            }
            if (task.getParentId() != null && task.getParentId() > 0) {
                parentIds.add(task.getParentId());
            }
        }
        Map<Long, String> userNames = lookup.userNames(userIds);
        Map<Long, String> deptNames = lookup.deptNames(deptIds);
        Map<Long, String> parentTitles = new HashMap<>();
        if (!parentIds.isEmpty()) {
            for (CollTask parent : taskMapper.selectBatchIds(parentIds)) {
                parentTitles.put(parent.getId(), parent.getTaskTitle());
            }
        }
        LocalDate today = LocalDate.now();
        for (CollTask task : tasks) {
            task.setAssigneeName(userNames.get(task.getAssigneeId()));
            task.setCreatorName(userNames.get(task.getCreatorId()));
            task.setDeptName(deptNames.get(task.getDeptId()));
            task.setParentTaskTitle(parentTitles.get(task.getParentId()));
            task.setOverdue(isOverdue(task, today));
        }
    }

    private void fillOne(CollTask task) {
        List<CollTask> one = new ArrayList<>();
        one.add(task);
        fillList(one);
    }

    // package-private for unit tests (M2d overdue rule)
    boolean isOverdue(CollTask task, LocalDate today) {
        return task.getPlanEnd() != null && task.getPlanEnd().isBefore(today)
                && (Objects.equals(task.getStatus(), STATUS_TODO) || Objects.equals(task.getStatus(), STATUS_DOING));
    }

    private CollMessage notify(Long receiverId, Long senderId, String title, String content, String bizType, Long bizId) {
        if (receiverId == null) {
            return null;
        }
        CollMessage message = new CollMessage();
        message.setMessageType(3);
        message.setTitle(truncate(title, 200));
        message.setContent(truncate(content, 1000));
        message.setBizType(bizType);
        message.setBizId(bizId);
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setReadFlag(0);
        messageMapper.insert(message);
        return message;
    }

    private Map<String, Object> stateResult(CollTask task, String extraKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", task.getId());
        result.put("taskNo", task.getTaskNo());
        result.put("status", task.getStatus());
        if (extraKey != null) {
            result.put(extraKey, "actualStart".equals(extraKey) ? task.getActualStart() : task.getActualEnd());
        }
        return result;
    }

    private Long defaultDept(Long assigneeId) {
        Long deptId = lookup.userDeptId(assigneeId);
        return deptId != null ? deptId : lookup.currentDeptId();
    }

    private int requireRange(int value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, label + "取值不合法");
        }
        return value;
    }

    private String requireText(String value, String label, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, label + "不能为空");
        }
        String text = value.trim();
        if (text.length() > maxLength) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, label + "长度不能超过 " + maxLength);
        }
        return text;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

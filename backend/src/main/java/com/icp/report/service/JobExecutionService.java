package com.icp.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.icp.collab.entity.CollMessage;
import com.icp.collab.entity.CollNotice;
import com.icp.collab.entity.CollTask;
import com.icp.collab.mapper.CollabMessageMapper;
import com.icp.collab.mapper.CollNoticeMapper;
import com.icp.collab.mapper.CollTaskMapper;
import com.icp.collab.service.MessageService;
import com.icp.common.security.SecurityUtils;
import com.icp.common.security.UserPrincipal;
import com.icp.icp.entity.IcpEvaluationTask;
import com.icp.icp.entity.IcpPolicy;
import com.icp.icp.entity.IcpRectify;
import com.icp.icp.mapper.IcpEvaluationTaskMapper;
import com.icp.icp.mapper.IcpPolicyMapper;
import com.icp.icp.mapper.IcpRectifyMapper;
import com.icp.system.entity.SysJob;
import com.icp.system.entity.SysOperLog;
import com.icp.system.mapper.SysOperLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M2e 定时任务执行器（D40）：5 个 handler，命中写 coll_message(type=3) + sys_oper_log(module=定时任务)，
 * 同一 (receiver_id,biz_type,biz_id,当天) 去重；手动与 @Scheduled 共用同一套逻辑。
 */
@Service
public class JobExecutionService {

    public static final String TARGET_POLICY_EXPIRE = "policyExpireJob.scan";
    public static final String TARGET_EVAL_TASK_OVERDUE = "evaluationTaskJob.scanOverdue";
    public static final String TARGET_COLL_TASK_REMIND = "collTaskJob.remind";
    public static final String TARGET_RECTIFY_OVERDUE = "defectJob.remindOverdue";
    public static final String TARGET_NOTICE_PUBLISH = "noticeJob.publishScheduled";
    public static final String BIZ_TYPE_ALERT = "ALERT";
    public static final String BIZ_TYPE_REMIND = "REMIND";
    private static final String JOB_MODULE = "定时任务";
    private static final int MESSAGE_TYPE_TASK = 3;
    private static final int ALERT_WINDOW_DAYS = 30;
    private static final int TASK_REMIND_WINDOW_DAYS = 3;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Logger log = LoggerFactory.getLogger(JobExecutionService.class);

    private final MessageService messageService;
    private final CollabMessageMapper messageMapper;
    private final SysOperLogMapper operLogMapper;
    private final IcpPolicyMapper policyMapper;
    private final IcpEvaluationTaskMapper evaluationTaskMapper;
    private final CollTaskMapper taskMapper;
    private final IcpRectifyMapper rectifyMapper;
    private final CollNoticeMapper noticeMapper;

    public JobExecutionService(MessageService messageService,
                               CollabMessageMapper messageMapper,
                               SysOperLogMapper operLogMapper,
                               IcpPolicyMapper policyMapper,
                               IcpEvaluationTaskMapper evaluationTaskMapper,
                               CollTaskMapper taskMapper,
                               IcpRectifyMapper rectifyMapper,
                               CollNoticeMapper noticeMapper) {
        this.messageService = messageService;
        this.messageMapper = messageMapper;
        this.operLogMapper = operLogMapper;
        this.policyMapper = policyMapper;
        this.evaluationTaskMapper = evaluationTaskMapper;
        this.taskMapper = taskMapper;
        this.rectifyMapper = rectifyMapper;
        this.noticeMapper = noticeMapper;
    }

    /** 执行一次 job；status=0 时返回 skipped=true 不执行（R14）。 */
    public Map<String, Object> run(SysJob job, boolean manual) {
        long start = System.currentTimeMillis();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId", job.getId());
        data.put("jobName", job.getJobName());
        if (job.getStatus() != null && job.getStatus() == 0) {
            data.put("executed", false);
            data.put("skipped", true);
            data.put("matched", 0);
            data.put("deduped", 0);
            data.put("message", "任务已停用(status=0)，未执行");
            data.put("lastRunTime", LocalDateTime.now().format(TIME_FORMAT));
            writeOperLog(job, manual, false, data, System.currentTimeMillis() - start, null);
            return data;
        }
        HandlerResult result;
        try {
            result = dispatch(job);
        } catch (RuntimeException ex) {
            log.warn("job {} failed: {}", job.getJobName(), ex.getMessage());
            data.put("executed", false);
            data.put("skipped", false);
            data.put("matched", 0);
            data.put("deduped", 0);
            data.put("message", "执行失败: " + ex.getMessage());
            data.put("lastRunTime", LocalDateTime.now().format(TIME_FORMAT));
            writeOperLog(job, manual, true, data, System.currentTimeMillis() - start, ex.getMessage());
            return data;
        }
        data.put("executed", true);
        data.put("skipped", false);
        data.put("matched", result.matched);
        data.put("deduped", result.deduped);
        data.put("messages", result.messages);
        data.put("message", result.summary());
        data.put("lastRunTime", LocalDateTime.now().format(TIME_FORMAT));
        writeOperLog(job, manual, true, data, System.currentTimeMillis() - start, null);
        return data;
    }

    private HandlerResult dispatch(SysJob job) {
        String target = job.getInvokeTarget();
        if (target == null || target.isBlank()) {
            target = job.getJobName();
        }
        if (TARGET_POLICY_EXPIRE.equals(target)) {
            return scanPolicyExpire();
        }
        if (TARGET_EVAL_TASK_OVERDUE.equals(target)) {
            return scanEvaluationTaskOverdue();
        }
        if (TARGET_COLL_TASK_REMIND.equals(target)) {
            return remindCollTasks();
        }
        if (TARGET_RECTIFY_OVERDUE.equals(target)) {
            return remindRectifyOverdue();
        }
        if (TARGET_NOTICE_PUBLISH.equals(target)) {
            return publishScheduledNotices();
        }
        return new HandlerResult(0, 0, 0, "未识别的执行目标: " + target);
    }

    /** 制度到期扫描：生效中且 expire_date ∈ [今天, 今天+30 天]。 */
    private HandlerResult scanPolicyExpire() {
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(ALERT_WINDOW_DAYS);
        List<IcpPolicy> policies = policyMapper.selectList(new LambdaQueryWrapper<IcpPolicy>()
                .eq(IcpPolicy::getStatus, 2)
                .isNotNull(IcpPolicy::getExpireDate)
                .ge(IcpPolicy::getExpireDate, today)
                .le(IcpPolicy::getExpireDate, deadline));
        int matched = 0;
        int deduped = 0;
        int messages = 0;
        for (IcpPolicy policy : policies) {
            matched++;
            Long receiver = policy.getOwnerUserId() == null ? 1L : policy.getOwnerUserId();
            String title = "制度到期提醒";
            String content = "制度《" + policy.getPolicyName() + "》(" + policy.getPolicyCode()
                    + ") 将于 " + policy.getExpireDate() + " 到期，请及时修订或重新发布。";
            if (dedupedBefore(receiver, BIZ_TYPE_ALERT, policy.getId())) {
                deduped++;
                continue;
            }
            messageService.push(receiver, 1L, MESSAGE_TYPE_TASK, title, content, BIZ_TYPE_ALERT, policy.getId());
            messages++;
        }
        return new HandlerResult(matched, deduped, messages, null);
    }

    /** 评价任务逾期扫描：status∈(0,1) 且 due_date < 今天。 */
    private HandlerResult scanEvaluationTaskOverdue() {
        LocalDate today = LocalDate.now();
        List<IcpEvaluationTask> tasks = evaluationTaskMapper.selectList(new LambdaQueryWrapper<IcpEvaluationTask>()
                .in(IcpEvaluationTask::getStatus, 0, 1)
                .isNotNull(IcpEvaluationTask::getDueDate)
                .lt(IcpEvaluationTask::getDueDate, today));
        int matched = 0;
        int deduped = 0;
        int messages = 0;
        for (IcpEvaluationTask task : tasks) {
            matched++;
            Long receiver = task.getEvaluatorId() == null ? 1L : task.getEvaluatorId();
            String title = "评价任务逾期提醒";
            String content = "评价任务《" + task.getTaskName() + "》(" + task.getTaskCode()
                    + ") 已于 " + task.getDueDate() + " 逾期，请尽快完成。";
            if (dedupedBefore(receiver, BIZ_TYPE_ALERT, task.getId())) {
                deduped++;
                continue;
            }
            messageService.push(receiver, 1L, MESSAGE_TYPE_TASK, title, content, BIZ_TYPE_ALERT, task.getId());
            messages++;
        }
        return new HandlerResult(matched, deduped, messages, null);
    }

    /** 任务到期提醒：status∈(0,1) 且 plan_end ∈ [今天, 今天+3 天]。 */
    private HandlerResult remindCollTasks() {
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(TASK_REMIND_WINDOW_DAYS);
        List<CollTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<CollTask>()
                .in(CollTask::getStatus, 0, 1)
                .isNotNull(CollTask::getPlanEnd)
                .ge(CollTask::getPlanEnd, today)
                .le(CollTask::getPlanEnd, deadline));
        int matched = 0;
        int deduped = 0;
        int messages = 0;
        for (CollTask task : tasks) {
            matched++;
            Long receiver = task.getAssigneeId() == null ? 1L : task.getAssigneeId();
            String title = "任务到期提醒";
            String content = "任务《" + task.getTaskTitle() + "》(" + task.getTaskNo()
                    + ") 将于 " + task.getPlanEnd() + " 到期，请及时处理。";
            if (dedupedBefore(receiver, BIZ_TYPE_REMIND, task.getId())) {
                deduped++;
                continue;
            }
            messageService.push(receiver, 1L, MESSAGE_TYPE_TASK, title, content, BIZ_TYPE_REMIND, task.getId());
            messages++;
        }
        return new HandlerResult(matched, deduped, messages, null);
    }

    /** 缺陷整改超期提醒：整改 status∈(0,1) 且 deadline < 今天。 */
    private HandlerResult remindRectifyOverdue() {
        LocalDate today = LocalDate.now();
        List<IcpRectify> rectifies = rectifyMapper.selectList(new LambdaQueryWrapper<IcpRectify>()
                .in(IcpRectify::getStatus, 0, 1)
                .isNotNull(IcpRectify::getDeadline)
                .lt(IcpRectify::getDeadline, today));
        int matched = 0;
        int deduped = 0;
        int messages = 0;
        for (IcpRectify rectify : rectifies) {
            matched++;
            Long receiver = rectify.getResponsibleUserId() == null ? 1L : rectify.getResponsibleUserId();
            String title = "整改超期提醒";
            String content = "整改单 " + rectify.getRectifyCode() + " 已于 " + rectify.getDeadline()
                    + " 超期未完成，请尽快整改并提交复核。";
            if (dedupedBefore(receiver, BIZ_TYPE_ALERT, rectify.getId())) {
                deduped++;
                continue;
            }
            messageService.push(receiver, 1L, MESSAGE_TYPE_TASK, title, content, BIZ_TYPE_ALERT, rectify.getId());
            messages++;
        }
        return new HandlerResult(matched, deduped, messages, null);
    }

    /** 公告定时发布：草稿(status=0) 且 publish_time <= 现在 → 置为已发布并通知发布人。 */
    private HandlerResult publishScheduledNotices() {
        LocalDateTime now = LocalDateTime.now();
        List<CollNotice> notices = noticeMapper.selectList(new LambdaQueryWrapper<CollNotice>()
                .eq(CollNotice::getStatus, 0)
                .isNotNull(CollNotice::getPublishTime)
                .le(CollNotice::getPublishTime, now));
        int matched = 0;
        int deduped = 0;
        int messages = 0;
        for (CollNotice notice : notices) {
            matched++;
            noticeMapper.update(null, new LambdaUpdateWrapper<CollNotice>()
                    .eq(CollNotice::getId, notice.getId())
                    .eq(CollNotice::getStatus, 0)
                    .set(CollNotice::getStatus, 1)
                    .set(CollNotice::getPublishTime, now));
            Long receiver = notice.getPublisherId() == null ? 1L : notice.getPublisherId();
            String title = "公告已发布";
            String content = "公告《" + notice.getNoticeTitle() + "》已按计划定时发布。";
            if (dedupedBefore(receiver, BIZ_TYPE_ALERT, notice.getId())) {
                deduped++;
                continue;
            }
            messageService.push(receiver, 1L, MESSAGE_TYPE_TASK, title, content, BIZ_TYPE_ALERT, notice.getId());
            messages++;
        }
        return new HandlerResult(matched, deduped, messages, null);
    }

    private boolean dedupedBefore(Long receiverId, String bizType, Long bizId) {
        if (receiverId == null || bizId == null) {
            return false;
        }
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<CollMessage>()
                .eq(CollMessage::getReceiverId, receiverId)
                .eq(CollMessage::getBizType, bizType)
                .eq(CollMessage::getBizId, bizId)
                .apply("DATE(create_time) = CURDATE()"));
        return count != null && count > 0;
    }

    private void writeOperLog(SysJob job, boolean manual, boolean executed, Map<String, Object> data,
                              long costMs, String errorMsg) {
        try {
            SysOperLog operLog = new SysOperLog();
            operLog.setTitle(JOB_MODULE + "-" + job.getJobName());
            operLog.setBusinessType("sys_job");
            operLog.setMethod("JobExecutionService.run");
            operLog.setRequestMethod(manual ? "POST" : "SCHEDULE");
            operLog.setRequestUri(manual ? "/api/v1/jobs/" + job.getId() + "/run" : "scheduled://jobs");
            operLog.setRequestParam("{\"jobId\":" + job.getId() + ",\"invokeTarget\":\""
                    + job.getInvokeTarget() + "\",\"matched\":" + data.get("matched")
                    + ",\"deduped\":" + data.get("deduped") + ",\"executed\":" + executed + "}");
            operLog.setStatus(errorMsg == null ? 0 : 1);
            operLog.setErrorMsg(errorMsg);
            UserPrincipal principal = SecurityUtils.currentUserOrNull();
            if (principal != null) {
                operLog.setOperUserId(principal.getUserId());
                operLog.setOperName(principal.getUsername());
                operLog.setDeptId(principal.getDeptId());
            } else {
                operLog.setOperName("system");
            }
            // trace: manual run reuses MDC traceId from HTTP request; scheduled thread generates a 32-hex id (R19/V45)
            String traceId = MDC.get("traceId");
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString().replace("-", "");
                log.info("job {} ({}) generated traceId={}", job.getId(), job.getJobName(), traceId);
            }
            operLog.setTraceId(traceId);
            operLog.setCostMs((int) Math.min(costMs, Integer.MAX_VALUE));
            operLog.setOperTime(LocalDateTime.now());
            operLogMapper.insert(operLog);
        } catch (Exception ex) {
            log.warn("job oper log skipped: {}", ex.getMessage());
        }
    }

    private static final class HandlerResult {
        private final int matched;
        private final int deduped;
        private final int messages;
        private final String message;

        private HandlerResult(int matched, int deduped, int messages, String message) {
            this.matched = matched;
            this.deduped = deduped;
            this.messages = messages;
            this.message = message;
        }

        private String summary() {
            if (message != null) {
                return message;
            }
            return "命中 " + matched + " 条，去重 " + deduped + " 条，发送 " + messages + " 条消息";
        }
    }
}

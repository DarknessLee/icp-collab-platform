package com.icp.report.job;

import com.icp.report.service.JobExecutionService;
import com.icp.system.entity.SysJob;
import com.icp.system.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * M2e D40：进程内定时扫描（fixedDelay=10 分钟）。读取 sys_job(status=1)，按 invoke_target 映射 5 个 handler，
 * 命中写 coll_message(type=3) + sys_oper_log（module=定时任务），同一 (receiver_id,biz_type,biz_id,当天) 去重。
 * 不引入 XXL-Job / Quartz 等外部调度。
 */
@Component
public class JobScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    private final JobService jobService;
    private final JobExecutionService jobExecutionService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${report.scheduler.enabled:true}")
    private boolean enabled = true;

    public JobScheduler(JobService jobService, JobExecutionService jobExecutionService) {
        this.jobService = jobService;
        this.jobExecutionService = jobExecutionService;
    }

    @Scheduled(fixedDelay = 600_000L, initialDelay = 60_000L)
    public void scan() {
        if (!enabled) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("job scheduler skipped: previous scan still running");
            return;
        }
        try {
            for (SysJob job : jobService.listEnabled()) {
                Map<String, Object> result = jobExecutionService.run(job, false);
                jobService.recordRun(job.getId(), result);
            }
        } catch (RuntimeException ex) {
            log.warn("job scheduler scan failed: {}", ex.getMessage());
        } finally {
            running.set(false);
        }
    }
}

package com.icp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.exception.BusinessException;
import com.icp.system.entity.SysJob;
import com.icp.system.mapper.SysJobMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定时任务管理（M2e 起升级为真实执行：{@link JobScheduler} 每 10 分钟扫描 status=1 的任务，
 * 手动触发走 {@code POST /jobs/{id}/run}，两者共用 {@code JobExecutionService} 的 handler）。
 * 最近执行结果保存在进程内（D40/M2e，不落新增表）。
 */
@Service
public class JobService {

    private final SysJobMapper jobMapper;
    private final Map<Long, Map<String, Object>> lastRuns = new ConcurrentHashMap<>();

    public JobService(SysJobMapper jobMapper) {
        this.jobMapper = jobMapper;
    }

    public PageResult<Map<String, Object>> page(long pageNum, long pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<SysJob> wrapper = new LambdaQueryWrapper<SysJob>().orderByAsc(SysJob::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(SysJob::getJobName, keyword.trim())
                    .or().like(SysJob::getJobGroup, keyword.trim())
                    .or().like(SysJob::getInvokeTarget, keyword.trim()));
        }
        if (status != null) {
            wrapper.eq(SysJob::getStatus, status);
        }
        Page<SysJob> page = jobMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<Map<String, Object>> records = new ArrayList<>();
        for (SysJob job : page.getRecords()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", job.getId());
            view.put("jobName", job.getJobName());
            view.put("jobGroup", job.getJobGroup());
            view.put("cronExpression", job.getCronExpression());
            view.put("invokeTarget", job.getInvokeTarget());
            view.put("status", job.getStatus());
            view.put("remark", job.getRemark());
            Map<String, Object> last = lastRuns.get(job.getId());
            view.put("lastRunTime", last == null ? null : last.get("lastRunTime"));
            view.put("lastResult", last == null ? null : last.get("message"));
            records.add(view);
        }
        return new PageResult<>(records, page.getTotal(), pageNum, pageSize);
    }

    public SysJob get(Long id) {
        SysJob job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        return job;
    }

    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "任务状态仅支持 0 停止 / 1 运行");
        }
        SysJob job = get(id);
        job.setStatus(status);
        jobMapper.updateById(job);
    }

    /** 调度扫描用：仅执行启用中的任务。 */
    public List<SysJob> listEnabled() {
        return jobMapper.selectList(new LambdaQueryWrapper<SysJob>()
                .eq(SysJob::getStatus, 1)
                .orderByAsc(SysJob::getId));
    }

    public void recordRun(Long jobId, Map<String, Object> result) {
        lastRuns.put(jobId, new LinkedHashMap<>(result));
    }

    public Map<String, Object> lastRun(Long jobId) {
        return lastRuns.get(jobId);
    }
}

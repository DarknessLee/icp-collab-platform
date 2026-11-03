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

/**
 * 定时任务 M2a 仅提供管理与接口占位：不接入调度器（M2 后续阶段接入）。
 */
@Service
public class JobService {

    public static final String NOT_SCHEDULED_NOTE = "M2a 仅接口未接入调度";

    private final SysJobMapper jobMapper;

    public JobService(SysJobMapper jobMapper) {
        this.jobMapper = jobMapper;
    }

    public PageResult<SysJob> page(long pageNum, long pageSize, String keyword, Integer status) {
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
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
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

    public SysJob run(Long id) {
        SysJob job = get(id);
        return job;
    }
}

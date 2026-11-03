package com.icp.icp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.icp.entity.IcpEvaluationTask;
import org.apache.ibatis.annotations.Select;

/**
 * 评价任务 Mapper（编码规则 {plan_code}-##，docs/04 §5）。
 */
public interface IcpEvaluationTaskMapper extends BaseMapper<IcpEvaluationTask> {

    @Select("SELECT MAX(task_code) FROM icp_evaluation_task WHERE plan_id=#{planId}")
    String selectMaxCodeByPlan(Long planId);

    @Select("SELECT COUNT(*) FROM icp_evaluation_task WHERE deleted=0 AND plan_id=#{planId}")
    long countByPlan(Long planId);
}

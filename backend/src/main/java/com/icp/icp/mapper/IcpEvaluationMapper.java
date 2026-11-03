package com.icp.icp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.icp.entity.IcpEvaluation;
import org.apache.ibatis.annotations.Select;

/**
 * 评价计划 Mapper（编码 PJ-yyyy-##，docs/04 §5）。
 */
public interface IcpEvaluationMapper extends BaseMapper<IcpEvaluation> {

    @Select("SELECT MAX(plan_code) FROM icp_evaluation")
    String selectMaxCode();
}

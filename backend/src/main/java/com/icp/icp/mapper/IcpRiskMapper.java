package com.icp.icp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.icp.entity.IcpRisk;
import org.apache.ibatis.annotations.Select;

/**
 * 风险 Mapper（编码 FX-yyyy-###，docs/04 §5）。
 */
public interface IcpRiskMapper extends BaseMapper<IcpRisk> {

    @Select("SELECT MAX(risk_code) FROM icp_risk")
    String selectMaxCode();

    @Select("SELECT COUNT(*) FROM icp_matrix WHERE deleted=0 AND risk_id=#{riskId}")
    long countMatrixRefs(Long riskId);
}

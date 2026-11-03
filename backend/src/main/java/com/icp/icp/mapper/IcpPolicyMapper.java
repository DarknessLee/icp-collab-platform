package com.icp.icp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.icp.entity.IcpPolicy;
import org.apache.ibatis.annotations.Select;

/**
 * 制度 Mapper，附带编码取最大值（编码规则 ZD-yyyy-####，见 docs/04 §5）。
 */
public interface IcpPolicyMapper extends BaseMapper<IcpPolicy> {

    @Select("SELECT MAX(policy_code) FROM icp_policy")
    String selectMaxCode();

    @Select("SELECT COUNT(*) FROM icp_matrix WHERE deleted=0 AND policy_id=#{policyId}")
    long countMatrixRefs(Long policyId);
}

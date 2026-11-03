package com.icp.icp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.icp.entity.IcpControl;
import org.apache.ibatis.annotations.Select;

/**
 * 控制点 Mapper（编码 KZ-yyyy-###，docs/04 §5）。
 */
public interface IcpControlMapper extends BaseMapper<IcpControl> {

    @Select("SELECT MAX(control_code) FROM icp_control")
    String selectMaxCode();

    @Select("SELECT COUNT(*) FROM icp_matrix WHERE deleted=0 AND control_id=#{controlId}")
    long countMatrixRefs(Long controlId);
}

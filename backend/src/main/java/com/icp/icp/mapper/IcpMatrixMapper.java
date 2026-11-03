package com.icp.icp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.icp.entity.IcpMatrix;
import org.apache.ibatis.annotations.Select;

/**
 * 矩阵 Mapper（编码 JC-yyyy-###，docs/04 §5）。
 */
public interface IcpMatrixMapper extends BaseMapper<IcpMatrix> {

    @Select("SELECT MAX(matrix_code) FROM icp_matrix")
    String selectMaxCode();
}

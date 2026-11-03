package com.icp.icp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.icp.entity.IcpRectify;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 整改 Mapper（编码 ZG-yyyy-###，docs/04 §5）。
 */
public interface IcpRectifyMapper extends BaseMapper<IcpRectify> {

    @Select("SELECT MAX(rectify_code) FROM icp_rectify")
    String selectMaxCode();

    @Select("SELECT COUNT(*) FROM icp_rectify WHERE deleted=0 AND defect_id=#{defectId} AND status IN (0,1,3)")
    long countOpenByDefect(@Param("defectId") Long defectId);

    @Select("SELECT COUNT(*) FROM icp_rectify WHERE deleted=0 AND defect_id=#{defectId} AND status=2")
    long countApprovedByDefect(@Param("defectId") Long defectId);
}

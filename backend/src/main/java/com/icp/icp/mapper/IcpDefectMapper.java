package com.icp.icp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.icp.entity.IcpDefect;
import org.apache.ibatis.annotations.Select;

/**
 * 缺陷 Mapper（编码 QX-yyyy-###，docs/04 §5）。
 */
public interface IcpDefectMapper extends BaseMapper<IcpDefect> {

    @Select("SELECT MAX(defect_code) FROM icp_defect")
    String selectMaxCode();
}

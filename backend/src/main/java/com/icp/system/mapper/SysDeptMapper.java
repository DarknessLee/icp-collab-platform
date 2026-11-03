package com.icp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.system.entity.SysDept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {

    /**
     * 取自动编码 D + 序号的最大值（含逻辑删除行，避免与唯一索引 uk_dept_code 冲突）。
     */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(dept_code, 2) AS UNSIGNED)), 0) "
            + "FROM sys_dept WHERE dept_code REGEXP '^D[0-9]{1,6}$'")
    long selectMaxAutoCode();
}

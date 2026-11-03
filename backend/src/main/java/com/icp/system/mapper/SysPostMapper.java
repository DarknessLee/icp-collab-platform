package com.icp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.system.entity.SysPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysPostMapper extends BaseMapper<SysPost> {

    /**
     * 取自动编码 P + 序号的最大值（含逻辑删除行，避免与唯一索引 uk_post_code 冲突）。
     */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(post_code, 2) AS UNSIGNED)), 0) "
            + "FROM sys_post WHERE post_code REGEXP '^P[0-9]{1,6}$'")
    long selectMaxAutoCode();

    /**
     * 按编码查询（包含逻辑删除行），用于识别“历史记录占用了唯一编码”的场景。
     */
    @Select("SELECT id, post_code, post_name, order_num, status, remark, deleted "
            + "FROM sys_post WHERE post_code = #{code} LIMIT 1")
    SysPost selectByCodeIncludeDeleted(@Param("code") String code);

    /**
     * 恢复被逻辑删除的历史岗位（同一编码再次新增时复用该行，保证接口幂等不 500）。
     */
    @Update("UPDATE sys_post SET post_name = #{postName}, order_num = #{orderNum}, status = #{status}, "
            + "remark = #{remark}, deleted = 0, update_by = #{updateBy}, update_time = NOW() WHERE id = #{id}")
    int revive(@Param("id") Long id,
               @Param("postName") String postName,
               @Param("orderNum") Integer orderNum,
               @Param("status") Integer status,
               @Param("remark") String remark,
               @Param("updateBy") Long updateBy);
}

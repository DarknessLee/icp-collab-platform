package com.icp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("SELECT r.* FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} AND r.deleted = 0 AND r.status = 1 ORDER BY r.role_sort, r.id")
    List<SysRole> selectByUserId(@Param("userId") Long userId);

    /**
     * 按角色编码查询（包含逻辑删除行），用于唯一编码被历史记录占用时复用该行。
     */
    @Select("SELECT id, role_code, role_name, role_sort, data_scope, status, remark, deleted "
            + "FROM sys_role WHERE role_code = #{roleCode} LIMIT 1")
    SysRole selectByCodeIncludeDeleted(@Param("roleCode") String roleCode);

    /**
     * 恢复被逻辑删除的历史角色（同一编码再次新增时复用该行，保证接口幂等）。
     */
    @Update("UPDATE sys_role SET role_name = #{r.roleName}, role_sort = #{r.roleSort}, "
            + "data_scope = #{r.dataScope}, status = #{r.status}, remark = #{r.remark}, "
            + "deleted = 0, update_by = #{updateBy}, update_time = NOW() WHERE id = #{r.id}")
    int reviveRole(@Param("r") SysRole role, @Param("updateBy") Long updateBy);
}

package com.icp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 按用户名查询（包含逻辑删除行），用于识别“历史记录占用了唯一用户名”的场景。
     */
    @Select("SELECT id, username, password, real_name, dept_id, post_id, status, deleted "
            + "FROM sys_user WHERE username = #{username} LIMIT 1")
    SysUser selectByUsernameIncludeDeleted(@Param("username") String username);

    /**
     * 恢复被逻辑删除的历史用户（同一用户名再次新增时复用该行，保持接口幂等不 500）。
     */
    @Update("UPDATE sys_user SET password = #{u.password}, real_name = #{u.realName}, "
            + "dept_id = #{u.deptId}, post_id = #{u.postId}, gender = COALESCE(#{u.gender}, 0), "
            + "mobile = #{u.mobile}, email = #{u.email}, avatar = #{u.avatar}, status = #{u.status}, "
            + "fail_count = 0, lock_until = NULL, login_ip = NULL, login_time = NULL, remark = #{u.remark}, "
            + "pwd_update_time = NOW(), deleted = 0, update_by = #{updateBy}, update_time = NOW() "
            + "WHERE id = #{u.id}")
    int reviveUser(@Param("u") SysUser user, @Param("updateBy") Long updateBy);
}

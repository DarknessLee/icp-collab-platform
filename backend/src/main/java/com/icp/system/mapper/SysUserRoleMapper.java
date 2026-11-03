package com.icp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 按角色编码查询首个有效用户（流程催办定位候选人时使用）。
     */
    @Select("<script>SELECT ur.user_id FROM sys_user_role ur "
            + "JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 "
            + "JOIN sys_user u ON u.id = ur.user_id AND u.deleted = 0 AND u.status = 1 "
            + "WHERE r.role_code IN "
            + "<foreach collection='codes' item='code' open='(' separator=',' close=')'>#{code}</foreach> "
            + "ORDER BY ur.user_id LIMIT 1</script>")
    Long selectFirstUserIdByRoleCodes(@Param("codes") List<String> codes);
}

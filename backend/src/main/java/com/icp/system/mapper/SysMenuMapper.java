package com.icp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.icp.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    @Select("<script>"
            + "SELECT DISTINCT m.* FROM sys_menu m "
            + "JOIN sys_role_menu rm ON rm.menu_id = m.id "
            + "WHERE m.deleted = 0 AND m.status = 1 AND rm.role_id IN "
            + "<foreach collection='roleIds' item='roleId' open='(' separator=',' close=')'>#{roleId}</foreach> "
            + "ORDER BY m.order_num, m.id"
            + "</script>")
    List<SysMenu> selectByRoleIds(@Param("roleIds") List<Long> roleIds);
}

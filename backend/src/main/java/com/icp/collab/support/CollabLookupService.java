package com.icp.collab.support;

import com.icp.common.exception.BusinessException;
import com.icp.common.api.ErrorCode;
import com.icp.common.security.SecurityUtils;
import com.icp.common.security.UserPrincipal;
import com.icp.system.entity.SysDept;
import com.icp.system.entity.SysRole;
import com.icp.system.entity.SysUser;
import com.icp.system.mapper.SysDeptMapper;
import com.icp.system.mapper.SysRoleMapper;
import com.icp.system.mapper.SysUserMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 协同域通用查询：当前用户信息 + 用户名/部门名/角色名批量解析（列表填充用）。
 */
@Component
public class CollabLookupService {

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;

    public CollabLookupService(SysUserMapper userMapper, SysDeptMapper deptMapper, SysRoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.roleMapper = roleMapper;
    }

    public Long currentUserId() {
        return SecurityUtils.currentUser().getUserId();
    }

    public UserPrincipal currentUser() {
        return SecurityUtils.currentUser();
    }

    public String currentRealName() {
        UserPrincipal principal = SecurityUtils.currentUser();
        return StringUtils.hasText(principal.getRealName()) ? principal.getRealName() : principal.getUsername();
    }

    public Long currentDeptId() {
        UserPrincipal principal = SecurityUtils.currentUser();
        if (principal.getDeptId() != null) {
            return principal.getDeptId();
        }
        SysUser user = userMapper.selectById(principal.getUserId());
        return user == null ? null : user.getDeptId();
    }

    public List<String> currentRoles() {
        return SecurityUtils.currentUser().getRoles();
    }

    public boolean isAdmin() {
        return currentRoles().contains("ADMIN");
    }

    public List<Long> currentRoleIds() {
        return roleMapper.selectByUserId(currentUserId()).stream().map(SysRole::getId).toList();
    }

    public List<String> currentRoleCodes() {
        return roleMapper.selectByUserId(currentUserId()).stream().map(SysRole::getRoleCode).toList();
    }

    public String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = userMapper.selectById(userId);
        return user == null ? null : user.getRealName();
    }

    public Long userDeptId(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = userMapper.selectById(userId);
        return user == null ? null : user.getDeptId();
    }

    public Map<Long, String> userNames(Collection<Long> ids) {
        Map<Long, String> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        for (SysUser user : userMapper.selectBatchIds(ids)) {
            result.put(user.getId(), user.getRealName());
        }
        return result;
    }

    public String deptName(Long deptId) {
        if (deptId == null) {
            return null;
        }
        SysDept dept = deptMapper.selectById(deptId);
        return dept == null ? null : dept.getDeptName();
    }

    public Map<Long, String> deptNames(Collection<Long> ids) {
        Map<Long, String> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        for (SysDept dept : deptMapper.selectBatchIds(ids)) {
            result.put(dept.getId(), dept.getDeptName());
        }
        return result;
    }

    /** 某批部门下所有用户 ID（部门数据权限过滤用）。 */
    public java.util.Set<Long> userIdsOfDepts(Collection<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return java.util.Set.of();
        }
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.in(SysUser::getDeptId, deptIds);
        return userMapper.selectList(wrapper).stream().map(SysUser::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    public Map<Long, String> roleNames(Collection<Long> ids) {        Map<Long, String> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        for (SysRole role : roleMapper.selectBatchIds(ids)) {
            result.put(role.getId(), role.getRoleName());
        }
        return result;
    }

    public void requireUser(Long userId) {
        if (userId == null || userMapper.selectById(userId) == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户不存在");
        }
    }
}

package com.icp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.icp.common.api.ErrorCode;
import com.icp.common.datascope.DataScopeInfo;
import com.icp.common.exception.BusinessException;
import com.icp.common.security.SecurityUtils;
import com.icp.system.entity.SysDept;
import com.icp.system.entity.SysRole;
import com.icp.system.entity.SysUser;
import com.icp.system.mapper.SysDeptMapper;
import com.icp.system.mapper.SysRoleMapper;
import com.icp.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据权限解析：取当前用户所有角色的最大 data_scope（ADMIN 视为 4），
 * scope=3 时展开本部门及所有下级部门；结果放入 {@link com.icp.common.datascope.DataScopeContext}。
 */
@Service
public class DataScopeService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;

    public DataScopeService(SysUserMapper userMapper, SysRoleMapper roleMapper, SysDeptMapper deptMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.deptMapper = deptMapper;
    }

    public DataScopeInfo current() {
        Long userId = SecurityUtils.currentUserIdOrNull();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return forUser(userId);
    }

    public DataScopeInfo forUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() != 1)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或已停用");
        }
        List<SysRole> roles = roleMapper.selectByUserId(userId);
        int scope = DataScopeInfo.SELF;
        for (SysRole role : roles) {
            if ("ADMIN".equals(role.getRoleCode())) {
                scope = DataScopeInfo.ALL;
                break;
            }
            int roleScope = role.getDataScope() == null ? DataScopeInfo.SELF : role.getDataScope();
            if (roleScope == 5) {
                // 自定义数据范围本期不启用，按本部门处理（避免越权放大）
                roleScope = DataScopeInfo.DEPT;
            }
            if (roleScope > scope) {
                scope = roleScope;
            }
        }
        List<Long> deptIds = List.of();
        if (scope == DataScopeInfo.DEPT || scope == DataScopeInfo.DEPT_AND_CHILD) {
            deptIds = resolveDeptIds(user.getDeptId(), scope == DataScopeInfo.DEPT_AND_CHILD);
        }
        return new DataScopeInfo(userId, user.getDeptId(), scope, deptIds);
    }

    private List<Long> resolveDeptIds(Long deptId, boolean includeChildren) {
        if (deptId == null) {
            return List.of();
        }
        if (!includeChildren) {
            return List.of(deptId);
        }
        List<SysDept> depts = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .select(SysDept::getId, SysDept::getParentId, SysDept::getAncestors));
        Map<Long, List<Long>> childrenIndex = new HashMap<>();
        for (SysDept dept : depts) {
            childrenIndex.computeIfAbsent(dept.getParentId() == null ? 0L : dept.getParentId(),
                    key -> new ArrayList<>()).add(dept.getId());
        }
        Set<Long> result = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(deptId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!result.add(current)) {
                continue;
            }
            for (Long child : childrenIndex.getOrDefault(current, List.of())) {
                queue.add(child);
            }
        }
        return new ArrayList<>(result);
    }

    /** 是否允许访问某条业务记录（按部门/本人）。 */
    public boolean canAccess(DataScopeInfo info, Long deptId, Long ownerUserId) {
        if (info.isFull()) {
            return true;
        }
        if (info.isSelfOnly()) {
            return ownerUserId != null && ownerUserId.equals(info.getUserId());
        }
        return deptId != null && info.getDeptIds().contains(deptId);
    }

    /** 单条记录越权时统一抛 40300，便于验收。 */
    public void checkAccess(DataScopeInfo info, Long deptId, Long ownerUserId) {
        if (!canAccess(info, deptId, ownerUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无数据权限访问该记录");
        }
    }

    /** 判断部门/用户维度是否在可见范围内（列表过滤的兜底校验用）。 */
    public boolean inScope(DataScopeInfo info, Long deptId, Long userId) {
        return canAccess(info, deptId, userId);
    }
}

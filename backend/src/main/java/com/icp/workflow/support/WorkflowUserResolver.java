package com.icp.workflow.support;

import com.icp.common.security.SecurityUtils;
import com.icp.common.security.UserPrincipal;
import com.icp.system.entity.SysDept;
import com.icp.system.entity.SysUser;
import com.icp.system.mapper.SysDeptMapper;
import com.icp.system.mapper.SysUserMapper;
import com.icp.system.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 流程参与人解析（docs/05 §4.2 流程参与人例外）。
 *
 * <p>流程任务可见性由 Flowable 候选组 / 办理人决定：候选组统一使用 {@code ROLE_<roleCode>}，
 * 与 JWT 中的角色码（ADMIN、ICP_MANAGER 等）一一对应；姓名/部门名用于列表展示。</p>
 */
@Component
public class WorkflowUserResolver {

    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public WorkflowUserResolver(SysUserMapper sysUserMapper, SysDeptMapper sysDeptMapper,
                                SysUserRoleMapper sysUserRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysDeptMapper = sysDeptMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    /**
     * 候选组任务无具体办理人时，按候选组找到一个可通知的用户（催办消息接收人）。
     */
    public Long firstUserIdOfGroups(Collection<String> roleGroups) {
        if (roleGroups == null || roleGroups.isEmpty()) {
            return null;
        }
        List<String> codes = roleGroups.stream()
                .filter(g -> g != null && !g.isBlank())
                .map(g -> g.startsWith("ROLE_") ? g.substring(5) : g)
                .toList();
        if (codes.isEmpty()) {
            return null;
        }
        return sysUserRoleMapper.selectFirstUserIdByRoleCodes(codes);
    }

    public Long currentUserId() {
        return SecurityUtils.currentUserIdOrNull();
    }

    public String currentUsername() {
        UserPrincipal principal = SecurityUtils.currentUserOrNull();
        return principal == null ? null : principal.getUsername();
    }

    public String currentRealName() {
        UserPrincipal principal = SecurityUtils.currentUserOrNull();
        return principal == null ? null : principal.getRealName();
    }

    /** 当前用户角色对应的 Flowable 候选组：ADMIN → ROLE_ADMIN。 */
    public Set<String> currentRoleGroups() {
        UserPrincipal principal = SecurityUtils.currentUserOrNull();
        Set<String> groups = new LinkedHashSet<>();
        if (principal != null && principal.getRoles() != null) {
            for (String role : principal.getRoles()) {
                if (role != null && !role.isBlank()) {
                    groups.add("ROLE_" + role.toUpperCase(Locale.ROOT));
                }
            }
        }
        return groups;
    }

    public Map<Long, String> userNames(Collection<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(ids);
        for (SysUser user : users) {
            map.put(user.getId(), user.getRealName() == null ? user.getUsername() : user.getRealName());
        }
        return map;
    }

    public Map<Long, String> deptNames(Collection<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        List<SysDept> depts = sysDeptMapper.selectBatchIds(ids);
        for (SysDept dept : depts) {
            map.put(dept.getId(), dept.getDeptName());
        }
        return map;
    }

    public String nameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return user.getRealName() == null ? user.getUsername() : user.getRealName();
    }
}

package com.icp.common.security;

import java.util.List;

/**
 * 认证后的当前用户（存放在 SecurityContext 中）。jti 用于在线会话与强制下线。
 */
public class UserPrincipal {

    private final Long userId;
    private final String username;
    private final String realName;
    private final Long deptId;
    private final String jti;
    private final List<String> roles;
    private final List<String> permissions;

    public UserPrincipal(Long userId, String username, String realName,
                         List<String> roles, List<String> permissions) {
        this(userId, username, realName, null, null, roles, permissions);
    }

    public UserPrincipal(Long userId, String username, String realName, Long deptId,
                         List<String> roles, List<String> permissions) {
        this(userId, username, realName, deptId, null, roles, permissions);
    }

    public UserPrincipal(Long userId, String username, String realName, Long deptId, String jti,
                         List<String> roles, List<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.deptId = deptId;
        this.jti = jti;
        this.roles = roles;
        this.permissions = permissions;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRealName() {
        return realName;
    }

    public Long getDeptId() {
        return deptId;
    }

    public String getJti() {
        return jti;
    }

    public List<String> getRoles() {
        return roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }
}

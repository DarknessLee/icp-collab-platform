package com.icp.common.security;

import java.util.List;

/**
 * 认证后的当前用户（存放在 SecurityContext 中）。
 */
public class UserPrincipal {

    private final Long userId;
    private final String username;
    private final String realName;
    private final List<String> roles;
    private final List<String> permissions;

    public UserPrincipal(Long userId, String username, String realName, List<String> roles, List<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.realName = realName;
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

    public List<String> getRoles() {
        return roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }
}

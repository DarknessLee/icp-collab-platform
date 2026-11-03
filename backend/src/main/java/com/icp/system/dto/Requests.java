package com.icp.system.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 系统域简单请求体集合（避免为每个小接口单独建类）。
 */
public final class Requests {

    private Requests() {
    }

    public record Status(Integer status) {
    }

    public record ResetPassword(String password) {
    }

    public record ChangePassword(@NotBlank(message = "旧密码不能为空") String oldPassword,
                                 @NotBlank(message = "新密码不能为空") String newPassword) {
    }

    public record RoleMenus(List<Long> menuIds) {
    }

    public record UserRoles(List<Long> roleIds) {
    }

    public record ConfigValue(String configValue) {
    }

    public record Profile(String realName, String email, String mobile) {
    }

    public record PageQuery(Long pageNum, Long pageSize, String keyword, Integer status, Long deptId) {
    }
}

package com.icp.common.security;

import com.icp.common.api.ErrorCode;
import com.icp.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具：统一从 SecurityContext 读取 UserPrincipal。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserPrincipal currentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    public static UserPrincipal currentUser() {
        UserPrincipal principal = currentUserOrNull();
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    public static Long currentUserIdOrNull() {
        UserPrincipal principal = currentUserOrNull();
        return principal == null ? null : principal.getUserId();
    }
}

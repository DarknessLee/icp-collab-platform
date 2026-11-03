package com.icp.system.controller;

import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.system.dto.LoginRequest;
import com.icp.system.dto.LoginResponse;
import com.icp.system.dto.MeResponse;
import com.icp.system.dto.OnlineSession;
import com.icp.system.dto.RefreshTokenRequest;
import com.icp.system.dto.Requests;
import com.icp.system.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证", description = "登录 / 当前用户 / 刷新令牌 / 退出 / 在线会话 / 修改密码")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "账号密码登录")
    @PostMapping("/login")
    @AuditLog(module = "认证", action = "用户登录", businessType = "login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request, httpRequest));
    }

    @Operation(summary = "当前登录用户信息（含角色/权限/菜单）")
    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(authService.me());
    }

    @Operation(summary = "刷新 access token")
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    @AuditLog(module = "认证", action = "退出登录", businessType = "logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.ok();
    }

    @Operation(summary = "在线会话列表")
    @GetMapping("/online")
    @PreAuthorize("hasAuthority('system:user:list')")
    public ApiResponse<PageResult<OnlineSession>> online(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword) {
        long size = Math.min(Math.max(pageSize, 1), 100);
        long page = Math.max(pageNum, 1);
        return ApiResponse.ok(authService.online(page, size, keyword));
    }

    @Operation(summary = "强制下线（按会话 jti）")
    @DeleteMapping("/online/{jti}")
    @PreAuthorize("hasAuthority('system:user:reset')")
    @AuditLog(module = "认证", action = "强制下线", businessType = "online")
    public ApiResponse<Void> kick(@PathVariable String jti) {
        authService.kick(jti);
        return ApiResponse.ok();
    }

    @Operation(summary = "修改当前用户密码")
    @PutMapping("/password")
    @AuditLog(module = "认证", action = "修改密码", businessType = "password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody Requests.ChangePassword request) {
        authService.changePassword(request);
        return ApiResponse.ok();
    }
}

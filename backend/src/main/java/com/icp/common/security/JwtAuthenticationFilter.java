package com.icp.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 Authorization: Bearer &lt;access token&gt; 恢复登录态。
 * - 权限标识（perms）转成 Spring Security authority，配合 @PreAuthorize 做接口鉴权；
 * - 已强制下线（吊销 jti）的 token 直接视为未认证；
 * - 每次请求刷新在线会话活跃时间。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final SessionRegistry sessionRegistry;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, SessionRegistry sessionRegistry) {
        this.tokenProvider = tokenProvider;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            try {
                var claims = tokenProvider.parse(token);
                String type = tokenProvider.getType(claims);
                String jti = tokenProvider.getJti(claims);
                if (JwtTokenProvider.TYPE_ACCESS.equals(type)) {
                    if (sessionRegistry != null && sessionRegistry.isRevoked(jti)) {
                        SecurityContextHolder.clearContext();
                    } else {
                        Long userId = tokenProvider.getUserId(claims);
                        String username = tokenProvider.getUsername(claims);
                        Long deptId = tokenProvider.getDeptId(claims);
                        List<String> roles = tokenProvider.getRoles(claims);
                        List<String> permissions = tokenProvider.getPermissions(claims);
                        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                        for (String role : roles) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                        }
                        for (String permission : permissions) {
                            if (StringUtils.hasText(permission)) {
                                authorities.add(new SimpleGrantedAuthority(permission));
                            }
                        }
                        UserPrincipal principal =
                                new UserPrincipal(userId, username, null, deptId, jti, roles, permissions);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        if (sessionRegistry != null) {
                            sessionRegistry.touch(jti);
                        }
                    }
                }
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}

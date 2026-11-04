package com.icp.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * SSE 通道鉴权过滤器（M2d D36）。
 *
 * <p>浏览器 EventSource 无法设置 Authorization 请求头，因此 {@code /api/v1/messages/stream}
 * 通过 {@code ?access_token=<JWT>} 传递令牌，并在 {@link SecurityConfig} 白名单中放行。
 * 白名单只含义"不要求 SecurityContext 认证"，过滤器链仍然执行；本过滤器在该路径上
 * 手工校验令牌与 jti 吊销状态，非法时直接返回 401 JSON，避免错误落到 text/event-stream
 * 的异步响应上导致 500 / "response not usable"。</p>
 */
@Component
public class SseAuthFilter extends OncePerRequestFilter {

    private static final String SSE_PATH = "/api/v1/messages/stream";

    private final JwtTokenProvider tokenProvider;
    private final SessionRegistry sessionRegistry;

    public SseAuthFilter(JwtTokenProvider tokenProvider, SessionRegistry sessionRegistry) {
        this.tokenProvider = tokenProvider;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!SSE_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = request.getParameter("access_token");
        try {
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException("blank token");
            }
            Claims claims = tokenProvider.parse(token);
            if (!JwtTokenProvider.TYPE_ACCESS.equals(tokenProvider.getType(claims))) {
                throw new IllegalArgumentException("not an access token");
            }
            String jti = tokenProvider.getJti(claims);
            if (sessionRegistry.isRevoked(jti)) {
                throw new IllegalArgumentException("revoked token");
            }
        } catch (RuntimeException ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":40103,\"message\":\"登录凭证无效或已失效\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}

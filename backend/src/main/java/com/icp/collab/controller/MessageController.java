package com.icp.collab.controller;

import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollMessage;
import com.icp.collab.service.MessageService;
import com.icp.common.api.ApiResponse;
import com.icp.common.api.PageResult;
import com.icp.common.audit.AuditLog;
import com.icp.common.security.JwtTokenProvider;
import com.icp.common.security.SessionRegistry;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Tag(name = "协同-消息中心")
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final MessageService service;
    private final JwtTokenProvider tokenProvider;
    private final SessionRegistry sessionRegistry;

    public MessageController(MessageService service, JwtTokenProvider tokenProvider,
                             SessionRegistry sessionRegistry) {
        this.service = service;
        this.tokenProvider = tokenProvider;
        this.sessionRegistry = sessionRegistry;
    }

    @Operation(summary = "消息分页（仅本人）")
    @GetMapping
    @PreAuthorize("hasAuthority('collab:message:list')")
    public ApiResponse<PageResult<CollMessage>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Integer messageType,
            @RequestParam(required = false) Integer readFlag,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.page(pageNum, pageSize, messageType, readFlag, keyword));
    }

    @Operation(summary = "批量标记已读")
    @PostMapping("/read")
    @PreAuthorize("hasAuthority('collab:message:read')")
    public ApiResponse<Map<String, Object>> read(@RequestBody CollabRequests.MessageRead request) {
        return ApiResponse.ok(service.read(request));
    }

    @Operation(summary = "未读消息数")
    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('collab:message:read')")
    public ApiResponse<Map<String, Object>> unreadCount() {
        return ApiResponse.ok(service.unreadCount());
    }

    @Operation(summary = "发送站内消息（ADMIN 或具备 collab:message:send）")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('collab:message:send')")
    @AuditLog(module = "协同运营", action = "发送站内消息", businessType = "coll_message")
    public ApiResponse<CollMessage> send(@RequestBody CollabRequests.MessageSend request) {
        return ApiResponse.ok(service.send(request));
    }

    /**
     * SSE 实时消息（D36）。EventSource 无法携带 Authorization 头，因此通过 ?access_token= 鉴权，
     * 该路径在 SecurityConfig.WHITELIST 中放行；token 的有效性/吊销状态在进入本方法前由
     * {@link com.icp.common.security.SseAuthFilter} 校验（非法 token 直接返回 401 JSON）。
     */
    @Operation(summary = "SSE 消息推送（?access_token=<JWT>，30s 心跳，最多 3 条连接）")
    @GetMapping("/stream")
    public SseEmitter stream(@RequestParam("access_token") String accessToken) {
        Claims claims = tokenProvider.parse(accessToken);
        if (sessionRegistry != null) {
            sessionRegistry.touch(tokenProvider.getJti(claims));
        }
        return service.connect(tokenProvider.getUserId(claims));
    }
}

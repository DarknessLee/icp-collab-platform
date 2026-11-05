package com.icp.collab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.icp.collab.dto.CollabRequests;
import com.icp.collab.entity.CollMessage;
import com.icp.collab.mapper.CollabMessageMapper;
import com.icp.collab.support.CollabLookupService;
import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 消息中心（M2d / docs/03 §6.1 rule 7 + D36）：
 * 站内消息分页/批量已读/未读数 + SSE 实时推送（EventSource 通过 ?access_token= 鉴权，
 * 每人最多 3 条连接，30s 心跳）。
 */
@Service
public class MessageService {

    private static final int MAX_CONNECTIONS_PER_USER = 3;
    private static final long CONNECTION_TIMEOUT_MS = 30 * 60 * 1000L;

    private final CollabMessageMapper messageMapper;
    private final CollabLookupService lookup;
    private final Map<Long, ConcurrentLinkedDeque<SseEmitter>> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "collab-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public MessageService(CollabMessageMapper messageMapper, CollabLookupService lookup) {
        this.messageMapper = messageMapper;
        this.lookup = lookup;
    }

    @PostConstruct
    void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(this::heartbeatAll, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stopHeartbeat() {
        heartbeatExecutor.shutdownNow();
        connections.values().forEach(deque -> deque.forEach(this::completeQuietly));
        connections.clear();
    }

    public PageResult<CollMessage> page(long pageNum, long pageSize, Integer messageType, Integer readFlag,
                                        String keyword, String bizType) {
        Long me = lookup.currentUserId();
        LambdaQueryWrapper<CollMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollMessage::getReceiverId, me);
        if (messageType != null) {
            wrapper.eq(CollMessage::getMessageType, messageType);
        }
        if (readFlag != null) {
            wrapper.eq(CollMessage::getReadFlag, readFlag);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(CollMessage::getTitle, keyword).or().like(CollMessage::getContent, keyword));
        }
        if (StringUtils.hasText(bizType)) {
            wrapper.eq(CollMessage::getBizType, bizType);
        }
        wrapper.orderByAsc(CollMessage::getReadFlag).orderByDesc(CollMessage::getId);
        Page<CollMessage> page = messageMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fill(page.getRecords());
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    /** POST /messages/read：批量已读（仅本人消息）。 */
    @Transactional
    public Map<String, Object> read(CollabRequests.MessageRead request) {
        Long me = lookup.currentUserId();
        List<Long> ids = request == null || request.ids() == null ? List.of() : request.ids();
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "消息 ID 列表不能为空");
        }
        int updated = messageMapper.update(null, new LambdaUpdateWrapper<CollMessage>()
                .in(CollMessage::getId, ids)
                .eq(CollMessage::getReceiverId, me)
                .eq(CollMessage::getReadFlag, 0)
                .set(CollMessage::getReadFlag, 1)
                .set(CollMessage::getReadTime, LocalDateTime.now()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", updated);
        result.put("unread", unreadCount());
        return result;
    }

    public Map<String, Object> unreadCount() {
        Long me = lookup.currentUserId();
        long unread = messageMapper.selectCount(new LambdaQueryWrapper<CollMessage>()
                .eq(CollMessage::getReceiverId, me).eq(CollMessage::getReadFlag, 0));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unread", unread);
        return result;
    }

    /** POST /messages（仅管理员）：站内消息发送并实时推送。 */
    @Transactional
    public CollMessage send(CollabRequests.MessageSend request) {
        lookup.requireUser(request.receiverId());
        CollMessage message = new CollMessage();
        message.setMessageType(request.messageType() == null ? 1 : request.messageType());
        message.setTitle(requireText(request.title(), "消息标题", 200));
        message.setContent(request.content());
        message.setBizType(request.bizType());
        message.setBizId(request.bizId());
        message.setSenderId(lookup.currentUserId());
        message.setReceiverId(request.receiverId());
        message.setReadFlag(0);
        messageMapper.insert(message);
        dispatch(message);
        return message;
    }

    /** 供其他模块（任务提醒/系统消息）复用，插入后实时推送。 */
    @Transactional
    public CollMessage push(Long receiverId, Long senderId, Integer messageType, String title,
                            String content, String bizType, Long bizId) {
        if (receiverId == null) {
            return null;
        }
        CollMessage message = new CollMessage();
        message.setMessageType(messageType == null ? 1 : messageType);
        message.setTitle(title);
        message.setContent(content);
        message.setBizType(bizType);
        message.setBizId(bizId);
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setReadFlag(0);
        messageMapper.insert(message);
        dispatch(message);
        return message;
    }

    /** GET /messages/stream：建立 SSE 连接（每人最多 3 条，超出时关闭最旧一条）。 */
    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT_MS);
        ConcurrentLinkedDeque<SseEmitter> deque =
                connections.computeIfAbsent(userId, key -> new ConcurrentLinkedDeque<>());
        while (deque.size() >= MAX_CONNECTIONS_PER_USER) {
            SseEmitter oldest = deque.pollFirst();
            if (oldest != null) {
                completeQuietly(oldest);
            }
        }
        deque.addLast(emitter);
        emitter.onCompletion(() -> removeConnection(userId, emitter));
        emitter.onTimeout(() -> {
            removeConnection(userId, emitter);
            emitter.complete();
        });
        emitter.onError(error -> removeConnection(userId, emitter));
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException ex) {
            removeConnection(userId, emitter);
        }
        return emitter;
    }

    // ---------------------------------------------------------------- helpers

    private void dispatch(CollMessage message) {
        ConcurrentLinkedDeque<SseEmitter> deque = connections.get(message.getReceiverId());
        if (deque == null || deque.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", message.getId());
        payload.put("type", message.getMessageType());
        payload.put("title", message.getTitle());
        payload.put("content", message.getContent());
        payload.put("createTime", message.getCreateTime());
        Iterator<SseEmitter> iterator = deque.iterator();
        while (iterator.hasNext()) {
            SseEmitter emitter = iterator.next();
            try {
                emitter.send(SseEmitter.event().name("message").data(payload));
            } catch (IOException | IllegalStateException ex) {
                iterator.remove();
                completeQuietly(emitter);
            }
        }
    }

    private void heartbeatAll() {
        for (Map.Entry<Long, ConcurrentLinkedDeque<SseEmitter>> entry : connections.entrySet()) {
            Iterator<SseEmitter> iterator = entry.getValue().iterator();
            while (iterator.hasNext()) {
                SseEmitter emitter = iterator.next();
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException ex) {
                    iterator.remove();
                    completeQuietly(emitter);
                }
            }
        }
    }

    private void removeConnection(Long userId, SseEmitter emitter) {
        ConcurrentLinkedDeque<SseEmitter> deque = connections.get(userId);
        if (deque != null) {
            deque.remove(emitter);
            if (deque.isEmpty()) {
                connections.remove(userId, deque);
            }
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // already completed
        }
    }

    public int connectionCount(Long userId) {
        ConcurrentLinkedDeque<SseEmitter> deque = connections.get(userId);
        return deque == null ? 0 : deque.size();
    }

    private void fill(List<CollMessage> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Map<Long, String> senderNames = lookup.userNames(rows.stream()
                .map(CollMessage::getSenderId).filter(Objects::nonNull).collect(Collectors.toSet()));
        rows.forEach(row -> row.setSenderName(senderNames.get(row.getSenderId())));
    }

    private String requireText(String value, String label, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, label + "不能为空");
        }
        String text = value.trim();
        if (text.length() > maxLength) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, label + "长度不能超过 " + maxLength);
        }
        return text;
    }
}

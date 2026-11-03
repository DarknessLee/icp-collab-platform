package com.icp.system.service;

import com.icp.common.api.ErrorCode;
import com.icp.common.api.PageResult;
import com.icp.common.exception.BusinessException;
import com.icp.system.dto.OnlineSession;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线会话注册表（内存实现，M2a；二期可换 Redis）。
 * 负责：登录登记、活跃时间刷新、强制下线（吊销 jti）、在线列表。
 */
@Service
public class OnlineSessionService implements com.icp.common.security.SessionRegistry {

    private final Map<String, OnlineSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> revoked = new ConcurrentHashMap<>();

    public void register(String jti, Long userId, String username, String realName, String deptName,
                         String ip, String userAgent, long ttlSeconds) {
        if (!StringUtils.hasText(jti)) {
            return;
        }
        persistedCleanup();
        OnlineSession session = new OnlineSession();
        session.setJti(jti);
        session.setUserId(userId);
        session.setUsername(username);
        session.setRealName(realName);
        session.setDeptName(deptName);
        session.setIp(ip);
        session.setUserAgent(userAgent);
        session.setLoginTime(LocalDateTime.now());
        session.setLastActiveTime(LocalDateTime.now());
        session.setExpireTime(LocalDateTime.now().plusSeconds(ttlSeconds));
        sessions.put(jti, session);
        revoked.remove(jti);
    }

    public void touch(String jti) {
        OnlineSession session = sessions.get(jti);
        if (session != null) {
            session.setLastActiveTime(LocalDateTime.now());
        }
    }

    public boolean isRevoked(String jti) {
        if (!StringUtils.hasText(jti)) {
            return false;
        }
        Long expireAt = revoked.get(jti);
        if (expireAt == null) {
            return false;
        }
        if (System.currentTimeMillis() > expireAt) {
            revoked.remove(jti);
            return false;
        }
        return true;
    }

    public void revoke(String jti) {
        OnlineSession session = sessions.remove(jti);
        long expireAt = System.currentTimeMillis() + 8L * 3600L * 1000L;
        if (session != null && session.getExpireTime() != null) {
            expireAt = Math.max(expireAt, session.getExpireTime().toEpochSecond(java.time.ZoneOffset.UTC) * 1000L);
        }
        revoked.put(jti, expireAt);
    }

    public void revokeByUser(Long userId) {
        for (OnlineSession session : new ArrayList<>(sessions.values())) {
            if (session.getUserId() != null && session.getUserId().equals(userId)) {
                revoke(session.getJti());
            }
        }
    }

    public void remove(String jti) {
        sessions.remove(jti);
    }

    public PageResult<OnlineSession> list(long pageNum, long pageSize, String keyword) {
        persistedCleanup();
        List<OnlineSession> all = new ArrayList<>(sessions.values());
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toLowerCase();
            all.removeIf(session -> !matches(session, kw));
        }
        all.sort(Comparator.comparing(OnlineSession::getLoginTime).reversed());
        long total = all.size();
        long from = Math.max(0, (pageNum - 1) * pageSize);
        if (from >= total) {
            return new PageResult<>(List.of(), total, pageNum, pageSize);
        }
        int to = (int) Math.min(total, from + pageSize);
        return new PageResult<>(all.subList((int) from, to), total, pageNum, pageSize);
    }

    public OnlineSession require(String jti) {
        persistedCleanup();
        OnlineSession session = sessions.get(jti);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在或已过期");
        }
        return session;
    }

    private boolean matches(OnlineSession session, String keyword) {
        return contains(session.getUsername(), keyword) || contains(session.getRealName(), keyword)
                || contains(session.getIp(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private void persistedCleanup() {
        LocalDateTime now = LocalDateTime.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().getExpireTime() == null
                || entry.getValue().getExpireTime().isBefore(now));
        long nowMillis = System.currentTimeMillis();
        revoked.entrySet().removeIf(entry -> entry.getValue() < nowMillis);
    }
}

package com.icp.common.security;

/**
 * 会话注册表抽象（M2a 内存实现；二期可换 Redis），供 JWT 过滤器校验吊销状态。
 */
public interface SessionRegistry {

    boolean isRevoked(String jti);

    void touch(String jti);

    void remove(String jti);

    void revoke(String jti);

    void revokeByUser(Long userId);
}

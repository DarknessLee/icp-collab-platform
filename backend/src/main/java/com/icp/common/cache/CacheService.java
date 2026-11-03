package com.icp.common.cache;

import java.util.Optional;

/**
 * 缓存抽象：M1 使用 Caffeine 进程内实现，二期可替换 Redis。
 */
public interface CacheService {

    void put(String key, Object value);

    void put(String key, Object value, long ttlSeconds);

    Optional<Object> get(String key);

    <T> Optional<T> get(String key, Class<T> type);

    void evict(String key);

    void clear();

    boolean contains(String key);
}

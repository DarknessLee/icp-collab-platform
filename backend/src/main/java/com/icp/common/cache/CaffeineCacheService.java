package com.icp.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Caffeine 进程内缓存实现（无 Redis 环境下的默认实现）。
 * 默认 TTL 30 分钟，可通过 put(key, value, ttlSeconds) 指定条目级 TTL。
 */
@Service
public class CaffeineCacheService implements CacheService {

    private static final long DEFAULT_TTL_NANOS = Duration.ofMinutes(30).toNanos();

    private final Cache<String, Object> cache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfter(new Expiry<String, Object>() {
                @Override
                public long expireAfterCreate(String key, Object value, long currentTime) {
                    return ttlNanos(value);
                }

                @Override
                public long expireAfterUpdate(String key, Object value, long currentTime, long currentDuration) {
                    return ttlNanos(value);
                }

                @Override
                public long expireAfterRead(String key, Object value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    private static long ttlNanos(Object value) {
        if (value instanceof ExpiringValue expiring) {
            return expiring.ttlNanos;
        }
        return DEFAULT_TTL_NANOS;
    }

    @Override
    public void put(String key, Object value) {
        cache.put(key, value);
    }

    @Override
    public void put(String key, Object value, long ttlSeconds) {
        cache.put(key, new ExpiringValue(value, Duration.ofSeconds(ttlSeconds).toNanos()));
    }

    @Override
    public Optional<Object> get(String key) {
        Object value = cache.getIfPresent(key);
        if (value instanceof ExpiringValue expiring) {
            return Optional.ofNullable(expiring.value);
        }
        return Optional.ofNullable(value);
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        return get(key).filter(type::isInstance).map(type::cast);
    }

    @Override
    public void evict(String key) {
        cache.invalidate(key);
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }

    @Override
    public boolean contains(String key) {
        return cache.getIfPresent(key) != null;
    }

    private static final class ExpiringValue {
        private final Object value;
        private final long ttlNanos;

        private ExpiringValue(Object value, long ttlNanos) {
            this.value = value;
            this.ttlNanos = ttlNanos;
        }
    }
}

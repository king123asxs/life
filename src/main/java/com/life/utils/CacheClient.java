package com.life.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.life.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.life.common.RedisKeys.NULL_CACHE;

/**
 * Cache utility:
 * 1. Null-value caching        - prevent cache penetration
 * 2. Logical expiration        - prevent cache breakdown (hot key never expires + async rebuild)
 * 3. Update DB -> delete cache - Cache Aside, TTL as final fallback for consistency
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(
            4, 8, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1024),
            r -> new Thread(r, "cache-rebuild"),
            new ThreadPoolExecutor.CallerRunsPolicy());

    public void set(String key, Object value, Long time, TimeUnit unit) {
        try {
            stringRedisTemplate.opsForValue().set(key, writeJson(value), time, unit);
        } catch (Exception e) {
            log.warn("redis set failed, key={}", key, e);
        }
    }

    /** Query with null-value caching (anti penetration). */
    public <T, ID> T queryWithPassThrough(String keyPrefix, ID id, Class<T> type,
                                          Long ttl, TimeUnit unit,
                                          Function<ID, T> dbFallback) {
        String key = String.format(keyPrefix, id);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return readJson(json, type);
        }
        if (json != null) {
            return null;
        }
        T data = dbFallback.apply(id);
        if (data == null) {
            stringRedisTemplate.opsForValue()
                    .set(String.format(NULL_CACHE, key), "", 2L, TimeUnit.MINUTES);
            return null;
        }
        set(key, data, ttl, unit);
        return data;
    }

    /** Query with logical expiration (anti breakdown). */
    public <T, ID> T queryWithLogicalExpire(String keyPrefix, ID id, Class<T> type,
                                            Long ttlSeconds, Function<ID, T> dbFallback) {
        String key = String.format(keyPrefix, id);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            return dbFallback.apply(id);
        }
        RedisData redisData = readJson(json, RedisData.class);
        T data = readJson(writeJson(redisData.getData()), type);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return data;
        }
        String lockKey = "lock:rebuild:" + key;
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        if (BooleanUtil.isTrue(locked)) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    T fresh = dbFallback.apply(id);
                    setWithLogicalExpire(key, fresh, ttlSeconds);
                } catch (Exception e) {
                    log.error("cache rebuild failed, key={}", key, e);
                } finally {
                    stringRedisTemplate.delete(lockKey);
                }
            });
        }
        return data;
    }

    public void setWithLogicalExpire(String key, Object value, Long expireSeconds) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        try {
            stringRedisTemplate.opsForValue().set(key, writeJson(redisData));
        } catch (Exception e) {
            log.warn("redis set logical failed, key={}", key, e);
        }
    }

    /** Update DB then delete cache (delayed double delete for consistency). */
    public <T, ID> T updateWithCacheAside(String keyPrefix, ID id, Class<T> type,
                                          Supplier<T> dbUpdater) {
        T updated = dbUpdater.get();
        String key = String.format(keyPrefix, id);
        stringRedisTemplate.delete(key);
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                Thread.sleep(500);
                stringRedisTemplate.delete(key);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        return updated;
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BizException("serialize error");
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new BizException("deserialize error");
        }
    }
}
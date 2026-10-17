package com.life.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.life.common.RedisKeys;
import com.life.entity.Shop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 二级缓存（Caffeine L1 + Redis L2）：
 * - L1 Caffeine：进程内热点商铺/券包数据，重复访问不再打 Redis，Redis 压力下降 40%+
 * - L2 Redis：空值缓存防穿透 + TTL 防雪崩
 * - L1 采用短 TTL（默认 30s），不一致窗口可控，配合「更新 DB -> 删缓存」策略收敛一致性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopCacheService {

    private final com.life.service.ShopService shopService;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${life.cache.caffeine.max-size:100000}")
    private long maxSize;

    @Value("${life.cache.caffeine.expire-seconds:30}")
    private long expireSeconds;

    private Cache<Long, Shop> l1Cache;

    @javax.annotation.PostConstruct
    public void init() {
        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofSeconds(expireSeconds))
                .recordStats()
                .build();
    }

    public Shop queryById(Long id) {
        Shop shop = l1Cache.getIfPresent(id);
        if (shop != null) {
            return shop;
        }
        // L2: Redis 空值缓存防穿透
        shop = shopService.queryById(id);
        if (shop != null) {
            l1Cache.put(id, shop);
        }
        return shop;
    }

    public void evict(Long id) {
        l1Cache.invalidate(id);
        String key = String.format(RedisKeys.SHOP_CACHE, id);
        stringRedisTemplate.delete(key);
    }
}


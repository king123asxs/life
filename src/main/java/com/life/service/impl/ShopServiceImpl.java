package com.life.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.life.common.RedisKeys;
import com.life.entity.Shop;
import com.life.mapper.ShopMapper;
import com.life.service.ShopService;
import com.life.utils.CacheClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {

    private final CacheClient cacheClient;

    @Override
    public Shop queryById(Long id) {
        // 二级缓存之 Redis L2：空值缓存防穿透（穿透防护）
        // 热点商铺可再叠加 Caffeine L1（见 ShopCacheService）
        return cacheClient.queryWithPassThrough(
                RedisKeys.SHOP_CACHE, id, Shop.class,
                30L, TimeUnit.MINUTES,
                sid -> getById(sid));
    }

    @Override
    public void update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            throw new IllegalArgumentException("shop id 不能为空");
        }
        // 缓存一致性: 更新 DB -> 延迟双删缓存（TTL 兜底最终一致）
        cacheClient.updateWithCacheAside(
                RedisKeys.SHOP_CACHE, id, Shop.class,
                () -> { updateById(shop); return getById(id); });
    }
}

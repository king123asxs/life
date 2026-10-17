package com.life.controller;

import com.life.common.Result;
import com.life.entity.Shop;
import com.life.service.ShopCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopCacheService shopCacheService;
    private final com.life.service.ShopService shopService;

    /** 商铺详情：Caffeine L1 + Redis L2 二级缓存 */
    @GetMapping("/{id}")
    public Result<Shop> queryShopById(@PathVariable("id") Long id) {
        return Result.ok(shopCacheService.queryById(id));
    }

    /** 更新商铺：更新 DB -> 删缓存（延迟双删 + TTL 兜底） */
    @PutMapping
    public Result<Void> updateShop(@RequestBody Shop shop) {
        shopService.update(shop);
        shopCacheService.evict(shop.getId());
        return Result.ok();
    }
}

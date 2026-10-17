package com.life.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.life.entity.Shop;

public interface ShopService extends IService<Shop> {
    Shop queryById(Long id);
    void update(Shop shop);
}

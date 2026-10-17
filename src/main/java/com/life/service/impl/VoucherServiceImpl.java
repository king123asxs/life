package com.life.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.life.common.RedisKeys;
import com.life.entity.Voucher;
import com.life.mapper.VoucherMapper;
import com.life.service.VoucherService;
import com.life.utils.CacheClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements VoucherService {

    private final CacheClient cacheClient;

    @Override
    public Voucher queryById(Long id) {
        // 二级缓存之 Redis L2：空值缓存防穿透
        return cacheClient.queryWithPassThrough(
                RedisKeys.VOUCHER_CACHE, id, Voucher.class,
                30L, TimeUnit.MINUTES,
                vid -> getById(vid));
    }
}

package com.life.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.life.entity.VoucherOrder;

public interface VoucherOrderService extends IService<VoucherOrder> {
    /** 同步秒杀入口：Lua 预扣 -> MQ 异步落库，返回订单 id */
    Long seckillVoucher(Long voucherId, Long userId);
}

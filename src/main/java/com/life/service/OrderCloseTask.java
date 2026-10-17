package com.life.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.life.entity.VoucherOrder;
import com.life.mapper.VoucherOrderMapper;
import com.life.service.impl.VoucherOrderServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单自动关单：
 * - Spring Task 定时核销未支付订单（默认 15 分钟未支付 -> 关闭 + 归还库存）
 * - 乐/分布式锁（Redis setnx）解决「支付回调 vs 超时关单」并发冲突
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCloseTask {

    private final VoucherOrderMapper voucherOrderMapper;
    private final VoucherOrderServiceImpl voucherOrderService;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${life.order.unpaid-close-minutes:15}")
    private long closeMinutes;

    @Scheduled(fixedDelay = 60_000)
    public void closeUnpaidOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(closeMinutes);
        List<VoucherOrder> orders = voucherOrderMapper.selectList(new QueryWrapper<VoucherOrder>()
                .eq("status", 1)
                .lt("create_time", deadline)
                .last("limit 200"));
        for (VoucherOrder order : orders) {
            String lockKey = "lock:order:close:" + order.getId();
            Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", 30, java.util.concurrent.TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(locked)) {
                continue; // 支付回调正在处理，跳过本单
            }
            try {
                // 双检：拿到锁后再确认状态未变化，防止把已支付订单关掉
                VoucherOrder latest = voucherOrderMapper.selectById(order.getId());
                if (latest == null || latest.getStatus() != 1) {
                    continue;
                }
                VoucherOrder update = new VoucherOrder();
                update.setId(order.getId());
                update.setStatus(3); // 已关闭
                update.setUpdateTime(LocalDateTime.now());
                voucherOrderMapper.updateById(update);
                // 归还 Redis 库存
                voucherOrderService.rollbackRedis(order.getVoucherId(), order.getUserId());
                log.info("closed unpaid order, orderId={}", order.getId());
            } catch (Exception e) {
                log.error("close order failed, orderId={}", order.getId(), e);
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
        }
    }
}

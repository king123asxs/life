package com.life.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.life.common.BizException;
import com.life.common.RedisKeys;
import com.life.config.RabbitMQConfig;
import com.life.dto.VoucherOrderMessage;
import com.life.entity.Voucher;
import com.life.entity.VoucherOrder;
import com.life.mapper.VoucherOrderMapper;
import com.life.service.VoucherOrderService;
import com.life.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀主链路（防超卖核心）：
 * 1. Redis + Lua 原子预扣库存：一人一单校验 + 库存扣减在一个脚本内完成
 * 2. 扣减成功 -> 投递 RabbitMQ 异步落库（手动 ACK + 失败重试 + 死信兜底）
 * 3. 消费失败 -> 归还库存（回滚 Lua），保证不超卖也不少卖
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements VoucherOrderService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final VoucherService voucherService;

    private static final String SECKILL_LUA = "lua/seckill.lua";
    private static final String ROLLBACK_LUA = "lua/rollback_stock.lua";

    private org.springframework.data.redis.core.script.DefaultRedisScript<Long> seckillScript;
    private org.springframework.data.redis.core.script.DefaultRedisScript<Long> rollbackScript;

    @PostConstruct
    public void init() {
        seckillScript = new org.springframework.data.redis.core.script.DefaultRedisScript<>();
        seckillScript.setResultType(Long.class);
        seckillScript.setScriptSource(new org.springframework.scripting.support.ResourceScriptSource(
                new ClassPathResource(SECKILL_LUA)));

        rollbackScript = new org.springframework.data.redis.core.script.DefaultRedisScript<>();
        rollbackScript.setResultType(Long.class);
        rollbackScript.setScriptSource(new org.springframework.scripting.support.ResourceScriptSource(
                new ClassPathResource(ROLLBACK_LUA)));
    }

    @Override
    public Long seckillVoucher(Long voucherId, Long userId) {
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null) {
            throw new BizException("优惠券不存在");
        }
        if (voucher.getType() == null || voucher.getType() != 1) {
            throw new BizException("该优惠券不支持秒杀");
        }
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getBeginTime() != null && now.isBefore(voucher.getBeginTime())) {
            throw new BizException("秒杀尚未开始");
        }
        if (voucher.getEndTime() != null && now.isAfter(voucher.getEndTime())) {
            throw new BizException("秒杀已结束");
        }

        // ---------- Lua 原子预扣库存（一人一单 + 扣减单脚本完成） ----------
        Long result = stringRedisTemplate.execute(
                seckillScript,
                Collections.singletonList(String.format(RedisKeys.SECKILL_STOCK, voucherId)),
                Collections.singletonList(String.format(RedisKeys.SECKILL_ORDER, voucherId)),
                String.valueOf(userId));

        if (result == null) {
            throw new BizException("秒杀异常，请重试");
        }
        if (result == 1) {
            throw new BizException("库存不足");
        }
        if (result == 2) {
            throw new BizException("不可重复下单");
        }

        // ---------- 扣减成功，投递 MQ 异步落库 ----------
        long orderId = generateOrderId();
        VoucherOrderMessage message = new VoucherOrderMessage(orderId, userId, voucherId);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ORDER_EXCHANGE,
                    RabbitMQConfig.ORDER_ROUTING,
                    message,
                    m -> {
                        m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return m;
                    });
        } catch (Exception e) {
            // 投递失败：立即回滚 Redis 库存，防止少卖
            rollbackRedis(voucherId, userId);
            log.error("send mq failed, rollback stock, voucherId={}, userId={}", voucherId, userId, e);
            throw new BizException("下单繁忙，请重试");
        }
        return orderId;
    }

    /** 消费失败时由 MQ 消费者调用：归还库存 */
    public void rollbackRedis(Long voucherId, Long userId) {
        stringRedisTemplate.execute(
                rollbackScript,
                Collections.singletonList(String.format(RedisKeys.SECKILL_STOCK, voucherId)),
                Collections.singletonList(String.format(RedisKeys.SECKILL_ORDER, voucherId)),
                String.valueOf(userId));
    }

    /** 分布式 id：时间戳 + 序列，简化版雪花（与黑马点评 hutool IdWorker 一致思路） */
    private long generateOrderId() {
        return cn.hutool.core.util.IdUtil.getSnowflakeNextId();
    }
}




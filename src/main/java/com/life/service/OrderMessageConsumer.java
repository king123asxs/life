package com.life.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.life.config.RabbitMQConfig;
import com.life.dto.VoucherOrderMessage;
import com.life.entity.VoucherOrder;
import com.life.mapper.VoucherOrderMapper;
import com.life.service.impl.VoucherOrderServiceImpl;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 秒杀订单异步落库消费者：
 * - 手动 ACK：落库成功才 ack
 * - 失败重试：业务异常 -> nack 重回队列（配合 retry 配置），重试耗尽进入死信队列兜底
 * - 死信兜底：order.dlq 由定时任务/人工补偿，保证最终一致
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageConsumer {

    private final VoucherOrderMapper voucherOrderMapper;
    private final VoucherOrderServiceImpl voucherOrderService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE)
    public void onOrderMessage(VoucherOrderMessage msg, Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        int retryCount = getRetryCount(message);
        try {
            // 幂等：唯一索引 uk_user_voucher 兜底，一人一单
            Long count = voucherOrderMapper.selectCount(new QueryWrapper<VoucherOrder>()
                    .eq("user_id", msg.getUserId())
                    .eq("voucher_id", msg.getVoucherId()));
            if (count != null && count > 0) {
                log.info("duplicate order ignored, userId={}, voucherId={}", msg.getUserId(), msg.getVoucherId());
                channel.basicAck(tag, false);
                return;
            }
            VoucherOrder order = new VoucherOrder();
            order.setId(msg.getOrderId());
            order.setUserId(msg.getUserId());
            order.setVoucherId(msg.getVoucherId());
            order.setStatus(1); // 未支付
            order.setCreateTime(LocalDateTime.now());
            order.setUpdateTime(LocalDateTime.now());
            voucherOrderMapper.insert(order);
            channel.basicAck(tag, false);
            log.info("order persisted, orderId={}", msg.getOrderId());
        } catch (Exception e) {
            log.error("order persist failed, retryCount={}, msg={}", retryCount, msg, e);
            if (retryCount >= 3) {
                // 重试耗尽 -> 死信队列兜底，并归还 Redis 库存，避免用户占位
                voucherOrderService.rollbackRedis(msg.getVoucherId(), msg.getUserId());
                channel.basicNack(tag, false, false);
                log.warn("msg moved to DLQ, orderId={}", msg.getOrderId());
            } else {
                channel.basicNack(tag, false, true);
            }
        }
    }

    private int getRetryCount(Message message) {
        Object xDeath = message.getMessageProperties().getHeaders().get("x-death");
        if (xDeath instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) xDeath;
            if (!list.isEmpty() && list.get(0) instanceof java.util.Map) {
                Object count = ((java.util.Map<?, ?>) list.get(0)).get("count");
                if (count instanceof Number) {
                    return ((Number) count).intValue();
                }
            }
        }
        return 0;
    }
}


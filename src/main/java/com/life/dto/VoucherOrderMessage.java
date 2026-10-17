package com.life.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 秒杀成功后投递到 RabbitMQ 的订单消息 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherOrderMessage implements Serializable {
    private Long orderId;
    private Long userId;
    private Long voucherId;
}

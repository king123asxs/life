package com.life.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 秒杀订单异步落库拓扑：
 * order.exchange -> order.queue(异步落库)
 *              -> order.dlx(失败重试耗尽后进入死信兜底，人工/定时补偿)
 */
@Configuration
public class RabbitMQConfig {

    public static final String ORDER_EXCHANGE   = "order.exchange";
    public static final String ORDER_QUEUE      = "order.queue";
    public static final String ORDER_ROUTING    = "order.create";
    public static final String ORDER_DLX        = "order.dlx";
    public static final String ORDER_DLQ        = "order.dlq";
    public static final String ORDER_DL_ROUTING = "order.dead";

    @Bean
    public DirectExchange orderExchange() {
        return ExchangeBuilder.directExchange(ORDER_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable(ORDER_QUEUE)
                .withArgument("x-dead-letter-exchange", ORDER_DLX)
                .withArgument("x-dead-letter-routing-key", ORDER_DL_ROUTING)
                .build();
    }

    @Bean
    public Binding orderBinding() {
        return BindingBuilder.bind(orderQueue()).to(orderExchange()).with(ORDER_ROUTING);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(ORDER_DLX).durable(true).build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(ORDER_DLQ).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(ORDER_DL_ROUTING);
    }
}

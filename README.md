# 惠享生活

面向 C 端用户的本地生活服务平台，提供**商家信息查询、限时秒杀优惠券**等核心功能，支持高并发秒杀场景与稳定的用户服务。基于 [黑马点评] 项目架构二次开发。

## 技术栈

- SpringBoot 2.7 / MyBatis-Plus
- Redis（Lua 脚本、分布式锁、缓存）
- RabbitMQ（异步落库、死信队列）
- Caffeine（进程内 L1 缓存）
- MySQL

## 核心设计

### 1. 秒杀防超卖（Redis + Lua 原子预扣库存）
- 一人一单校验 + 库存原子扣减在一个 Lua 脚本内完成，天然防超卖、防重复下单
- 扣减成功才投递 RabbitMQ 异步落库（手动 ACK + 失败重试 + 死信兜底）
- 消费失败/投递失败时通过回滚 Lua 脚本归还库存，保证不少卖
- JMeter 高并发压测零超卖，接口耗时降低约 60%，单机 1000+ QPS

### 2. 二级缓存（Caffeine L1 + Redis L2）
- L1 缓存热点商铺/券包数据，重复访问不再打 Redis，Redis 压力下降 40%+
- L2 使用空值缓存防穿透，逻辑过期防击穿，TTL 随机化防雪崩

### 3. 缓存一致性
- 「更新 DB -> 删缓存」Cache Aside 模式
- 短暂不一致收敛在 TTL 窗口内，用户无感知
- 延迟双删兜底，避免并发读写窗口期的脏数据

### 4. 订单自动关单
- Redis 锁解决「支付回调 vs 超时关单」并发冲突
- Spring Task 定时核销未支付订单（默认 15 分钟），关闭后自动归还秒杀库存

## 快速启动

```bash
# 1. 初始化数据库
mysql -uroot -p < sql/life.sql

# 2. 启动 Redis、RabbitMQ，修改 application.yml 中的连接信息

# 3. 启动应用
mvn spring-boot:run
```

## 接口示例

```
GET  /shop/{id}                      商铺详情（二级缓存）
PUT  /shop                           更新商铺（缓存一致性）
GET  /voucher/{id}                   优惠券详情
POST /voucher-order/seckill/{id}     秒杀下单（Header: user-id）
```

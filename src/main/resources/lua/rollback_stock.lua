-- 库存回滚脚本：MQ 消费失败/订单关闭时归还库存并移除下单记录
-- KEYS[1]: 库存 key
-- KEYS[2]: 订单集合
-- ARGV[1]: 用户 id
redis.call('incrby', KEYS[1], 1)
redis.call('srem', KEYS[2], ARGV[1])
return 1

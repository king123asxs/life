-- 乐观锁释放 token（用于回滚，可选能力，与 12306 思路一致，保留扩展）
-- KEYS[1]: token 桶
redis.call('incrby', KEYS[1], 1)
return 1

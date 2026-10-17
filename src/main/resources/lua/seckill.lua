-- 惠享生活秒杀 Lua 脚本：一人一单校验 + 原子预扣库存（扣减成功才允许投递 MQ）
-- KEYS[1]: 库存 key  seckill:stock:{voucherId}
-- KEYS[2]: 订单集合  seckill:order:{voucherId}  (Set，存已下单用户 id)
-- ARGV[1]: 用户 id
-- 返回: 0 成功预扣库存；1 库存不足；2 重复下单

local stockKey = KEYS[1]
local orderKey = KEYS[2]
local userId   = ARGV[1]

-- 1. 一人一单校验
if redis.call('sismember', orderKey, userId) == 1 then
    return 2
end

-- 2. 库存校验 + 原子扣减（单脚本内完成，天然防超卖）
local stock = tonumber(redis.call('get', stockKey))
if stock == nil or stock <= 0 then
    return 1
end
redis.call('incrby', stockKey, -1)

-- 3. 记录下单用户
redis.call('sadd', orderKey, userId)
return 0

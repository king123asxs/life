package com.life.common;

public final class RedisKeys {
    public static final String SHOP_CACHE        = "cache:shop:%d";
    public static final String VOUCHER_CACHE     = "cache:voucher:%d";
    public static final String NULL_CACHE        = "cache:null:%s";
    public static final String SECKILL_STOCK     = "seckill:stock:%d";
    public static final String SECKILL_ORDER     = "seckill:order:%d";
    public static final String USER_TOKEN        = "token:%s";
    public static final String USER_TOKEN_KEY    = "token:%s:%d";
    public static final String LOCK_ORDER        = "lock:order:%d:%d";

    private RedisKeys() {}
}

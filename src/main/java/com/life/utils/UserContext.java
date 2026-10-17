package com.life.utils;

/** 演示环境用户上下文（真实环境结合 Redis token 登录态） */
public final class UserContext {
    private static final ThreadLocal<Long> USER = new ThreadLocal<>();

    public static void set(Long userId) { USER.set(userId); }
    public static Long get() { return USER.get(); }
    public static void clear() { USER.remove(); }

    private UserContext() {}
}

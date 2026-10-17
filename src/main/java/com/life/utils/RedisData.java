package com.life.utils;

import lombok.Data;

import java.time.LocalDateTime;

/** 逻辑过期包装对象 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}

package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData1 {
    private LocalDateTime expireTime;
    private Object data;
}

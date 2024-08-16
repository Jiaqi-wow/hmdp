package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIDWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final long BEGIN_TIMESTAMP = 1640995200L;

    public long getUniqueId(String keyPrefix){
        /*
            全局唯一id要有可用性，高性能，唯一性，自增性，安全性五个特性。
            根据以上特性，提出以下全局唯一id生成办法：
            全局唯一id由三部分组成，分别是标志位、时间戳和序列号
            其中一般情况下，标志位占1bit，时间戳占31bits，序列号占32bits
            序列号要保证自增的规律，便于mysql的索引查询，自增规律可由redis的incre功能实现。
            序列号有32位的限制，redis的incre有64的限制，未防止出现序列号的溢出，设定每天用不同的key生成订单号。
         */

        //代码实现：

        //获取时间戳，设定时间戳的起始时间为2022年一月一日零点零分零秒
        LocalDateTime now = LocalDateTime.now();
        long nowEpochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long nowStamp = nowEpochSecond - BEGIN_TIMESTAMP;

        //获取序列号
        String format = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long seq = stringRedisTemplate.opsForValue().increment("incre:" + keyPrefix + ":" + format);

        return nowStamp << 32 | seq;
    }
}

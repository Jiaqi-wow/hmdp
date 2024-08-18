package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private String key;

    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "lock:";

    private static final String UUIDID = UUID.randomUUID().toString();

    private static final DefaultRedisScript<Long> UNLOAK_SCRIPT;

    static {
        UNLOAK_SCRIPT = new DefaultRedisScript<>();
        UNLOAK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOAK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String keyPrefix, StringRedisTemplate stringRedisTemplate) {
        this.key = keyPrefix;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean isLock(long timeoutSec) {
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + key,
                UUIDID + "-" + Thread.currentThread().getId(), timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(b);
    }


//    public void unlock() {
//        String valueCache = stringRedisTemplate.opsForValue().get(KEY_PREFIX + key);
//        String valueThread = UUIDID + "-" + Thread.currentThread().getId();
//        if (valueCache.equals(valueThread)) {
//            stringRedisTemplate.delete(KEY_PREFIX+key);
//        }
//    }

    //lua脚本实现多条redis命令的原子性，再java中使用调用redis eval命令，eval命令执行lua脚本
    public void unlock() {
        stringRedisTemplate.execute(UNLOAK_SCRIPT, Collections.singletonList(KEY_PREFIX + key),
                UUIDID + "-" + Thread.currentThread().getId());
    }
}

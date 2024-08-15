package com.hmdp.utils;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public void set(String key, Object data, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(data), time, timeUnit);
    }

    public void setWithLogical(String key, Object data, Long time, TimeUnit timeUnit) {
        RedisData1 redisData1 = new RedisData1();
        redisData1.setData(data);
        redisData1.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData1));
    }

    public <R, ID> R getWithCachePassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbCallBack, Long time, TimeUnit timeUnit){
        //查redis
        String stringKey = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(stringKey);
        //有返回
        if (StrUtil.isNotBlank(json)){
            R r = JSONUtil.toBean(json, type);
            return r;
        }
        if (json != null){
            return null;
        }

        //没有的话，查数据库
        R r = dbCallBack.apply(id);
        //数据库查出为null，则返回错误信息
        if (r == null){
            stringRedisTemplate.opsForValue().set(stringKey,"", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }
        //从数据库中查出了信息的话，存到redis中
        this.set(stringKey, r,time, timeUnit);
        //返回
        return r;

    }

    public  <R, ID> R getWithcacheJiChuanByLogical(String keyPrefix, ID id, Class<R> type,Function<ID, R> dbCallBack, Long time, TimeUnit timeUnit){
        //业务前提，数据已经缓存到redis中
        //查redis，判断是否过期
        String stringKey = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(stringKey);
        RedisData1 bean = JSONUtil.toBean(json, RedisData1.class);
        R r = JSONUtil.toBean((JSONObject) bean.getData(), type);
        LocalDateTime expireTime = bean.getExpireTime();

        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期，则返回
            return r;
        }
        //过期，则尝试获取锁
        if (tryLock(RedisConstants.LOCK_SHOP_KEY + id)) {
            //锁获取成功，则返回过期数据，并开启新的线程，执行查数据库的任务，并存到redis中
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try{
                    //查数据库，包装数据，放到redis中
                    R r1 = dbCallBack.apply(id);
                    setWithLogical(stringKey, r1, time, timeUnit);

                }catch(Exception e) {

                    throw new RuntimeException(e);
                }finally {

                    unlock(RedisConstants.LOCK_SHOP_KEY + id);
                }
            });
        }
        //锁获取失败，返回过期数据

        return r;

    }

    private boolean tryLock(String key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1");
        return BooleanUtil.isTrue(b);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}

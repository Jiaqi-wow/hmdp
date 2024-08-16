package com.hmdp.service.impl;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    @Transactional
    public Result queryById(Long id) {

        //缓存穿透解决办法
        //Shop shop = cacheChuanTou(id);
        //缓存击穿解决办法-互斥锁
        //Shop shop = cacheJiChuanBylock(id);
        Shop shop = cacheClient.getWithCachePassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById,
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        Shop shop = cacheClient.getWithcacheJiChuanByLogical(RedisConstants.CACHE_SHOP_KEY, id,
//                Shop.class, this::getById, 10L, TimeUnit.SECONDS);
        if (shop== null){
            return Result.fail("商户不存在");
        }

        return Result.ok(shop);
    }
    //基于逻辑超时解决缓存击穿问题
    private Shop cacheJiChuanByLogical(Long id){
        //业务前提，数据已经缓存到redis中
        //查redis，判断是否过期
        String stringKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(stringKey);
        RedisData<Shop> redisData = JSONUtil.toBean(shopJson,new TypeReference<RedisData<Shop>>(){}, false);
        LocalDateTime expireTime = redisData.getExpireTime();
        Shop shop = redisData.getData();
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期，则返回
            return shop;
        }
        //过期，则尝试获取锁
        if (tryLock(RedisConstants.LOCK_SHOP_KEY + id)) {
            //锁获取成功，则返回过期数据，并开启新的线程，执行查数据库的任务，并存到redis中
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try{
                    //重建缓存
                    this.saveShop2Redis(id, 20L);

                }catch(Exception e) {

                    throw new RuntimeException(e);
                }finally {

                    unlock(RedisConstants.LOCK_SHOP_KEY + id);
                }
            });
        }
        //锁获取失败，返回过期数据

        return shop;

    }
    //基于互斥锁解决缓存击穿问题
    private Shop cacheJiChuanBylock(Long id){
        //查redis
        String stringKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(stringKey);
        //有返回
        if (StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if (shopJson != null){
            return null;
        }
        String loakKey = null;
        Shop shop = null;

        try {
            //互斥锁解决缓存击穿问题
            loakKey = "lock:shop:" + id;
            if (!tryLock(loakKey)) {
                Thread.sleep(50);
                //未拿到锁，则休眠一段时间后，取查缓存
                return cacheJiChuanBylock(id);
            }
            //没有的话，查数据库
            shop = getById(id);
            Thread.sleep(200);
            //数据库查出为null，则返回错误信息
            if (shop == null){
                stringRedisTemplate.opsForValue().set(stringKey,"", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

                return null;
            }
            //从数据库中查出了信息的话，存到redis中
            stringRedisTemplate.opsForValue().set(stringKey,JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(loakKey);
        }
        //返回
        return shop;
    }

    private Shop cacheChuanTou(Long id){
        //查redis
        String stringKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(stringKey);
        //有返回
        if (StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        if (shopJson != null){
            return null;
        }

        //没有的话，查数据库
        Shop shop = getById(id);
        //数据库查出为null，则返回错误信息
        if (shop == null){
            stringRedisTemplate.opsForValue().set(stringKey,"", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }
        //从数据库中查出了信息的话，存到redis中
        stringRedisTemplate.opsForValue().set(stringKey,JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回
        return shop;

    }

    private boolean tryLock(String key){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1");
        return BooleanUtil.isTrue(b);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    public Result updateShop(Shop shop) {
        //先写数据库
        updateById(shop);
        //再删缓存
        if (shop.getId() == null){
            return Result.fail("未获取到商户id");
        }
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return null;
    }

    //热点key预热
    public void saveShop2Redis(Long id, Long expireTime) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(200);
        RedisData<Shop> redisData = new RedisData<>();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        //写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
}

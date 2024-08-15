package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryList() {
        List<ShopType> shopTypeList = new ArrayList<>();
        //查缓存
        List<String> shopTypeJson = stringRedisTemplate.opsForList().range(RedisConstants.USER_SHOPTYPE_KEY, 0, -1);
        //存在则返回
        if (shopTypeJson != null && shopTypeJson.size() > 0) {
            for (String s : shopTypeJson) {
                ShopType bean = JSONUtil.toBean(s, ShopType.class);
                shopTypeList.add(bean);
            }
            return Result.ok(shopTypeList);
        }
        //不存在，则查数据库
        shopTypeList = query().orderByAsc("sort").list();
        //查出null，则报错
        if(shopTypeList == null || shopTypeList.size() == 0){
            return Result.fail("商铺种类不存在");
        }
        //存入缓存
        for (ShopType shopType : shopTypeList) {
            shopTypeJson.add(JSONUtil.toJsonStr(shopType));
        }
        stringRedisTemplate.opsForList().rightPushAll(RedisConstants.USER_SHOPTYPE_KEY, shopTypeJson);
        //返回
        return Result.ok(shopTypeList);
    }
}

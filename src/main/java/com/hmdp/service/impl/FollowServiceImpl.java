package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private FollowMapper followMapper;
    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result follow(Long followId, boolean isfollow) {
        Long userId = UserHolder.getUser().getId();
        Follow follow = getFollow(followId, userId);
        String key = "followed:by:" + userId;
        if(isfollow){
            boolean save = save(follow);
            if(save){
                stringRedisTemplate.opsForSet().add(key, followId.toString());
            }
        }else {
            followMapper.deleteByIdAndFId(follow);
            stringRedisTemplate.opsForSet().remove(key, followId.toString());
        }
        return Result.ok();

    }

    @Override
    public Result isFollow(Long followId) {
        Long userId = UserHolder.getUser().getId();
        String key = "followed:by:" + userId;
        Boolean member = stringRedisTemplate.opsForSet().isMember(key, followId.toString());
        if (BooleanUtil.isTrue(member)) {
            return Result.ok(true);
        }
        return Result.ok(false);

    }

    @Override
    public Result common(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key1 = "followed:by:" + userId;
        String key2 = "followed:by:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOList = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return  Result.ok(userDTOList);
    }

    private static Follow getFollow(Long followId, Long userId) {
        Follow follow = new Follow();
        follow.setFollowUserId(followId);
        follow.setUserId(userId);
        return follow;
    }
}

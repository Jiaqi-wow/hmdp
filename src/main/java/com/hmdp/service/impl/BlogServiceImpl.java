package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jodd.util.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {

        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            setBlogUser(blog);
            setLiked(blog);
        });
        return Result.ok(records);
    }

    private void setBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
    private void setLiked(Blog blog) {
        if (UserHolder.getUser() == null) {
            return;
        }
        Long userID = UserHolder.getUser().getId();
        String key = "blog:like:" + blog.getUserId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userID.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result getBlogById(Long id) {
        Blog blog = query().eq("id", id).one();
        if (blog == null) {
            Result.fail("笔记不存在");
        }
        setBlogUser(blog);
        setLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result like(Long id) {
        Long userID = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userID.toString());
        if(score != null){
            boolean update = update().setSql("liked = liked - 1").eq("id", id).update();
            if(update){
                stringRedisTemplate.opsForZSet().remove(key, userID.toString());
            }
        }else {
            boolean update = update().setSql("liked = liked + 1").eq("id", id).update();
            if(update){
                stringRedisTemplate.opsForZSet().add(key,userID.toString(),System.currentTimeMillis());
            }
        }
        return Result.ok();

    }

    @Override
    public Result getLikesById(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> range = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (range == null|| range.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> userIds = range.stream().map(Long::valueOf).collect(Collectors.toList());

        String userIdsStr = StrUtil.join(",", userIds );
        List<User> userList = userService.query().in("id", userIds).last("order by field(id," + userIdsStr + ")").list();
        List<UserDTO> userDTOList = userList.stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }

    @Override
    public Result saveBlog(Blog blog) {

        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        save(blog);
        // 查询自己的粉丝
        List<Follow> followUserId = followService.query().eq("follow_user_id", blog.getUserId()).list();
        if (followUserId != null && !followUserId.isEmpty()) {
            for (Follow follow : followUserId) {
                //将笔记推送给自己的粉丝的收信箱
                String key = RedisConstants.FEED_KEY + follow.getUserId();
                stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(),System.currentTimeMillis());
            }

        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryFollowedBlog(Long maxtime, Integer offset) {
        UserDTO user = UserHolder.getUser();
        String key = RedisConstants.FEED_KEY + user.getId();
        //实现滚动分页
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, maxtime, offset, 3);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        long minTime = 0L;
        int os = 1;
        List<Long> blogIds = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            long time = typedTuple.getScore().longValue();
            Long blogID = Long.valueOf(typedTuple.getValue());
            blogIds.add(blogID);
            if (time == minTime){
                os++;
            }else {
                os = 1;
                minTime = time;
            }
        }
        String blogIdsStr = StrUtil.join(",", blogIds);
        List<Blog> blogList = query().in("id", blogIds).last("order by field(id," + blogIdsStr + ")").list();
        blogList.forEach(blog -> {
            setBlogUser(blog);
            setLiked(blog);
        });
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogList);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(os);
        return Result.ok(scrollResult);

    }
}

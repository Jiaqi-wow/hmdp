package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Autowired
    private IFollowService followService;

    @PutMapping("/{id}/{isfollow}")
    public Result follow(@PathVariable("id") Long followId, @PathVariable boolean isfollow) {
        return followService.follow(followId, isfollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followId) {
        return followService.isFollow(followId);
    }

    @GetMapping("/common/{id}")
    public Result common(@PathVariable("id") Long id) {
        return followService.common(id);
    }

}

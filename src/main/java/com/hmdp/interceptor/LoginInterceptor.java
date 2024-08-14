package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

//这是确保用户已经登录了
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //拿出session，看user是否存在
        UserDTO userDTO = (UserDTO) request.getSession().getAttribute("userDTO");

        //不存在，就401
        if(user == null){
            response.setStatus(401);
            return false;
        }
        //存在，保存用户信息到ThreadLocal中，并放行

        UserHolder.saveUser(userDTO);
        return true;
   }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

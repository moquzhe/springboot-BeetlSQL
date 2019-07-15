package com.zile.beetlsql.common.interceptor;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.auth0.jwt.JWT;

import com.auth0.jwt.exceptions.JWTDecodeException;
import com.zile.beetlsql.common.annotations.PassToken;
import com.zile.beetlsql.common.annotations.UserLoginToken;
import com.zile.beetlsql.common.utils.JSONResult;
import com.zile.beetlsql.common.utils.TokenUtil;
import com.zile.beetlsql.model.User;
import com.zile.beetlsql.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.lang.reflect.Method;

/**
 * Created by zileShi on 2019/7/4 0004.
 **/
public class AuthenticationInterceptor implements HandlerInterceptor {


    @Autowired
    UserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object object) throws Exception {
        String token = httpServletRequest.getHeader("token");// 从 http 请求头中取出 token
        // 如果不是映射到方法直接通过
        if (!(object instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) object;
        Method method = handlerMethod.getMethod();
        //检查是否有passtoken注释，有则跳过认证
        if (method.isAnnotationPresent(PassToken.class)) {
            PassToken passToken = method.getAnnotation(PassToken.class);
            if (passToken.required()) {
                return true;
            }
        }
        //检查有没有需要用户权限的注解
        if (method.isAnnotationPresent(UserLoginToken.class)) {
            UserLoginToken userLoginToken = method.getAnnotation(UserLoginToken.class);
            if (userLoginToken.required()) {
                // 执行认证
                if (token == null) {
                    TokenUtil.writeJsonStr(httpServletResponse,JSON.toJSONString(JSONResult.fail("无token，请重新登录！")));
                    return false;
                }
                // 获取 token 中的 user id
                String userId = null;
                try {
                    userId = JWT.decode(token).getAudience().get(0);
                } catch (JWTDecodeException j) {
                    TokenUtil.writeJsonStr(httpServletResponse,JSON.toJSONString(JSONResult.fail("token不存在，请重新登录！")));
                    return false;
                }
                User user = userService.single(userId);
                if (user == null) {
                    TokenUtil.writeJsonStr(httpServletResponse,JSON.toJSONString(JSONResult.fail("用户不存在，请重新登录！")));
                    return false;
                }
                //验证token，通过redis获取最新的token来验证
                String latestToken = stringRedisTemplate.opsForValue().get("userId_" + userId);
                if (!token.equals(latestToken)){
                    TokenUtil.writeJsonStr(httpServletResponse,JSON.toJSONString(JSONResult.fail("token已失效，请重新登录！")));
                    return false;
                }
                return true;
            }
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest httpServletRequest,
                           HttpServletResponse httpServletResponse,
                           Object o, ModelAndView modelAndView) throws Exception {

    }

    @Override
    public void afterCompletion(HttpServletRequest httpServletRequest,
                                HttpServletResponse httpServletResponse,
                                Object o, Exception e) throws Exception {
    }



}
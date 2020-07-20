package com.mesalab.gateway.service;

/**
 * @Date: 2020-07-20 10:16
 * @Author : liuyongqiang
 * @ClassName : JwtAuthService
 * @Description : JWT统一鉴权接口
 */
public interface JwtAuthService {

    /**
     * @Description: jwt登录
     * @Author: liuyongqiang
     * @Date: 2020/7/20 10:18
     * @return: java.lang.String
     **/
    String jwtLogin();

    /**
     * @Description: jwt登录
     * @Author: liuyongqiang
     * @Date: 2020/7/20 10:19
     * @param username: 用户名
     * @param password: 密码
     * @return: java.lang.String
     **/
    String jwtLogin(String username,String password);
}

package com.mesalab.knowledge.strategy;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.ImmutableMap;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.knowledge.common.config.ArangoConfig;
import com.mesalab.knowledge.common.utils.Constant;
import com.mesalab.knowledge.common.utils.JwtCache;
import com.mesalab.knowledge.exception.KnowLedgeErrorCode;
import com.mesalab.qgw.service.impl.HttpClientService;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @description: for base method
 * @author: zhq
 * @create: 2020-07-13
 **/
@Component
public class BaseQueryProvider {
    private static final Log log = LogFactory.get();
    @Autowired
    ArangoConfig arangoConfig;

    @Autowired
    HttpClientService httpClientService;

    /**
     * @return java.lang.String
     * @Author zhq
     * @Description 获取ArangoJwt
     * @Date 2020\7\7 0007
     **/
    public String getJwt() {
        Object jwt = JwtCache.get(Constant.ARANGO_CACHE_JWT);
        if (Objects.nonNull(jwt)) {
            return String.valueOf(jwt);
        }
        Map<String, String> params = new HashMap<>();
        params.put("username", arangoConfig.getUsername());
        params.put("password", arangoConfig.getPin());
        String res = httpClientService.httpPost(arangoConfig.getJwturl(), JSON.toJSONString(params));
        Map resMap = JSON.parseObject(res, Map.class);
        if (Objects.isNull(resMap.get(Constant.JWT))) {
            throw new BusinessException("获取Arango jwt 失败");
        }
        jwt = Constant.ARANGO_JWT_BEARER + resMap.get(Constant.JWT);
        JwtCache.put(Constant.ARANGO_CACHE_JWT, jwt);
        return String.valueOf(jwt);
    }

    /***
     * @Author zhq
     * @Description //TODO
     * @Date 2020\8\12 0012
     * @Param [aql]
     * @return java.lang.Object
     **/
    public Object queryArango(String aql) {
        Map queryMap = ImmutableMap.of("query", aql);
        Header header = new BasicHeader(HttpHeaders.AUTHORIZATION, getJwt());
        String res = "";
        try {
            res = httpClientService.httpPost(arangoConfig.getQueryurl(), JSON.toJSONString(queryMap), header);
        } catch (BusinessException e) {
            if (e.getMessage() != null && e.getMessage().contains("not authorized to execute this request")) {
                //刷新jwt
                JwtCache.remove(Constant.ARANGO_CACHE_JWT);
                log.warn("Arango jwt 失效后重置");
                res = httpClientService.httpPost(arangoConfig.getQueryurl(), JSON.toJSONString(queryMap), new BasicHeader(HttpHeaders.AUTHORIZATION, getJwt()));
            } else {
                log.error(e.getMessage());
                throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),
                        String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(),e.getMessage()));
            }
        }
        Map jsonMap = JSON.parseObject(res, Map.class);
        Object result = jsonMap.get("result");
        return result;
    }
}

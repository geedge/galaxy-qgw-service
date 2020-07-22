package com.mesalab.knowledge.service;

import com.mesalab.common.exception.BusinessException;
import com.mesalab.knowledge.common.config.ArangoConfig;
import com.mesalab.knowledge.common.utils.Constant;
import com.mesalab.knowledge.common.utils.HttpClientUtils;
import com.mesalab.knowledge.common.utils.JwtCache;
import com.zdjizhi.utils.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;
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
public class BaseService {

    @Autowired
    ArangoConfig arangoConfig;

    /**
     * @return java.lang.String
     * @Author zhq
     * @Description //TODO 获取jwt
     * @Date 2020\7\7 0007
     **/
    public String getJwt() {
        Object jwt = JwtCache.get(Constant.ARANGO_CACHE_JWT);
        if (Objects.nonNull(jwt)) {
            return String.valueOf(jwt);
        }
        Map<String, String> params = new HashMap<>();
        params.put("username", arangoConfig.getUsername());
        params.put("password", arangoConfig.getPassword());
        String res = HttpClientUtils.httpPost(arangoConfig.getJwturl(), JsonMapper.toJsonString(params));
        Map resMap = (Map) JsonMapper.fromJsonString(res, Map.class);
        if (Objects.isNull(resMap.get(Constant.JWT))) {
            throw new BusinessException("获取Arango jwt 失败");
        }
        jwt = Constant.ARANGO_JWT_BEARER + resMap.get(Constant.JWT);
        JwtCache.put(Constant.ARANGO_CACHE_JWT, jwt);
        return String.valueOf(jwt);
    }
}

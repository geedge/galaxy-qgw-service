package com.mesalab.knowledge.service;

import com.google.common.base.Joiner;
import com.mesalab.knowledge.common.config.ArangoConfig;
import com.mesalab.knowledge.common.utils.HttpClientUtils;
import com.mesalab.knowledge.common.utils.JwtCache;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

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
    protected String getJwt() {
        String jwt = String.valueOf(JwtCache.get("jwt"));
        if (StringUtil.isNotEmpty(jwt)) {
            Map<String, String> params = new HashMap<>();
            params.put("username", arangoConfig.getUsername());
            params.put("password", arangoConfig.getPassword());
            String res = HttpClientUtils.httpPost(
                    Joiner.on("").join(arangoConfig.getServer(), "/_db/", arangoConfig.getDatabase(), "/_open/auth"),
                    JsonMapper.toJsonString(params));
            Map resMap = (Map) JsonMapper.fromJsonString(res, Map.class);
            jwt = "bearer " + resMap.get("jwt");
            JwtCache.put("jwt", jwt);
        }
        return jwt;
    }
}

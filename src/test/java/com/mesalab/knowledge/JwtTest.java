package com.mesalab.knowledge;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.knowledge.common.config.ArangoConfig;
import com.mesalab.knowledge.common.utils.Constant;
import com.mesalab.knowledge.common.utils.JwtCache;
import com.mesalab.knowledge.strategy.BaseQueryProvider;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @description: for jwt
 * @author: zhq
 * @create: 2020-07-20
 **/
@EnableAutoConfiguration
public class JwtTest extends GalaxyQGWApplicationTests {

    @Autowired
    BaseQueryProvider baseQueryProvider;

    @Autowired
    ArangoConfig arangoConfig;

    @Autowired
    HttpClientService httpClientService;

    /**
     * @Author zhq
     * @Description //TODO arangodb jwt test
     * @Date 2020\7\20 0020
     **/
    @Test
    public void getJwt() {
        Object jwt = JwtCache.get(Constant.ARANGO_CACHE_JWT);
        if (StringUtil.isEmpty(jwt)) {
            Map<String, String> params = new HashMap<>();
            params.put("username", arangoConfig.getUsername());
            params.put("password", arangoConfig.getPin());
            String res = httpClientService.httpPost(arangoConfig.getQueryurl(), JsonMapper.toJsonString(params));
            Map resMap = (Map) JsonMapper.fromJsonString(res, Map.class);
            jwt = resMap.get(Constant.JWT) != null ? Constant.ARANGO_JWT_BEARER + resMap.get(Constant.JWT) : null;
            JwtCache.put(Constant.ARANGO_CACHE_JWT, jwt);
        }
        Objects.requireNonNull(jwt);
    }

    @Test
    public void jwtTest(){
        String jwt = baseQueryProvider.getJwt();
        Objects.requireNonNull(jwt);
    }

}

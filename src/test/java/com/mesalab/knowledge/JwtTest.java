package com.mesalab.knowledge;

import com.google.common.base.Joiner;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.knowledge.common.config.ArangoConfig;
import com.mesalab.knowledge.common.utils.HttpClientUtils;
import com.mesalab.knowledge.common.utils.JwtCache;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @description: for jwt
 * @author: zhq
 * @create: 2020-07-20
 **/
public class JwtTest extends GalaxyQGWApplicationTests {

    @Autowired
    ArangoConfig arangoConfig;

    /**
     * @Author zhq
     * @Description //TODO arangodb jwt test
     * @Date 2020\7\20 0020
     **/
    @Test
    public void getJwt() {
        Object jwt = JwtCache.get("jwt");
        if (StringUtil.isEmpty(jwt)) {
            Map<String, String> params = new HashMap<>();
            params.put("username", arangoConfig.getUsername());
            params.put("password", arangoConfig.getPassword());
            String res = HttpClientUtils.httpPost(Joiner.on("").join(arangoConfig.getServer(), "/_db/", arangoConfig.getDatabase(), "/_open/auth"), JsonMapper.toJsonString(params));
            Map resMap = (Map) JsonMapper.fromJsonString(res, Map.class);
            jwt = resMap.get("jwt") != null ? "bearer " + resMap.get("jwt") : null;
            JwtCache.put("jwt", jwt);
        }
        Objects.requireNonNull(jwt);
    }
}

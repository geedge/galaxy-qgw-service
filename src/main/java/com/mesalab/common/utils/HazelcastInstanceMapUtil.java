package com.mesalab.common.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.mesalab.qgw.model.basic.QueryCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * TODO
 *
 * @Classname HazelcastInstanceMapUtil
 * @Date 2023/12/5 15:13
 * @Author wWei
 */
@Component
public class HazelcastInstanceMapUtil {

    private static HazelcastInstance hazelcastInstance;
    private static final String AD_HOC_CACHE = "adHocCache";

    @Autowired
    public HazelcastInstanceMapUtil(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        HazelcastInstanceMapUtil.hazelcastInstance = hazelcastInstance;
    }

    public static IMap<String, String> retrieveMap() {
        return hazelcastInstance.getMap(AD_HOC_CACHE);
    }

    public static QueryCache put(String key, QueryCache value) {
        String vStr = JSON.toJSONString(value, JSONWriter.Feature.WriteNulls, JSONWriter.Feature.LargeObject);
        String old = retrieveMap().put(key, vStr);
        return old == null ? null : JSON.parseObject(old, QueryCache.class);
    }

    public static QueryCache get(String key) {
        String v = retrieveMap().get(key);
        return v == null ? null : JSON.parseObject(v, QueryCache.class);
    }

    public static QueryCache remove(String key) {
        String d = retrieveMap().remove(key);
        return d == null ? null : JSON.parseObject(d, QueryCache.class);
    }
}

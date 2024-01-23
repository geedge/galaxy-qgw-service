package com.mesalab.common.utils;

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

    public static IMap<String, QueryCache> retrieveMap() {
        return hazelcastInstance.getMap(AD_HOC_CACHE);
    }

    public static QueryCache put(String key, QueryCache value) {
        return retrieveMap().put(key, value);
    }

    public static QueryCache get(String key) {
        return retrieveMap().get(key);
    }

    public static QueryCache remove(String key) {
        return retrieveMap().remove(key);
    }
}

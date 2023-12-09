package com.mesalab.common.utils;

import com.hazelcast.core.HazelcastInstance;
import com.mesalab.qgw.model.basic.QueryCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentMap;

/**
 * TODO
 *
 * @Classname HazelcastInstanceUtil
 * @Date 2023/12/5 15:13
 * @Author wWei
 */
@Component
public class HazelcastInstanceUtil {

    private static HazelcastInstance hazelcastInstance;

    @Autowired
    public HazelcastInstanceUtil(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        HazelcastInstanceUtil.hazelcastInstance = hazelcastInstance;
    }


    private static ConcurrentMap<String, QueryCache> retrieveMap() {
        return hazelcastInstance.getMap("myCache");
    }

    public static Object put(String key, QueryCache value) {
        return retrieveMap().put(key, value);
    }

    public static QueryCache get(String key) {
        return retrieveMap().get(key);
    }

    public static QueryCache remove(String key) {
        return retrieveMap().remove(key);
    }
}

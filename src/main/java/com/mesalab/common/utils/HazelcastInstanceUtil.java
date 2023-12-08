package com.mesalab.common.utils;

import com.hazelcast.core.HazelcastInstance;
import com.mesalab.qgw.model.basic.QueryCache;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
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
public class HazelcastInstanceUtil implements ApplicationContextAware {
    private static HazelcastInstance hazelcastInstance;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        hazelcastInstance = applicationContext.getBean(HazelcastInstance.class);
    }

    public static HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
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

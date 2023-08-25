package com.mesalab.knowledge.common.utils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import com.geedgenetworks.utils.StringUtil;

/**
 * @description: for jwt cache
 * @author: zhq
 * @create: 2020-07-07
 **/
public class JwtCache {

    private static Cache<String, Object> cache;

    static {
        cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(20, TimeUnit.DAYS)
                .recordStats()
                .build();
    }

    public static Object get(String key) {
        return StringUtil.isNotBlank(key) ? cache.getIfPresent(key) : null;
    }

    public static void put(String key, Object value) {
        if (StringUtil.isNotBlank(key) && value != null) {
            cache.put(key, value);
        }
    }

    public static void remove(String key) {
        if (StringUtil.isNotBlank(key)) {
            cache.invalidate(key);
        }

    }

    public static void remove(List<String> keys) {
        if (StringUtil.isNotEmpty(keys)) {
            cache.invalidateAll(keys);
        }
    }

    public static CacheStats getStats() {
        return cache.stats();
    }
}

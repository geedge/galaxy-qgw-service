package com.mesalab.common.utils;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.mesalab.qgw.service.MetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class SchemaCacheUtils {

    private static final Logger log = LoggerFactory.getLogger(QueryCacheUtils.class);
    private static final  MetadataService metadataService = (MetadataService) SpringContextUtil.getBean("metadataService");

    private static LoadingCache<String, Object> loadingCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats()
            .build(new CacheLoader<String, Object>() {
                @Override
                public Object load(String key) throws Exception{
                    Object value = metadataService.getAllSchema();
                     log.info("loading value by key,key:{}, value:{}", key, value);

                     return value;
                }

            });

    public static Object get(String key) {
        try {
           return loadingCache.get(key);
        } catch(Exception e) {
            log.warn(" Schema Cache ,get key error ", e);
            return null;
        }
    }


    public static void put(String key, Object value) {
        loadingCache.put(key, value);
    }

    public static void remove(String key) {
        loadingCache.invalidate(key);
    }


    public static void removeAll() {
        loadingCache.invalidateAll();
    }

    public static CacheStats getStats() {
        return loadingCache.stats();
    }





}

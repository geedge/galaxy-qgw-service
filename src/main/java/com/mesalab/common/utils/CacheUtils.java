package com.mesalab.common.utils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import com.zdjizhi.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class CacheUtils {

    private static Cache<String,Object> cache;
    private static final Logger log = LoggerFactory.getLogger(CacheUtils.class);

    static {
        cache = CacheBuilder.newBuilder().maximumSize(100000)
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .initialCapacity(10)
                .recordStats()
                .build();
    }


    public  static Object get(String key){

        return StringUtil.isNotBlank(key)?cache.getIfPresent(key):null;
    }


    public static void put(String key,Object value){
        if(StringUtil.isNotBlank(key) && value != null){
            cache.put(key,value);
        }
    }

    public static void remove(String key){

        if(StringUtil.isNotBlank(key)){
            cache.invalidate(key);
        }
    }

    public static void remove(List<String> keys){
        if(StringUtil.isNotEmpty(keys)){
            cache.invalidateAll(keys);
        }

    }

    public static CacheStats getStats() {
        return cache.stats();
    }




}

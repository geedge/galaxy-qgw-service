package com.mesalab.qgw.interceptor;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.google.common.cache.CacheStats;
import com.mesalab.common.utils.HttpHelper;
import com.mesalab.common.utils.QueryCacheUtils;
import com.mesalab.qgw.model.basic.CachedSubmitCheck;
import com.geedgenetworks.utils.StringUtil;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class QuerySubmitInterceptor extends HandlerInterceptorAdapter {

    private static final Log log = LogFactory.get();
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object handler) throws Exception
    {

        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            CachedSubmitCheck submitCheck = handlerMethod.getMethodAnnotation(CachedSubmitCheck.class);
            if (StringUtil.isNotEmpty(submitCheck)) {
                String cacheKey = HttpHelper.generateCacheKey(request);
                if (StringUtil.isBlank(cacheKey)) {
                    return true;
                }
                Object result = QueryCacheUtils.get(cacheKey);
                if (StringUtil.isNotEmpty(result)) {
                    CacheStats stats = QueryCacheUtils.getStats();
                    log.info(" Query Repeat Submit Stat, QEQ_count:{}, HIT_count:{}, HIT_rate:{}, MISS_count:{}",
                            stats.requestCount(), stats.hitCount(), stats.hitRate(), stats.missCount());
                    response.setCharacterEncoding("UTF-8");
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(JSON.toJSONString(result, JSONWriter.Feature.LargeObject));
                    return false;

                }

            }
        }
        return true;
    }





    private UserMethodKey getUserMethodKey(String clientId, String queryString) {

        UserMethodKey k = new UserMethodKey();
        k.setClientId(clientId);
        k.setQueryString(queryString);
        return k;
    }

    private String getClientId() {
        return RequestContextHolder.getRequestAttributes().getSessionId();
    }

    @Data
    private static class UserMethodKey {
        private String clientId;
        private String queryString;

        @Override
        public String toString() {
            return DigestUtil.md5Hex(clientId + queryString);
        }
    }



}

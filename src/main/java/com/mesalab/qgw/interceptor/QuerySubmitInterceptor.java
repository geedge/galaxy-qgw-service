package com.mesalab.qgw.interceptor;

import cn.hutool.core.util.EscapeUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSON;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.cache.CacheStats;
import com.mesalab.common.utils.QueryCacheUtils;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.qgw.model.api.CachedSubmitCheck;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import org.apache.commons.codec.digest.DigestUtils;
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
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        if (handler instanceof HandlerMethod) {

            HandlerMethod handlerMethod = (HandlerMethod) handler;

            CachedSubmitCheck submitCheck = handlerMethod.getMethodAnnotation(CachedSubmitCheck.class);

            if (submitCheck == null || StringUtil.isBlank(request.getQueryString())) {
                return true;
            }

            String clientId = this.getClientId();
            String queryKey = DigestUtil.md5Hex(request.getQueryString());
            log.debug("Query Submit Check,ClientId:{}, QueryKey:{}", clientId, queryKey);
            Object result = QueryCacheUtils.get(queryKey);
            if (StringUtil.isNotEmpty(result)) {
                CacheStats stats = QueryCacheUtils.getStats();
                log.info(" Query Repeat Submit Stat,QEQ_count:{},HIT_count:{},HIT_rate:{}, MISS_count:{}",
                        stats.requestCount(), stats.hitCount(), stats.hitRate(), stats.missCount());
                response.setCharacterEncoding("UTF-8");
                response.setContentType("application/json;charset=UTF-8");
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.getWriter().write(JsonMapper.toJsonString(result));
                return false;

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

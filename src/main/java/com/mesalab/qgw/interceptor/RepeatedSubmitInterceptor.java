package com.mesalab.qgw.interceptor;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.qgw.model.api.RepeatedSubmitCheck;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RepeatedSubmitInterceptor extends HandlerInterceptorAdapter {
    private static final Cache<String, Integer> CACHE = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(1, TimeUnit.SECONDS)
            .recordStats()
            .build();


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        if (handler instanceof HandlerMethod) {

            HandlerMethod handlerMethod = (HandlerMethod) handler;

            RepeatedSubmitCheck submitCheck = handlerMethod.getMethodAnnotation(RepeatedSubmitCheck.class);

            if (submitCheck == null) {

                return true;
            }

            String clientId = this.getClientId();
            String queryString = request.getQueryString();

            UserMethodKey k = this.getUserMethodKey(clientId, queryString);

            log.info("Repeat Submit Check,ClientId:{}, key:{}", clientId, k.toString());

            if (CACHE.getIfPresent(k.toString()) == null) {

                CACHE.put(k.toString(), 1);

                log.info("Repeat Submit Stat,QEQ_count:{},HIT_count{},HIT_rate:{}, MISS_count:{}",
                        CACHE.stats().requestCount(), CACHE.stats().hitCount(), CACHE.stats().hitRate(), CACHE.stats().missCount());

            } else {
                throw  new BusinessException(BaseResultEnum.REQ_FORBIDDEN.getCode(),  "系统检测重复提交，请稍后重试 ！", null);
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
            return DigestUtils.md5Hex(clientId + queryString);
        }
    }



}

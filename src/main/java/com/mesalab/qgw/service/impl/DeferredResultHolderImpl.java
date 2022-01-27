package com.mesalab.qgw.service.impl;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.qgw.service.DeferredResultHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;
import java.util.Map;

@Service("deferredResultHolder")
public class DeferredResultHolderImpl implements DeferredResultHolder<BaseResult> {
    private static final Log log = LogFactory.get();
    private Map<String, DeferredResult<BaseResult>> deferredResults = Maps.newConcurrentMap();
    private static final int DEFAULT_POLLING_INTERVAL_MS = 10000;
    private static final int MIN_POLLING_INTERVAL_MS = 500;

    public DeferredResult<BaseResult> newDeferredResult(String key) {
        return newDeferredResult(key, DEFAULT_POLLING_INTERVAL_MS, null);
    }

    public DeferredResult<BaseResult> newDeferredResult(String key, long timeout) {
        return newDeferredResult(key, timeout, null);
    }

    public DeferredResult<BaseResult> newDeferredResult(String key, Object timeoutResult) {
        return newDeferredResult(key, DEFAULT_POLLING_INTERVAL_MS, timeoutResult);
    }

    @Override
    public DeferredResult<BaseResult> newDeferredResult(String key, long timeout, Object timeoutResult) {
        DeferredResult<BaseResult> deferredResult = new DeferredResult<>(Math.max(timeout, MIN_POLLING_INTERVAL_MS), timeoutResult);
        add(key, deferredResult);
        deferredResult.onCompletion(() -> {
            remove(key);
            log.info("Async Request {} is done.Current DeferredResults has been {} requests.", key, deferredResults.size());
        });

        deferredResult.onError((Throwable t) -> {
            remove(key);
            log.error("Async Request {} is error.Current DeferredResults has been {} requests.", key, deferredResults.size());
        });

        return deferredResult;
    }

    @Override
    public void add(String key, DeferredResult deferredResult) {
        deferredResults.put(key, deferredResult);
    }

    @Override
    public DeferredResult<BaseResult> get(String key) {
        return deferredResults.get(key);
    }

    @Override
    public void remove(String key) {
        deferredResults.remove(key);
    }

    @Override
    public void handleDeferredData(DeferredResult deferredResult, BaseResult deferredData){
        deferredResult.setResult(deferredData);

    }

}

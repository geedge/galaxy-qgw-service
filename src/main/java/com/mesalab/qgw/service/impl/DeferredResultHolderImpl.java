package com.mesalab.qgw.service.impl;
import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.qgw.service.DeferredResultHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;
import java.util.Map;

@Service("deferredResultHolder")
public class DeferredResultHolderImpl implements DeferredResultHolder<BaseResult> {
    private Map<String, DeferredResult<BaseResult>> deferredResults = Maps.newConcurrentMap();

    public DeferredResult<BaseResult> newDeferredResult(String key) {
        return newDeferredResult(key, 10 * 1000L, null);
    }

    public DeferredResult<BaseResult> newDeferredResult(String key, long timeout) {
        return newDeferredResult(key, timeout, null);
    }

    public DeferredResult<BaseResult> newDeferredResult(String key, Object timeoutResult) {
        return newDeferredResult(key, 10 * 1000L, timeoutResult);
    }

    @Override
    public DeferredResult<BaseResult> newDeferredResult(String key, long timeout, Object timeoutResult) {
        DeferredResult<BaseResult> deferredResult = new DeferredResult<>(timeout, timeoutResult);
        add(key, deferredResult);
        deferredResult.onCompletion(new Runnable() {
            @Override
            public void run() {
                remove(key);
            }
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

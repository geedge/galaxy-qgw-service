package com.mesalab.qgw.service;

import org.springframework.web.context.request.async.DeferredResult;

public interface DeferredResultHolder<BaseResult> {
    DeferredResult<BaseResult> newDeferredResult(String key, long timeout, Object timeoutResult);
    void add(String key, DeferredResult<BaseResult> deferredResult);
    DeferredResult<BaseResult> get(String key);
    void remove(String key);
    void handleDeferredData(DeferredResult deferredResult, BaseResult deferredData);
}

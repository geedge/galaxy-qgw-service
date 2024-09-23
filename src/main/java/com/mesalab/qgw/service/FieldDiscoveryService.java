package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.basic.DSLQueryRequestParam;

import java.util.concurrent.ExecutionException;

/**
 * TODO
 *
 * @Classname FieldDiscoveryService
 * @Date 2024/9/23 14:04
 * @Author wWei
 */
public interface FieldDiscoveryService {
    void asyncRun(DSLQueryRequestParam request) throws ExecutionException;

    void validate(DSLQueryRequestParam request);
}

package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.basic.DSLQueryRequestParam;

import java.util.List;
import java.util.Map;

/**
 * TODO
 *
 * @Classname TrafficSpectrumDslService
 * @Date 2024/5/10 10:13
 * @Author wWei
 */
public interface TrafficSpectrumDslService {
    BaseResult run(DSLQueryRequestParam dslQueryRequestParam);
    List<Map<String, Object>> summary(DSLQueryRequestParam dslQueryRequestParam);

    List<Map<String, Object>> uniqIpsStat(DSLQueryRequestParam dslQueryRequestParam);

    List<Map<String, Object>> topServerIpAndServerDomain(DSLQueryRequestParam dslQueryRequestParam);

    List<Map<String, Object>> internalExternalDimensionMetricsStat(DSLQueryRequestParam dslQueryRequestParam);

    List<Map<String, Object>> networkThroughputTrend(DSLQueryRequestParam dslQueryRequestParam);

}

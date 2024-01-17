package com.mesalab.network.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.network.dsl.DSLObject;
import com.mesalab.network.model.Protocol;

import java.util.List;
import java.util.Map;

/**
 * @author wangwei
 * @version 1.0
 * @date 2020/6/30 4:20 下午
 */


public interface NetworkMonitorService {

    BaseResult buildProtocolTree(DSLObject dslObject);
    BaseResult buildProtocolDataRateSummary(DSLObject dslObject);
    BaseResult buildNetworkOverviewResult(DSLObject dslObject);
    List<Protocol> buildFlatStructure(List<Map> data);
    List<Protocol> buildHierarchicalStructure(List<Protocol> nodes);
    BaseResult buildAppData(DSLObject dslObject);
    BaseResult buildInternalIPData(DSLObject dslObject);

    BaseResult buildAppDataRate(DSLObject dslObject);

    BaseResult buildAppTraffic(DSLObject dslObject);
}

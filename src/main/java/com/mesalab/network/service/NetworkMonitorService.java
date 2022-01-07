package com.mesalab.network.service;

import com.mesalab.common.base.BaseResult;
import com.mesalab.network.dsl.DSLObject;
import com.mesalab.network.model.protocol.ProtocolTree;

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
    List<ProtocolTree> buildFlatStructure(List<Map> data);
    List<ProtocolTree> buildHierarchicalStructure(List<ProtocolTree> nodes);

}

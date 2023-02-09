package com.mesalab.tsg.service;

import com.mesalab.tsg.entity.NetWorkDSLObject;
import com.mesalab.tsg.entity.ProtocolTree;
import com.mesalab.common.entity.BaseResult;

import java.util.List;
import java.util.Map;

/**
 * @author wangwei
 * @version 1.0
 * @date 2020/6/30 4:20 下午
 */


public interface NetworkMonitorService {

    BaseResult buildProtocolTree(NetWorkDSLObject dslObject);
    BaseResult buildProtocolDataRateSummary(NetWorkDSLObject dslObject);
    BaseResult buildNetworkOverviewResult(NetWorkDSLObject dslObject);
    List<ProtocolTree> buildFlatStructure(List<Map> data);
    List<ProtocolTree> buildHierarchicalStructure(List<ProtocolTree> nodes);
    BaseResult buildAppData(NetWorkDSLObject dslObject);
    BaseResult buildInternalIPData(NetWorkDSLObject dslObject);

    BaseResult buildAppDataRate(NetWorkDSLObject dslObject);

    BaseResult buildAppTraffic(NetWorkDSLObject dslObject);
}

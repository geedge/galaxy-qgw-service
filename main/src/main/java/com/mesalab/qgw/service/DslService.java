package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.network.model.protocol.ProtocolTree;
import com.mesalab.qgw.model.basic.DSLProfile;

import java.util.List;
import java.util.Map;


/**
 * TODO
 *
 * @Classname DSLService
 * @Date 2023/11/27 10:36
 * @Author wWei
 */
public interface DslService {

    /**
     * Desc: Application and Protocol Summary
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/11/27 10:40
     */
    BaseResult appAndProtocolSummary(DSLProfile dslProfile);

    /**
     * Desc: application and protocol tree composition
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/11/28 10:38
     */
    BaseResult appAndProtocolTreeComposition(DSLProfile dslProfile);

    /**
     * Desc: application and protocol tree throughput
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/11/28 10:38
     */
    BaseResult applicationAndProtocolTreeThroughput(DSLProfile dslProfile);

    /**
     * Desc: top apps
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/11/28 10:39
     */
    BaseResult applicationAndProtocolTopApp(DSLProfile dslProfile);

    /**
     * Desc: app-related internal ips
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/11/28 10:40
     */
    BaseResult applicationAndProtocolAppRelatedInternalIps(DSLProfile dslProfile);

    /**
     * Desc: app-throughput
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/11/28 10:40
     */
    BaseResult applicationAndProtocolAppThroughput(DSLProfile dslProfile);

    /**
     * Desc: app summary
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/11/28 10:41
     */
    BaseResult applicationAndProtocolAppSummary(DSLProfile dslProfile);

    List<ProtocolTree> buildFlatStructure(List<Map> protocolData);

    List<ProtocolTree> buildHierarchicalStructure(List<ProtocolTree> nodes);


}

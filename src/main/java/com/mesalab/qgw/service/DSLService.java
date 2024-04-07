package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.dsl.LiveChartProtocol;
import com.mesalab.qgw.model.basic.DSLQueryContext;

import java.util.List;
import java.util.Map;


/**
 * TODO
 *
 * @Classname DSLService
 * @Date 2023/11/27 10:36
 * @Author wWei
 */
public interface DSLService {

    /**
     * Desc: exec DSL
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/12/11 15:54
     */
    BaseResult execDsl(DSLQueryContext dslProfile, boolean isDryRun);

    /**
     * Desc: Application and Protocol Summary
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/11/27 10:40
     */
    BaseResult appAndProtocolSummary(DSLQueryContext dslProfile, boolean isDryRun);

    /**
     * Desc: application and protocol tree composition
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/11/28 10:38
     */
    BaseResult appAndProtocolTreeComposition(DSLQueryContext dslProfile, boolean isDryRun);

    /**
     * Desc: application and protocol tree throughput
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/11/28 10:38
     */
    BaseResult applicationAndProtocolTreeThroughput(DSLQueryContext dslProfile, boolean isDryRun);

    /**
     * Desc: top apps
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/11/28 10:39
     */
    BaseResult applicationAndProtocolTopApp(DSLQueryContext dslProfile, boolean isDryRun);

    /**
     * Desc: app-related internal ips
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/11/28 10:40
     */
    BaseResult applicationAndProtocolAppRelatedInternalIps(DSLQueryContext dslProfile, boolean isDryRun);

    /**
     * Desc: app-throughput
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/11/28 10:40
     */
    BaseResult applicationAndProtocolAppThroughput(DSLQueryContext dslProfile, boolean isDryRun);

    /**
     * Desc: app summary
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/11/28 10:41
     */
    BaseResult applicationAndProtocolAppSummary(DSLQueryContext dslProfile, boolean isDryRun);

    /**
     * Desc: Get Subscriber ID Relate Client IPs
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/11/30 11:31
     */
    BaseResult realTimeDataAnalyticsSubscriberIdRelateIp(DSLQueryContext dslProfile);

    /**
     * Desc: GTP-C Mobile Identities AND APNs Relate TEID
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/11/30 11:35
     */
    BaseResult realTimeDataAnalyticsMobileIdentityRelateTeid(DSLQueryContext dslProfile);

    /**
     * Desc: IP Learning （FQDN-IP）
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/12/1 11:04
     */
    BaseResult ipLearningFqdnRelateIp(DSLQueryContext dslProfile, boolean isDryRun);

    /**
     * Desc: IP Learning Active IP
     *
     * @param dslProfile
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2023/12/1 11:04
     */
    BaseResult ipLearningActiveIp(DSLQueryContext dslProfile, boolean isDryRun);

    List<LiveChartProtocol> buildFlatStructure(List<Map> protocols);

    List<LiveChartProtocol> buildHierarchicalStructure(List<LiveChartProtocol> nodes);

}

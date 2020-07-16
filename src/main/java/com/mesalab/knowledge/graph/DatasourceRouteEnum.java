package com.mesalab.knowledge.graph;

import com.mesalab.knowledge.router.FqdnAQLProviderImpl;
import lombok.AllArgsConstructor;

/**
 * @description: for datasource
 * @author: zhq
 * @create: 2020-07-06
 **/
@AllArgsConstructor
public enum DatasourceRouteEnum {

    /**
     * 对应 Arangodb aql
     **/
    IP_LEARNING_VIEW("IP_LEARNING_VIEW", new FqdnAQLProviderImpl(), "let FQS = (" +
            "for doc in FQDN " +
            "filter (" +
                "@likeFilter )" +
            "for fq2fq in " +
                "1..@depth " +
            "outbound doc._id R_SAME_ORIGIN_FQDN2FQDN " +
            "return distinct fq2fq) " +
            "for fq in FQS " +
            "for v,e,p in 1..1 outbound fq._id graph 'FQDN_IP_GRAPH' " +
                " filter @filter " +
            "return distinct p"),

    IP_VIEW("IP_VIEW", new FqdnAQLProviderImpl(), "for doc in IP\n" +

            "sort (doc.CLIENT_BYTES_SUM+doc.SERVER_BYTES_SUM) $direction\n" +
            "limit $limit\n" +

            "return doc"),

    SUBSCRIBER_ID_VIEW("SUBSCRIBER_ID_VIEW", new FqdnAQLProviderImpl(), "for doc in SUBSCRIBER_ID " +
            "sort doc.LAST_FOUND_TIME desc " +
            "limit $limit " +
            "return doc");

    private String view;
    private QLProvider router;
    private String aqlSchema;

    public String getName() {
        return view;
    }
    public QLProvider getRouter(){
        return router;
    }
    public String getAqlSchema() {
        return aqlSchema;
    }


    public static DatasourceRouteEnum match(String name, DatasourceRouteEnum defaultItem) {
        if (name != null) {
            for (DatasourceRouteEnum item : DatasourceRouteEnum.values()) {
                if (item.view.equals(name)) {
                    return item;
                }
            }
        }
        return defaultItem;
    }
}

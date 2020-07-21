package com.mesalab.knowledge.enums;

import com.mesalab.knowledge.router.IpAQLProviderImpl;
import com.mesalab.knowledge.router.QLProvider;
import com.mesalab.knowledge.router.FqdnAQLProviderImpl;
import com.mesalab.knowledge.router.SubscriberIdAQLProviderImpl;
import lombok.AllArgsConstructor;

/**
 * @description: for datasource
 * @author: zhq
 * @create: 2020-07-06
 **/
@AllArgsConstructor
public enum DataSourceRouteEnum {

    IP_LEARNING_VIEW("IP_LEARNING_VIEW", new FqdnAQLProviderImpl()),

    IP_VIEW("IP_VIEW", new IpAQLProviderImpl()),

    SUBSCRIBER_ID_VIEW("SUBSCRIBER_ID_VIEW", new SubscriberIdAQLProviderImpl());

    private String view;
    private QLProvider router;

    public String getView() {
        return view;
    }

    public QLProvider getRouter() {
        return router;
    }


    public static DataSourceRouteEnum match(String name) {
        if (name != null) {
            for (DataSourceRouteEnum item : DataSourceRouteEnum.values()) {
                if (item.view.equals(name)) {
                    return item;
                }
            }
        }
        return null;
    }
}

package com.mesalab.knowledge.enums;

import lombok.AllArgsConstructor;

/**
 * @description: for datasource
 * @author: zhq
 * @create: 2020-07-06
 **/
@AllArgsConstructor
public enum DataSourceEnum {


    IP_LEARNING_VIEW("IP_LEARNING_VIEW"),

    IP_VIEW("IP_VIEW"),

    SUBSCRIBER_ID_VIEW("SUBSCRIBER_ID_VIEW");

    private String view;

    public String getView() {
        return view;
    }

    public static DataSourceEnum match(String name) {
        if (name != null) {
            for (DataSourceEnum item : DataSourceEnum.values()) {
                if (item.view.equals(name)) {
                    return item;
                }
            }
        }
        return null;
    }
}

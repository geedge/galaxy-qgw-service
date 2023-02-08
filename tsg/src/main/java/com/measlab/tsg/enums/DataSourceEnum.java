package com.measlab.tsg.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @description: for datasource
 * @author: zhq
 * @create: 2020-07-06
 **/
@Getter
@AllArgsConstructor
public enum DataSourceEnum {


    IP_LEARNING_VIEW("IP_LEARNING_VIEW"),

    IP_VIEW("IP_VIEW"),

    SUBSCRIBER_ID_VIEW("SUBSCRIBER_ID_VIEW");

    private String view;

}

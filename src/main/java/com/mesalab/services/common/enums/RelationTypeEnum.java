package com.mesalab.services.common.enums;

import com.mesalab.cn.common.enums.LogicTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RelationTypeEnum {
    SUBSCRIBERIDPOOL("subscriberidpool","SUBSCRIBER_ID_VIEW","tsg_galaxy.relation_account_framedip","SELECT ROWKEY AS SUBSCRIBER_ID,framed_ip AS IP,last_found_time AS FIRST_FOUND_TIME,last_found_time AS LAST_FOUND_TIME FROM {0} WHERE 1=1 {1} LIMIT {2}");

    private final String type;
    private final String view;
    private final String datasource;
    private String sqlTemplate;

    public static RelationTypeEnum getByType(String type) {
        for (RelationTypeEnum constants : values()) {
            if (constants.type.equalsIgnoreCase(type)) {
                return constants;
            }
        }
        return null;
    }
}

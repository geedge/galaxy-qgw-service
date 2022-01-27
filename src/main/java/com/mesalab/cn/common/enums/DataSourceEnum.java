package com.mesalab.cn.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author wangwei
 * @description:
 * @date 2020/11/12 5:39 下午
 */
@Getter
@AllArgsConstructor
public enum DataSourceEnum {
    TUNNEL("tunnel", "闭区间间隔"),
    ENTITY_DETAIL_APP_VIEW("entity_detail_app_view", "根据app查询实体"),
    ENTITY_DETAIL_DOMAIN_VIEW("entity_detail_domain_view", "根据domain查询实体"),
    ENTITY_DETAIL_IP_VIEW("entity_detail_ip_view", "根据ip查询实体")
    ;

    private String type;
    private String name;

    public static DataSourceEnum getByType(String type) {
        for (DataSourceEnum constants : values()) {
            if (constants.type.equalsIgnoreCase(type)) {
                return constants;
            }
        }
        return null;
    }
}

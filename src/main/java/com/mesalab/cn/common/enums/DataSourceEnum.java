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
    ENTITY_DETAIL_APP_ID_VIEW("entity_detail_app_id_view", "根据app_id查询实体"),
    ENTITY_DETAIL_DOMAIN_VIEW("entity_detail_domain_view", "根据domain查询实体"),
    ENTITY_DETAIL_SERVER_IP_VIEW("entity_detail_server_ip_view", "根据server_ip查询实体"),
    ENTITY_DETAIL_CLIENT_IP_VIEW("entity_detail_client_ip_view", "根据client_ip查询实体"),
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

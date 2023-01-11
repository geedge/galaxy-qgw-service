package com.mesalab.recommendation.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author wWei
 * @description:
 * @date 2020/12/15 4:58 下午
 */
@Getter
@AllArgsConstructor
public enum CDQueryType {
    TUNNEL("tunnel", "sd_tunnel_proto_cnt_feature"),
    BANNED_APP("bannedApp", "sd_banned_app_cnt_feature"),
    EXTERNAL_CONTROL("externalControl", "sd_external_control_cnt_feature"),
    SHARE_NET("shareNet", "sd_multi_access_cnt_feature"),
    SHARE_KEY("shareKey", "sd_share_key_feature"),

    ENTITY_DETAIL("entityDetail", "entity_detail");
    private String queryType;
    private String dataSource;

    public static CDQueryType getByQueryType(String queryType) {
        for (CDQueryType constants : values()) {
            if (constants.queryType.equalsIgnoreCase(queryType)) {
                return constants;
            }
        }
        return null;
    }
}

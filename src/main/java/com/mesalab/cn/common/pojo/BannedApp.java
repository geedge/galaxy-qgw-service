package com.mesalab.cn.common.pojo;

import lombok.Data;

import java.util.List;

/**
 * @author wWei
 * @description:
 * @date 2020/12/17 15:52 下午
 */
@Data
public class BannedApp {

    private String userId;
    private List<UserIp> userIps;

    @Data
    public static class UserIp {
        private String ip;
        private List<IpAccessInfo> ipAccessInfo;

        @Data
        public static class IpAccessInfo {
            private String type;
            private List<Feature> feature;

            @Data
            public static class Feature {
                private String featureKey;
                private String uniqueCnt;
            }
        }
    }
}

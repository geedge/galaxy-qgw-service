package com.mesalab.cn.common.pojo;

import com.google.common.collect.Lists;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author wangwei
 * @description:
 * @date 2020/11/24 2:52 下午
 */
@Data
public class ShareNet {

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
                private HashMap analyseResult;
                private List<Map<String, Object>> countList = Lists.newArrayList();
            }
        }
    }
}

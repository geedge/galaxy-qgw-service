package com.mesalab.recommendation.entity.pojo;

import java.util.List;

/**
 * @author wWei
 * @description:
 * @date 2020/12/17 15:52 下午
 */
public class BannedApp {

    private String userId;
    private List<UserIp> userIps;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<UserIp> getUserIps() {
        return userIps;
    }

    public void setUserIps(List<UserIp> userIps) {
        this.userIps = userIps;
    }

    public static class UserIp {
        private String ip;
        private List<IpAccessInfo> ipAccessInfo;

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public List<IpAccessInfo> getIpAccessInfo() {
            return ipAccessInfo;
        }

        public void setIpAccessInfo(List<IpAccessInfo> ipAccessInfo) {
            this.ipAccessInfo = ipAccessInfo;
        }
    }

    public static class IpAccessInfo {
        private String type;
        private List<Feature> feature;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<Feature> getFeature() {
            return feature;
        }

        public void setFeature(List<Feature> feature) {
            this.feature = feature;
        }
    }

    public static class Feature {
        private String featureKey;
        private String uniqueCnt;

        public String getFeatureKey() {
            return featureKey;
        }

        public void setFeatureKey(String featureKey) {
            this.featureKey = featureKey;
        }

        public String getUniqueCnt() {
            return uniqueCnt;
        }

        public void setUniqueCnt(String uniqueCnt) {
            this.uniqueCnt = uniqueCnt;
        }
    }
}

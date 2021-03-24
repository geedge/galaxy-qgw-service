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
public class ShareNet {

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
        private HashMap analyseResult;
        private List<Map<String, Object>> countList = Lists.newArrayList();

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

        public HashMap getAnalyseResult() {
            return analyseResult;
        }

        public void setAnalyseResult(HashMap analyseResult) {
            this.analyseResult = analyseResult;
        }

        public List<Map<String, Object>> getCountList() {
            return countList;
        }

        public void setCountList(List<Map<String, Object>> countList) {
            this.countList = countList;
        }
    }
}

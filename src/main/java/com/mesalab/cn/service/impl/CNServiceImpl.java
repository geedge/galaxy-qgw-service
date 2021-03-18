package com.mesalab.cn.service.impl;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.cn.common.pojo.*;
import com.mesalab.cn.service.CNService;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.ua.analyser.UaAnalyser;
import com.mesalab.ua.analyser.enums.UserInfo;
import com.mesalab.ua.analyser.impl.UaAnalyserImpl;
import com.google.common.base.Joiner;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.exception.BusinessException;
import com.zdjizhi.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Date: 2020-11-20 10:05
 * @Author : wWei
 * @ClassName : CNServiceImpl
 * @Description : Druid数据查询引擎实现
 */
@Service("druidEngineService")
public class CNServiceImpl implements CNService {
    private static final Log log = LogFactory.get();

    @Autowired
    private CNParser cnParser;

    @Autowired
    private ApiService apiService;

    @Override
    public BaseResult shareNetQuery(DSLObject dslObject) throws BusinessException {
        dslObject.getQuery().getParameters().setGranularity("user_id,ip");
        String executeSql = cnParser.buildShareNetSQL(dslObject);
        ApiParam apiParam = new ApiParam();
        apiParam.setQuery(executeSql);
        BaseResult baseResult = apiService.executeQuery(apiParam);
        if (!baseResult.isSuccess()) {
            return baseResult;
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        List<ShareNet> result = formatShareNetResult(data, dslObject);
        for (ShareNet shareNet : result) {
            filterResult(dslObject, shareNet);
        }
        removeResult(result);
        removeResult(result);
        removeResult(result);

        Integer limit;
        if (StringUtil.isNotEmpty(limit = dslObject.getQuery().getParameters().getLimit()) && result.size() > limit) {
            result = result.subList(0, limit);
        }
        return BaseResultGenerator.success(result);
    }

    @Override
    public BaseResult bannedAppQuery(DSLObject dslObject) throws BusinessException {
        dslObject.getQuery().getParameters().setGranularity("user_id,ip");
        String executeSql = cnParser.bannedAppSQL(dslObject);
        ApiParam apiParam = new ApiParam();
        apiParam.setQuery(executeSql);
        BaseResult baseResult = apiService.executeQuery(apiParam);
        if (!baseResult.isSuccess()) {
            return baseResult;
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        List<BannedApp> result = formatBannedAppResult(data, dslObject);

        Integer limit;
        if (StringUtil.isNotEmpty(limit = dslObject.getQuery().getParameters().getLimit()) && result.size() > limit) {
            result = result.subList(0, limit);
        }
        return BaseResultGenerator.success(result);
    }

    private List<BannedApp> formatBannedAppResult(List<Map<String, Object>> data, DSLObject dslObject) {
        List<BannedApp> result = new ArrayList<>();
        DSLObject.Query.Parameters parameters = dslObject.getQuery().getParameters();
        DSLObject.Query.Parameters.Range range = parameters.getRange();
        for (Map<String, Object> datum : data) {
            List<BannedApp.UserIp.IpAccessInfo.Feature> featureList = new ArrayList<>();
            BannedApp.UserIp.IpAccessInfo.Feature feature;
            for (Condition condition : range.getCondition()) {
                feature = new BannedApp.UserIp.IpAccessInfo.Feature();
                feature.setFeatureKey(condition.getFieldKey());
                feature.setUniqueCnt(String.valueOf(datum.get(condition.getFieldKey())));
                featureList.add(feature);
            }
            List<BannedApp.UserIp.IpAccessInfo> ipAccessInfoList = new ArrayList<>();
            BannedApp.UserIp.IpAccessInfo ipAccessInfo = new BannedApp.UserIp.IpAccessInfo();
            ipAccessInfo.setType(dslObject.getQuery().getQueryType());
            ipAccessInfo.setFeature(featureList);
            ipAccessInfoList.add(ipAccessInfo);

            List<BannedApp.UserIp> userIpList = new ArrayList<>();
            BannedApp.UserIp userIp = new BannedApp.UserIp();
            userIp.setIp(String.valueOf(datum.get("ip")));
            userIp.setIpAccessInfo(ipAccessInfoList);
            userIpList.add(userIp);

            BannedApp bannedApp = new BannedApp();
            bannedApp.setUserId(String.valueOf(datum.get("user_id")));
            bannedApp.setUserIps(userIpList);
            result.add(bannedApp);
        }
        result = mergeUserIpOfBannedApp(result);
        return result;
    }

    private List<BannedApp> mergeUserIpOfBannedApp(List<BannedApp> dataList) {
        List<BannedApp> result = new ArrayList<>();
        data:
        for (BannedApp bannedApp : dataList) {
            for (BannedApp datum : result) {
                if (datum.getUserId().equals(bannedApp.getUserId())) {
                    datum.getUserIps().add(bannedApp.getUserIps().get(0));
                    continue data;
                }
            }
            result.add(bannedApp);
        }
        return result;
    }


    private void removeResult(List<ShareNet> data) {
        Iterator<ShareNet> iterator = data.iterator();
        while (iterator.hasNext()) {
            ShareNet next = iterator.next();
            if (CollectionUtils.isEmpty(next.getUserIps())) {
                iterator.remove();
            }
            List<ShareNet.UserIp> userIpList = next.getUserIps();
            Iterator<ShareNet.UserIp> userIpIterator = userIpList.iterator();
            while (userIpIterator.hasNext()) {
                ShareNet.UserIp next1 = userIpIterator.next();
                if (CollectionUtils.isEmpty(next1.getIpAccessInfo())) {
                    userIpIterator.remove();
                }
                List<ShareNet.UserIp.IpAccessInfo> ipAccessInfoList = next1.getIpAccessInfo();
                Iterator<ShareNet.UserIp.IpAccessInfo> ipAccessInfoIterator = ipAccessInfoList.iterator();
                while (ipAccessInfoIterator.hasNext()) {
                    ShareNet.UserIp.IpAccessInfo t = ipAccessInfoIterator.next();
                    if (StringUtil.isEmpty(t.getFeature())) {
                        ipAccessInfoIterator.remove();
                    }
                }
            }
        }
    }

    private void filterResult(DSLObject dslObject, ShareNet shareNet) {
        DSLObject.Query.Parameters parameters = dslObject.getQuery().getParameters();
        DSLObject.Query.Parameters.Distinct distinct = parameters.getDistinct();
        DSLObject.Query.Parameters.Range range = parameters.getRange();
        List<Condition> condition = new ArrayList<>();
        if (StringUtil.isNotEmpty(distinct) && !CollectionUtils.isEmpty(distinct.getCondition())) {
            condition.addAll(distinct.getCondition());
        }
        if (StringUtil.isNotEmpty(range) && !CollectionUtils.isEmpty(range.getCondition())) {
            condition.addAll(range.getCondition());
        }
        for (int i = 0; i < shareNet.getUserIps().size(); i++) {
            List<ShareNet.UserIp.IpAccessInfo> ipAccessInfoList = shareNet.getUserIps().get(i).getIpAccessInfo();
            for (int i1 = 0; i1 < ipAccessInfoList.size(); i1++) {
                List<ShareNet.UserIp.IpAccessInfo.Feature> featureList = ipAccessInfoList.get(i1).getFeature();
                Iterator<ShareNet.UserIp.IpAccessInfo.Feature> iterator = featureList.iterator();
                while (iterator.hasNext()) {
                    ShareNet.UserIp.IpAccessInfo.Feature next = iterator.next();
                    Condition fieldFilter = null;
                    for (Condition t : condition) {
                        if (next.getFeatureKey().equalsIgnoreCase(t.getFieldKey())) {
                            fieldFilter = t;
                        }
                    }
                    long uniqueCnt = Long.parseLong(next.getUniqueCnt());
                    long filterValue = Long.parseLong(fieldFilter.getFieldValues().get(0).toString());
                    if (((uniqueCnt - filterValue) < 0)
                            && !fieldFilter.getType().equalsIgnoreCase("lt")) {
                        iterator.remove();
                    }
                    if (((uniqueCnt - filterValue) == 0)
                            && !(fieldFilter.getType().equalsIgnoreCase("eq")
                            || fieldFilter.getType().equalsIgnoreCase("ge")
                            || fieldFilter.getType().equalsIgnoreCase("le"))) {
                        iterator.remove();
                    }
                    if (((uniqueCnt - filterValue) > 0)
                            && !(fieldFilter.getType().equalsIgnoreCase("gt") || fieldFilter.getType().equalsIgnoreCase("ge"))) {
                        iterator.remove();
                    }
                }
            }
        }
    }


    private List<ShareNet> formatShareNetResult(List<Map<String, Object>> data, DSLObject dslObject) {
        List<ShareNet> shareNets = new ArrayList<>();
        DSLObject.Query.Parameters parameters = dslObject.getQuery().getParameters();
        for (Map<String, Object> t : data) {
            List<ShareNet.UserIp.IpAccessInfo.Feature> features = distinctFeature(t, parameters);

            ShareNet.UserIp.IpAccessInfo ipAccessInfo = new ShareNet.UserIp.IpAccessInfo();
            List<ShareNet.UserIp.IpAccessInfo> ipAccessInfoList = new ArrayList<>();
            ipAccessInfo.setType(dslObject.getQuery().getQueryType());
            ipAccessInfo.setFeature(features);
            ipAccessInfoList.add(ipAccessInfo);

            List<ShareNet.UserIp> userIpList = new ArrayList<>();
            ShareNet.UserIp userIp = new ShareNet.UserIp();
            userIp.setIpAccessInfo(ipAccessInfoList);
            userIp.setIp(String.valueOf(t.get("ip")));
            userIpList.add(userIp);
            ShareNet shareNet = new ShareNet();
            shareNet.setUserId(String.valueOf(t.get("user_id")));
            shareNet.setUserIps(userIpList);
            shareNets.add(shareNet);
        }
        shareNets = mergeUserIpOfShareNet(shareNets);
        try {
            uaAnalyse(shareNets);
        } catch (Exception e) {
            log.error("UA解析异常:{}", e);
        }
        return shareNets;
    }

    private List<ShareNet.UserIp.IpAccessInfo.Feature> distinctFeature(Map<String, Object> data, DSLObject.Query.Parameters parameters) {
        List<ShareNet.UserIp.IpAccessInfo.Feature> features = new ArrayList<>();
        if (StringUtil.isEmpty(parameters.getDistinct()) || CollectionUtils.isEmpty(parameters.getDistinct().getCondition())) {
            return features;
        }
        List<Condition> condition = parameters.getDistinct().getCondition();
        for (Condition term : condition) {
            if (CNParser.UA_FILTER.contains(term.getFieldKey())) {
                continue;
            }
            ShareNet.UserIp.IpAccessInfo.Feature feature = new ShareNet.UserIp.IpAccessInfo.Feature();
            feature.setFeatureKey(String.valueOf(term.getFieldKey()));
            feature.setUniqueCnt(String.valueOf(data.get(term.getFieldKey().concat("UniqueCnt"))));
            HashMap<String, Object> map = new HashMap<>(16);
            map.put("value", data.get(term.getFieldKey()));
            map.put("frequency", data.get("hits"));
            feature.getCountList().add(map);
            features.add(feature);
        }
        return features;
    }

    private void uaAnalyse(List<ShareNet> shareNets) throws SQLException {
        for (ShareNet shareNet : shareNets) {
            for (ShareNet.UserIp userIp : shareNet.getUserIps()) {
                for (ShareNet.UserIp.IpAccessInfo ipAccessInfo : userIp.getIpAccessInfo()) {
                    Iterator<ShareNet.UserIp.IpAccessInfo.Feature> iteratorFeature = ipAccessInfo.getFeature().iterator();
                    while (iteratorFeature.hasNext()) {
                        ShareNet.UserIp.IpAccessInfo.Feature feature = iteratorFeature.next();
                        uaParse(shareNet.getUserId(), userIp.getIp(), feature);
                    }
                }
            }
        }
    }

    private void uaParse(String userId, String userIp, ShareNet.UserIp.IpAccessInfo.Feature feature) throws SQLException {
        if (!"UA".equalsIgnoreCase(feature.getFeatureKey())) {
            return;
        }
        List<String> uaList = new ArrayList<>();
        Iterator<Map<String, Object>> iterator = feature.getCountList().iterator();
        while (iterator.hasNext()) {
            Map<String, Object> next = iterator.next();
            if (StringUtil.isEmpty(next.get("value"))) {
                iterator.remove();
                feature.setUniqueCnt(String.valueOf(feature.getCountList().size()));
                continue;
            }
            uaList.add(String.format("'%s':%s", next.get("value"), next.get("frequency")));
        }
        if (uaList.isEmpty()) {
            return;
        }
        String inputString = userId + "\t" +
                userIp + "\t" +
                (System.currentTimeMillis() / 1000) + "\t" +
                Joiner.on(", ").join(uaList);
        UaAnalyser uaAnalyser = new UaAnalyserImpl();
        UserInfo userInfo = null;
        HashMap<String, Object> t = new HashMap<>();
        try {
            userInfo = uaAnalyser.getUserinfo(inputString);
        } catch (Exception e) {
            log.error("UA解析异常:{}", e.getMessage());
        }
        if (StringUtil.isEmpty(userInfo)) {
            feature.setAnalyseResult(t);
            return;
        }
        t.put("osCnt", userInfo.getOsCnt());
        t.put("osList", userInfo.getOsList());
        t.put("browserList", userInfo.getBrowserList());
        t.put("browserCnt", userInfo.getBrowserCnt());
        t.put("deviceCnt", userInfo.getDeviceCnt());
        t.put("deviceList", userInfo.getDeviceList());
        t.put("terminalCnt", userInfo.getTerminalCnt());
        t.put("terminalList", userInfo.getTerminalList());
        t.put("terminalCatCnt", userInfo.getTerminalCatCnt());
        t.put("terminalCatList", userInfo.getTerminalCatList());
        feature.setAnalyseResult(t);
    }

    private List<ShareNet> mergeUserIpOfShareNet(List<ShareNet> dataList) {
        List<ShareNet> result = new ArrayList<>();
        dataShareNet:
        for (ShareNet data : dataList) {
            for (ShareNet shareNet : result) {
                if (shareNet.getUserId().equals(data.getUserId())) {
                    for (ShareNet.UserIp userIp : shareNet.getUserIps()) {
                        if (userIp.getIp().equals(data.getUserIps().get(0))) {
                            dataFeature:
                            for (ShareNet.UserIp.IpAccessInfo.Feature dataFeature : data.getUserIps().get(0).getIpAccessInfo().get(0).getFeature()) {
                                for (ShareNet.UserIp.IpAccessInfo.Feature feature : userIp.getIpAccessInfo().get(0).getFeature()) {
                                    if (feature.getFeatureKey().equals(dataFeature)) {
                                        feature.getCountList().addAll(dataFeature.getCountList());
                                        List<Map<String, Object>> value = merge(feature.getCountList(), "value");
                                        feature.setCountList(value);
                                        feature.setUniqueCnt(String.valueOf(value.size()));
                                        continue dataFeature;
                                    }
                                }
                                userIp.getIpAccessInfo().get(0).getFeature().add(dataFeature);
                                continue dataFeature;
                            }
                        }
                    }
                    shareNet.getUserIps().add(data.getUserIps().get(0));
                    continue dataShareNet;
                }
            }
            result.add(data);
        }
        return result;
    }

    public List<Map<String, Object>> merge(List<Map<String, Object>> m1, String mergeKey) {
        Set<String> set = new HashSet<>();
        return m1.stream()
                .filter(map -> map.get(mergeKey) != null)
                .collect(Collectors.groupingBy(o -> {
                    //暂存所有key
                    set.addAll(o.keySet());
                    //按mergeKey分组
                    return o.get(mergeKey).toString();
                }))
                .entrySet().stream().map(o -> {
                    //合并
                    Map<String, Object> map = o.getValue().stream().flatMap(m -> {
                        return m.entrySet().stream();
                    }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b));
                    //为没有的key赋值0
                    set.stream().forEach(k -> {
                        if (!map.containsKey(k)) map.put(k, 0);
                    });
                    return map;
                }).collect(Collectors.toList());
    }
}

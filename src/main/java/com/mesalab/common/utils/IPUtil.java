package com.mesalab.common.utils;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.mesalab.network.common.Constants;
import com.mesalab.qgw.model.api.DruidIoHttpSource;
import com.mesalab.qgw.model.api.HttpConfig;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.mesalab.services.common.entity.KnowledgeBase;
import com.mesalab.services.common.entity.KnowledgeConstant;
import com.mesalab.services.common.entity.KnowledgeVersion;
import com.mesalab.services.configuration.HosConfig;
import com.zdjizhi.utils.IpLookupV2;
import com.zdjizhi.utils.StringUtil;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.beans.BeansException;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IP工具类
 *
 * @author Darnell
 */
public class IPUtil{

    private static final Log log = LogFactory.get();

    private static IpLookupV2 ipLookup;

    static Map<String, String> updateMap = new HashMap<>();

    private static HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");
    private static HosConfig hosConfig = (HosConfig) SpringContextUtil.getBean("hosConfig");
    private static HttpConfig httpConfig = (HttpConfig) SpringContextUtil.getBean("httpConfig");


    public void initIpLookup(String ip_v4, String ip_v6, String ip_private_v4, String ip_private_v6) {
        ipLookup = new IpLookupV2.Builder(false)
                .loadDataFileV4(ip_v4)
                .loadDataFileV6(ip_v6)
                .loadDataFilePrivateV4(ip_private_v4)
                .loadDataFilePrivateV6(ip_private_v6)
                .build();
    }

    public static String getGeo(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.latLngLookup(ip);
    }

    public static String getCityDetail(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.cityLookupDetail(ip);
    }

    public static String getCountry(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.countryLookup(ip);
    }

    public static String getISP(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.ispLookup(ip);
    }

    public static String getOrganization(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.organizationLookup(ip);
    }

    public void loadIpLocation(String content, KnowledgeVersion knowledgeVersion) throws NacosException {
        Map<String, String> map = queryLoadKnowledgeInfo(content, knowledgeVersion);
        loadKnowledge(map);
    }



    public void loadKnowledge(Map<String, String> map) {
        if (ObjectUtil.isNotEmpty(map)) {
            String ipV4path = map.get(Constants.IP_BUILT_IN_V4);
            String ipV6path = map.get(Constants.IP_BUILT_IN_V6);
            String ipPrivateV4path = map.get(Constants.IP_USER_DEFINED_V4);
            String ipPrivateV6path = map.get(Constants.IP_USER_DEFINED_V6);
            String isUpdate = map.get("isUpdate");
            if (StringUtil.isNotEmpty(isUpdate) && StringUtil.equals(isUpdate, "true")) {
                log.info("update ip location knowledge,ipV4path:{},ipV6path:{},ipPrivateV4path:{},ipPrivateV6path:{}",
                        ipV4path, ipV6path, ipPrivateV4path, ipPrivateV6path);
                initIpLookup(ipV4path, ipV6path, ipPrivateV4path, ipPrivateV6path);
            } else {
                log.info("No update required");
            }
        } else {
            initIpLookup(KnowledgeConstant.DAT + File.separator + Constants.IP_BUILT_IN_V4, KnowledgeConstant.DAT + File.separator + Constants.IP_BUILT_IN_V6, KnowledgeConstant.DAT + File.separator + Constants.IP_USER_DEFINED_V4,
                    KnowledgeConstant.DAT + File.separator + Constants.IP_USER_DEFINED_V6);
        }
    }

    public Map<String, String> queryLoadKnowledgeInfo(String content, KnowledgeVersion knowledgeVersion) {

        log.info("update knowledge Base version:{},update knowledge Base content:{}", knowledgeVersion, content);

        if (StringUtil.isNotEmpty(knowledgeVersion) && StringUtil.isNotEmpty(content)) {
            List<KnowledgeBase> KnowledgeBaseList = com.mesalab.common.utils.JsonMapper.fromJsonString(content, List.class, KnowledgeBase.class);
            Map<String, String> map = queryFilePath(knowledgeVersion, KnowledgeBaseList);
            return map;
        }
        return null;
    }

    private Map<String, String> queryFilePath(KnowledgeVersion knowledgeVersion, List<KnowledgeBase> knowledgeBaseList) {

        String ipV4BuiltIn = knowledgeVersion.getIpV4BuiltIn();
        String ipV4UserDefined = knowledgeVersion.getIpV4UserDefined();
        String ipV6BuiltIn = knowledgeVersion.getIpV6BuiltIn();
        String ipV6UserDefined = knowledgeVersion.getIpV6UserDefined();

        updateMap.put("isUpdate", "false");
        Map<String, String> map = new HashMap<>();
        for (KnowledgeBase knowledgeBase : knowledgeBaseList) {
            String name = knowledgeBase.getName();
            String version = knowledgeBase.getVersion();
            String concat = name.concat(":").concat(version);
            String path = KnowledgeConstant.DAT.concat(File.separator).concat(name).concat(".").concat(knowledgeBase.getFormat());
            if (StringUtil.equals(concat, ipV4BuiltIn)) {
                map.put(Constants.IP_BUILT_IN_V4, path);
                download(knowledgeBase);
            } else if (StringUtil.equals(concat, ipV4UserDefined)) {
                map.put(Constants.IP_USER_DEFINED_V4, path);
                download(knowledgeBase);
            } else if (StringUtil.equals(concat, ipV6BuiltIn)) {
                map.put(Constants.IP_BUILT_IN_V6, path);
                download(knowledgeBase);
            } else if (StringUtil.equals(concat, ipV6UserDefined)) {
                map.put(Constants.IP_USER_DEFINED_V6, path);
                download(knowledgeBase);
            } else if (StringUtil.equals(name, ipV4BuiltIn) && StringUtil.equals(version, KnowledgeConstant.LATEST)) {
                map.put(Constants.IP_BUILT_IN_V4, path);
                download(knowledgeBase);
            } else if (StringUtil.equals(name, ipV4UserDefined) && StringUtil.equals(version, KnowledgeConstant.LATEST)) {
                map.put(Constants.IP_USER_DEFINED_V4, path);
                download(knowledgeBase);
            } else if (StringUtil.equals(name, ipV6BuiltIn) && StringUtil.equals(version, KnowledgeConstant.LATEST)) {
                map.put(Constants.IP_BUILT_IN_V6, path);
                download(knowledgeBase);
            } else if (StringUtil.equals(name, ipV6UserDefined) && StringUtil.equals(version, KnowledgeConstant.LATEST)) {
                map.put(Constants.IP_USER_DEFINED_V6, path);
                download(knowledgeBase);
            }
        }

        // 引用本地知识库文件
        String ipV4BuiltInPath = map.get(Constants.IP_BUILT_IN_V4);
        String ipV4UserDefinedPath = map.get(Constants.IP_USER_DEFINED_V4);
        String ipV6BuiltInPath = map.get(Constants.IP_BUILT_IN_V6);
        String ipV6UserDefinedPath = map.get(Constants.IP_USER_DEFINED_V6);
        if (StringUtil.isEmpty(ipV4BuiltInPath)) {
            ipV4BuiltInPath = KnowledgeConstant.DAT + File.separator + Constants.IP_BUILT_IN_V4;
            map.put(Constants.IP_BUILT_IN_V4, ipV4BuiltInPath);
        }
        if (StringUtil.isEmpty(ipV4UserDefinedPath)) {
            ipV4UserDefinedPath = KnowledgeConstant.DAT + File.separator + Constants.IP_BUILT_IN_V6;
            map.put(Constants.IP_USER_DEFINED_V4, ipV4UserDefinedPath);
        }
        if (StringUtil.isEmpty(ipV6BuiltInPath)) {
            ipV6BuiltInPath = KnowledgeConstant.DAT + File.separator + Constants.IP_BUILT_IN_V6;
            map.put(Constants.IP_BUILT_IN_V6, ipV6BuiltInPath);
        }

        if (StringUtil.isEmpty(ipV6UserDefinedPath)) {
            ipV6UserDefinedPath = KnowledgeConstant.DAT + File.separator + Constants.IP_BUILT_IN_V6;
            map.put(Constants.IP_USER_DEFINED_V6, ipV6UserDefinedPath);
        }
        map.put("isUpdate", updateMap.get("isUpdate"));
        return map;
    }

    private void download(KnowledgeBase knowledgeBase) {

        String name = knowledgeBase.getName();
        String updateTime = knowledgeBase.getUpdateTime();
        String format = knowledgeBase.getFormat();
        String lastUpdateTime = updateMap.get(name);
        if (!StringUtil.equals(updateTime, lastUpdateTime)) {
            Header header = new BasicHeader(KnowledgeConstant.TOKEN, hosConfig.getToken());
            File file;
            FileOutputStream outputStream = null;
            InputStream inputStream = null;
            try {
                inputStream = httpClientService.httpGetInputStream(knowledgeBase.getPath(), httpConfig.getServerResponseTimeOut(), header);
                file = new File(KnowledgeConstant.DAT.concat(File.separator).concat(name).concat(".").concat(format));
                outputStream = new FileOutputStream(file);
                IoUtil.copy(inputStream, outputStream);
                log.info("knowledge download name :{},version:{}", name, knowledgeBase.getVersion());
            } catch (IOException e) {
                log.error("file not fount,:{}", e);
            } finally {
                IoUtil.close(inputStream);
                IoUtil.close(outputStream);
            }
            updateMap.put(name, updateTime);
            updateMap.put("isUpdate", "true");
        }
    }


}

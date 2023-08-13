package com.mesalab.qgw.listener;

import cn.hutool.core.io.IoUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.exception.NacosException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.nacos.NacosConst;
import com.mesalab.common.utils.IPUtil;
import com.mesalab.qgw.model.basic.HttpConfig;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.mesalab.services.common.entity.KnowledgeConfig;
import com.mesalab.services.common.entity.KnowledgeBase;
import com.mesalab.services.common.entity.KnowledgeConstant;
import com.mesalab.services.configuration.HosConfig;
import com.zdjizhi.utils.StringUtil;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author wxs
 * @Date 2022/7/7
 */
@Component
public class KnowledgeListener {

    private static final Log log = LogFactory.get();


    @Autowired
    private HttpConfig httpConfig;
    @Autowired
    KnowledgeConfig knowledgeConfig;
    @Autowired
    private HttpClientService httpClientService;
    @Autowired
    private HosConfig hosConfig;

    @Autowired
    private ConfigService pubConfigService;

    @Autowired
    private ConfigService systemConfigService;

    @Value("${nacos.config.group}")
    private String groupId;

    @Value("${nacos.config.data-id}")
    private String dataId;
    private static Map<String, String> updateMap = new HashMap<>();

    @PostConstruct
    private void initKnowledge() {
        try {
            loadIpLocation(pubConfigService.getConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, httpConfig.getServerRequestTimeOut()));
        } catch (RuntimeException | NacosException e) {
            log.error("loadIpLocation error: {}", e.getMessage());
        }
    }

    @PostConstruct
    private void registerKnowledgeBaseListener() {
        try {
            pubConfigService.addListener(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, new AbstractListener() {
                @Override
                public void receiveConfigInfo(String content) {
                    loadIpLocation(content);
                }
            });
        } catch (RuntimeException | NacosException e) {
            log.error("register knowledgeBase listener error: {}", e.getMessage());
        }
    }

    @PostConstruct
    private void registerSystemConfigListener() {
        try {
            systemConfigService.addListener(dataId, groupId, new AbstractListener() {
                @Override
                public void receiveConfigInfo(String versionInfo) {
                    try {
                        loadIpLocation(pubConfigService.getConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, httpConfig.getServerRequestTimeOut()));
                    } catch (NacosException e) {
                        log.error("loadIpLocation error: {}", e.getMessage());
                    }
                }
            });
        } catch (RuntimeException | NacosException e) {
            log.error("register systemConfig listener error: {}", e.getMessage());
        }
    }

    private void loadIpLocation(String knowledgeMetadata) {
        if (isLoadIpLocation(knowledgeMetadata)) {
            IPUtil.ipLookupBuild();
            log.info("ip location build success");
        } else {
            log.info("ip location no change");
        }
    }

    private boolean isLoadIpLocation(String knowledgeMetadata) {
        Map<String, KnowledgeBase> map = buildIPKnowledgeInfo(getKnowledgeBases(knowledgeMetadata));
        boolean isUpdate = false;
        for (Map.Entry<String, KnowledgeBase> entry : map.entrySet()) {
            KnowledgeBase knowledgeBase = entry.getValue();
            if (StringUtil.equals(knowledgeBase.getSha256(), updateMap.get(knowledgeBase.getId()))) {
                continue;
            }
            log.info("{}.{} version: {}, knowledge updated.", knowledgeBase.getName(), knowledgeBase.getFormat(), knowledgeBase.getVersion());
            download(knowledgeBase);
            updateMap.put(knowledgeBase.getId(), knowledgeBase.getSha256());
            isUpdate = true;
        }
        return isUpdate;
    }

    private void download(KnowledgeBase knowledgeBase) {
        FileOutputStream outputStream = null;
        InputStream inputStream = null;
        try {
            Header header = new BasicHeader(KnowledgeConstant.TOKEN, hosConfig.getToken());
            inputStream = httpClientService.httpGetInputStream(knowledgeBase.getPath(), httpConfig.getServerResponseTimeOut(), header);
            if (inputStream == null) {
                log.error("download file error, skip this update, knowledge is: {}", knowledgeBase);
                return;
            }
            outputStream = new FileOutputStream(KnowledgeConstant.DAT.concat(File.separator).concat(knowledgeBase.getName()).concat(".").concat(knowledgeBase.getFormat()));
            IoUtil.copy(inputStream, outputStream);
            log.info("knowledge download name: {}, version: {}", knowledgeBase.getName(), knowledgeBase.getVersion());
        } catch (IOException e) {
            log.error("download file error, message: {}", e.getMessage());
        } finally {
            IoUtil.close(inputStream);
            IoUtil.close(outputStream);
        }
    }

    private List<KnowledgeBase> getKnowledgeBases(String knowledgeMetadata) {
        log.info("knowledge base meta is: {}", knowledgeMetadata);
        if (StringUtil.isEmpty(knowledgeMetadata)) {
            return Lists.newArrayList();
        }
        return JSON.parseArray(knowledgeMetadata, KnowledgeBase.class);
    }

    private Map<String, KnowledgeBase> buildIPKnowledgeInfo(List<KnowledgeBase> knowledgeBaseList) {
        Map<String, KnowledgeBase> map = Maps.newHashMap();
        String ipV4BuiltIn = knowledgeConfig.getIpV4BuiltIn();
        String ipV4UserDefined = knowledgeConfig.getIpV4UserDefined();
        String ipV4Asn = knowledgeConfig.getIpV4Asn();
        String ipV6BuiltIn = knowledgeConfig.getIpV6BuiltIn();
        String ipV6UserDefined = knowledgeConfig.getIpV6UserDefined();
        String ipV6Asn = knowledgeConfig.getIpV6Asn();
        for (KnowledgeBase knowledgeBase : knowledgeBaseList) {
            String name = knowledgeBase.getName();
            String version = knowledgeBase.getVersion();
            String concat = name.concat(":").concat(version);
            if (StringUtil.equals(concat, ipV4BuiltIn)
                    || (StringUtil.equals(name, ipV4BuiltIn) && StringUtil.equals(version, KnowledgeConstant.LATEST))) {
                map.put(KnowledgeConstant.IP_BUILT_IN_V4, knowledgeBase);
            } else if (StringUtil.equals(concat, ipV4UserDefined)
                    || (StringUtil.equals(name, ipV4UserDefined) && StringUtil.equals(version, KnowledgeConstant.LATEST))) {
                map.put(KnowledgeConstant.IP_USER_DEFINED_V4, knowledgeBase);
            } else if (StringUtil.equals(concat, ipV6BuiltIn)
                    || (StringUtil.equals(name, ipV6BuiltIn) && StringUtil.equals(version, KnowledgeConstant.LATEST))) {
                map.put(KnowledgeConstant.IP_BUILT_IN_V6, knowledgeBase);
            } else if (StringUtil.equals(concat, ipV6UserDefined)
                    || (StringUtil.equals(name, ipV6UserDefined) && StringUtil.equals(version, KnowledgeConstant.LATEST))) {
                map.put(KnowledgeConstant.IP_USER_DEFINED_V6, knowledgeBase);
            } else if (StringUtil.equals(concat, ipV4Asn)
                    || (StringUtil.equals(name, ipV6UserDefined) && StringUtil.equals(version, KnowledgeConstant.LATEST))) {
                map.put(KnowledgeConstant.ASN_V4, knowledgeBase);
            } else if (StringUtil.equals(concat, ipV6Asn)
                    || (StringUtil.equals(name, ipV6UserDefined) && StringUtil.equals(version, KnowledgeConstant.LATEST))) {
                map.put(KnowledgeConstant.ASN_V6, knowledgeBase);
            }
        }
        return map;
    }
}

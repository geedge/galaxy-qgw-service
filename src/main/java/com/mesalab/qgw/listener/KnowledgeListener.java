package com.mesalab.qgw.listener;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.exception.NacosException;
import com.mesalab.common.nacos.NacosConst;
import com.mesalab.common.utils.IPUtil;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.services.common.entity.KnowledgeConstant;
import com.mesalab.network.common.Constants;
import com.mesalab.qgw.model.api.HttpConfig;
import com.mesalab.services.common.entity.KnowledgeVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.PostConstruct;
import java.io.File;
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
    private ConfigService pubConfigService;

    @Autowired
    private ConfigService configService;

    @Value("${nacos.config.group}")
    private String groupId;

    @Value("${nacos.config.data-id}")
    private String dataId;


    @PostConstruct
    private void initKnowledge() {
        IPUtil ipUtil = new IPUtil();
        try {
            String content = pubConfigService.getConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, httpConfig.getServerRequestTimeOut());
            ipUtil.loadIpLocation(content, getKnowledgeVersion());
        } catch (RuntimeException | NacosException e) {
            log.error("Not found file {}", e);
            ipUtil.initIpLookup(KnowledgeConstant.DAT + File.separator + Constants.IP_BUILT_IN_V4, KnowledgeConstant.DAT + File.separator + Constants.IP_BUILT_IN_V6, KnowledgeConstant.DAT + File.separator + Constants.IP_USER_DEFINED_V4,
                    KnowledgeConstant.DAT + File.separator + Constants.IP_USER_DEFINED_V6);
        }

    }


    @PostConstruct
    private void listenerKnowledge() {
        try {
            pubConfigService.addListener(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, new AbstractListener() {
                @Override
                public void receiveConfigInfo(String content) {
                    try {
                        IPUtil ipUtil = new IPUtil();
                        ipUtil.loadIpLocation(content, getKnowledgeVersion());
                    } catch (NacosException e) {
                        log.error("Not found file {}", e);
                    }
                }
            });
        } catch (RuntimeException | NacosException e) {
            log.error("Not found file {}", e);
        }
    }

    @PostConstruct
    private void listenerKnowledgeConfig() {
        try {
            configService.addListener(dataId, groupId, new AbstractListener() {
                @Override
                public void receiveConfigInfo(String versionInfo) {
                    try {
                        String content = pubConfigService.getConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, httpConfig.getServerRequestTimeOut());
                        IPUtil ipUtil = new IPUtil();
                        ipUtil.loadIpLocation(content,getKnowledgeVersion());
                    } catch (NacosException e) {
                        log.error("Not found file {}", e);
                    }
                }
            });
        } catch (RuntimeException | NacosException e) {
            log.error("Not found file {}", e);
        }
    }
    public KnowledgeVersion getKnowledgeVersion() throws NacosException {
        KnowledgeVersion knowledgeVersion = new KnowledgeVersion();
        String content = configService.getConfig(dataId, groupId, httpConfig.getServerRequestTimeOut());
        Yaml yaml = new Yaml();
        Object load = yaml.load(content);
        Object knowledge = JsonMapper.fromJsonString(JsonMapper.toJsonString(load), Map.class).get("knowledge");
        Object ipLocation = JsonMapper.fromJsonString(JsonMapper.toJsonString(knowledge), Map.class).get("ipLocation");
        Object ipV4 = JsonMapper.fromJsonString(JsonMapper.toJsonString(ipLocation), Map.class).get("ipV4");
        Object ipV6 = JsonMapper.fromJsonString(JsonMapper.toJsonString(ipLocation), Map.class).get("ipV6");
        Object ipV4UserDefined = JsonMapper.fromJsonString(JsonMapper.toJsonString(ipV4), Map.class).get("userDefined");
        Object ipV4BuiltIn = JsonMapper.fromJsonString(JsonMapper.toJsonString(ipV4), Map.class).get("builtIn");
        Object ipV6UserDefined = JsonMapper.fromJsonString(JsonMapper.toJsonString(ipV6), Map.class).get("userDefined");
        Object ipV6BuiltIn = JsonMapper.fromJsonString(JsonMapper.toJsonString(ipV6), Map.class).get("builtIn");
        knowledgeVersion.setIpV4UserDefined(String.valueOf(ipV4UserDefined));
        knowledgeVersion.setIpV4BuiltIn(String.valueOf(ipV4BuiltIn));
        knowledgeVersion.setIpV6UserDefined(String.valueOf(ipV6UserDefined));
        knowledgeVersion.setIpV6BuiltIn(String.valueOf(ipV6BuiltIn));
        return knowledgeVersion;
    }
}

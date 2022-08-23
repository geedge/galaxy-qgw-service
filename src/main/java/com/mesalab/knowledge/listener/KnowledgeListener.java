package com.mesalab.knowledge.listener;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.exception.NacosException;
import com.mesalab.common.nacos.NacosConst;
import com.mesalab.common.utils.IPUtil;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.knowledge.common.utils.Constant;
import com.mesalab.knowledge.entity.KnowledgeVersion;
import com.mesalab.knowledge.service.impl.KnowledgeBaseServiceImpl;
import com.mesalab.network.common.Constants;
import com.mesalab.qgw.model.api.HttpConfig;
import com.zdjizhi.utils.StringUtil;
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
    private ConfigService pubConfigService;

    @Autowired
    private HttpConfig httpConfig;

    @Autowired
    private ConfigService configService;

    @Value("${nacos.config.group}")
    private String groupId;

    @Value("${nacos.config.data-id}")
    private String dataId;


    @Autowired
    private KnowledgeBaseServiceImpl knowledgeBaseService;


    @PostConstruct
    private void initKnowledge() {
        try {
            String content = pubConfigService.getConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, httpConfig.getServerRequestTimeOut());
            knowledgeBaseService.queryLoadKnowledgeInfo(content, getKnowledgeVersion());
        } catch (RuntimeException | NacosException e) {
            log.error("Not found file {}", e);
            IPUtil.initIpLookup(Constant.DAT + File.separator + Constants.IP_BUILT_IN_V4, Constant.DAT + File.separator + Constants.IP_BUILT_IN_V6, Constant.DAT + File.separator + Constants.IP_USER_DEFINED_V4,
                    Constant.DAT + File.separator + Constants.IP_USER_DEFINED_V6);
        }

    }


    @PostConstruct
    private void listenerKnowledge() {
        try {
            pubConfigService.addListener(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, new AbstractListener() {
                @Override
                public void receiveConfigInfo(String content) {
                    try {
                        Map<String, String> map = knowledgeBaseService.queryLoadKnowledgeInfo(content, getKnowledgeVersion());
                        loadKnowledge(map);
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
                        Map<String, String> map = knowledgeBaseService.queryLoadKnowledgeInfo(content, getKnowledgeVersion());
                        loadKnowledge(map);
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

    public void loadKnowledge(Map<String,String> map){
        if (ObjectUtil.isNotEmpty(map)){
            String ipV4path = map.get(Constants.IP_BUILT_IN_V4);
            String ipV6path = map.get(Constants.IP_BUILT_IN_V6);
            String ipPrivateV4path = map.get(Constants.IP_USER_DEFINED_V4);
            String ipPrivateV6path = map.get(Constants.IP_USER_DEFINED_V6);
            String isUpdate = map.get("isUpdate");
            if (StringUtil.isNotEmpty(isUpdate) && StringUtil.equals(isUpdate, "true")) {
                log.info("update ip location knowledge,ipV4path:{},ipV6path:{},ipPrivateV4path:{},ipPrivateV6path:{}",
                        ipV4path, ipV6path, ipPrivateV4path, ipPrivateV6path);
                IPUtil.initIpLookup(ipV4path, ipV6path, ipPrivateV4path, ipPrivateV6path);
            } else {
                log.info("No update required");
            }
        } else {
            IPUtil.initIpLookup(Constant.DAT + File.separator + Constants.IP_BUILT_IN_V4, Constant.DAT + File.separator + Constants.IP_BUILT_IN_V6, Constant.DAT + File.separator + Constants.IP_USER_DEFINED_V4,
                    Constant.DAT + File.separator + Constants.IP_USER_DEFINED_V6);
        }

    }

}

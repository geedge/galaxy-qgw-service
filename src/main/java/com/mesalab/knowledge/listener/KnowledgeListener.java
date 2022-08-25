package com.mesalab.knowledge.listener;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.exception.NacosException;
import com.mesalab.common.nacos.NacosConst;
import com.mesalab.common.utils.IPUtil;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.knowledge.common.config.HosConfig;
import com.mesalab.knowledge.common.utils.Constant;
import com.mesalab.knowledge.entity.KnowledgeBase;
import com.mesalab.knowledge.entity.KnowledgeVersion;
import com.mesalab.network.common.Constants;
import com.mesalab.qgw.model.api.HttpConfig;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.StringUtil;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

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
    private HttpClientService httpClientService;

    @Autowired
    private HosConfig hosConfig;

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

    Map<String, String> updateMap = new HashMap<>();


    @PostConstruct
    private void initKnowledge() {
        try {
            String content = pubConfigService.getConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, httpConfig.getServerRequestTimeOut());
            Map<String, String> map = queryLoadKnowledgeInfo(content, getKnowledgeVersion());
            loadKnowledge(map);
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
                        Map<String, String> map = queryLoadKnowledgeInfo(content, getKnowledgeVersion());
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
                        Map<String, String> map = queryLoadKnowledgeInfo(content, getKnowledgeVersion());
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
                IPUtil.initIpLookup(ipV4path, ipV6path, ipPrivateV4path, ipPrivateV6path);
            } else {
                log.info("No update required");
            }
        } else {
            IPUtil.initIpLookup(Constant.DAT + File.separator + Constants.IP_BUILT_IN_V4, Constant.DAT + File.separator + Constants.IP_BUILT_IN_V6, Constant.DAT + File.separator + Constants.IP_USER_DEFINED_V4,
                    Constant.DAT + File.separator + Constants.IP_USER_DEFINED_V6);
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
            String path = Constant.DAT.concat(File.separator).concat(name);
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
            } else if (StringUtil.equals(name, ipV4BuiltIn) && StringUtil.equals(version, Constant.LATEST)) {
                map.put(Constants.IP_BUILT_IN_V4, path);
                download(knowledgeBase);
            } else if (StringUtil.equals(name, ipV4UserDefined) && StringUtil.equals(version, Constant.LATEST)) {
                map.put(Constants.IP_USER_DEFINED_V4, path);
                download(knowledgeBase);
            } else if (StringUtil.equals(name, ipV6BuiltIn) && StringUtil.equals(version, Constant.LATEST)) {
                map.put(Constants.IP_BUILT_IN_V6, path);
                download(knowledgeBase);
            } else if (StringUtil.equals(name, ipV6UserDefined) && StringUtil.equals(version, Constant.LATEST)) {
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
            ipV4BuiltInPath = Constant.DAT + File.separator + Constants.IP_BUILT_IN_V4;
            map.put(Constants.IP_BUILT_IN_V4, ipV4BuiltInPath);
        }
        if (StringUtil.isEmpty(ipV4UserDefinedPath)) {
            ipV4UserDefinedPath = Constant.DAT + File.separator + Constants.IP_BUILT_IN_V6;
            map.put(Constants.IP_USER_DEFINED_V4, ipV4UserDefinedPath);
        }
        if (StringUtil.isEmpty(ipV6BuiltInPath)) {
            ipV6BuiltInPath = Constant.DAT + File.separator + Constants.IP_BUILT_IN_V6;
            map.put(Constants.IP_BUILT_IN_V6, ipV6BuiltInPath);
        }

        if (StringUtil.isEmpty(ipV6UserDefinedPath)) {
            ipV6UserDefinedPath = Constant.DAT + File.separator + Constants.IP_BUILT_IN_V6;
            map.put(Constants.IP_USER_DEFINED_V6, ipV6UserDefinedPath);
        }
        map.put("isUpdate", updateMap.get("isUpdate"));
        return map;
    }

    private void download(KnowledgeBase knowledgeBase) {

        String name = knowledgeBase.getName();
        String updateTime = knowledgeBase.getUpdateTime();
        String lastUpdateTime = updateMap.get(name);
        if (!StringUtil.equals(updateTime, lastUpdateTime)) {
            Header header = new BasicHeader(Constant.TOKEN, hosConfig.getToken());
            File file;
            FileOutputStream outputStream = null;
            InputStream inputStream = null;
            try {
                inputStream = httpClientService.httpGetInputStream(knowledgeBase.getPath(), httpConfig.getServerResponseTimeOut(), header);
                file = new File(Constant.DAT.concat(File.separator).concat(name));
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

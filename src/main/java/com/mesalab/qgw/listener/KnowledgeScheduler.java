package com.mesalab.qgw.listener;

import cn.hutool.core.io.IoUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.utils.IPUtil;
import com.mesalab.qgw.model.basic.HttpConfig;
import com.mesalab.qgw.service.impl.HttpClientServiceV2;
import com.mesalab.services.common.entity.KnowledgeConfig;
import com.mesalab.services.common.entity.KnowledgeConstant;
import com.mesalab.services.service.KBService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
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
public class KnowledgeScheduler {

    private static final Log log = LogFactory.get();


    @Autowired
    private HttpConfig httpConfig;
    @Autowired
    KnowledgeConfig knowledgeConfig;
    @Autowired
    private HttpClientServiceV2 httpClientServiceV2;
    @Autowired
    private KBService kbService;
    private static final Map<String, String> updateMap = new HashMap<>();

    @PostConstruct
    private void loadKnowledge() {
        try {
            BaseResult baseResult = kbService.getList(null);
            if (!baseResult.isSuccess()) {
                log.error("get KB list error: {}", baseResult.getMessage());
            }
            List<Map<String, Object>> knowledgeList = (List<Map<String, Object>>) baseResult.getData();
            for (Map<String, Object> knowledge : knowledgeList) {
                String kbId = String.valueOf(knowledge.get("kb_id"));
                String version = String.valueOf(knowledge.get("version"));
                String sha256 = String.valueOf(knowledge.get("sha256"));
                String path = String.valueOf(knowledge.get("path"));
                if (knowledgeConfig.getIpUserDefined().equals(kbId.concat("_").concat(version))
                        && !sha256.equals(updateMap.get(kbId))) {
                    boolean downloadSucceed = downloadSucceed(path, KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_USER_DEFINED + KnowledgeConstant.MMDB);
                    if (downloadSucceed) {
                        rebuildIpLookup();
                        updateMap.put(kbId, sha256);
                    }
                    continue;
                }
                if (knowledgeConfig.getIpBuiltIn().equals(kbId.concat("_").concat(version))
                        && !sha256.equals(updateMap.get(kbId))) {
                    boolean downloadSucceed = downloadSucceed(path, KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_BUILTIN + KnowledgeConstant.MMDB);
                    if (downloadSucceed) {
                        rebuildIpLookup();
                        updateMap.put(kbId, sha256);
                    }
                    continue;
                }
                if (knowledgeConfig.getIpAsn().equals(kbId.concat("_").concat(version))
                        && !sha256.equals(updateMap.get(kbId))) {
                    boolean downloadSucceed = downloadSucceed(path, KnowledgeConstant.DAT + File.separator + KnowledgeConstant.ASN_BUILTIN + KnowledgeConstant.MMDB);
                    if (downloadSucceed) {
                        rebuildIpLookup();
                        updateMap.put(kbId, sha256);
                    }
                }
            }
        } catch (RuntimeException e) {
            log.error("loadIpLocation error: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${knowledge.corn}")
    public void knowledgeCorn() {
        loadKnowledge();
    }

    private static void rebuildIpLookup() {
        IPUtil.ipLookupBuild();
    }

    private boolean downloadSucceed(String url, String filePath) {
        FileOutputStream outputStream = null;
        InputStream inputStream = null;
        try {
            inputStream = httpClientServiceV2.getInputStream(url, httpConfig.getServerResponseTimeOut(), Maps.newHashMap());
            if (inputStream == null) {
                log.error("download file error, skip this update, knowledge is: {}", url);
                return false;
            }
            outputStream = new FileOutputStream(filePath);
            IoUtil.copy(inputStream, outputStream);
            log.info("knowledge download path is: {}", filePath);
            return true;
        } catch (IOException e) {
            log.error("download file error, message: {}", e.getMessage());
            return false;
        } finally {
            IoUtil.close(inputStream);
            IoUtil.close(outputStream);
        }
    }
}

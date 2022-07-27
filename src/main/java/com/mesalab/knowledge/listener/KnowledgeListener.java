package com.mesalab.knowledge.listener;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.exception.NacosException;
import com.mesalab.common.nacos.NacosConst;
import com.mesalab.knowledge.service.impl.KnowledgeBaseServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

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
    private ConfigService configService;

    @Value("${nacos.config.group}")
    private String groupId;


    @Autowired
    KnowledgeBaseServiceImpl knowledgeBaseService;

    @PostConstruct
    private void KnowledgeListener() {

        try {
            String content = pubConfigService.getConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, 3000);
            String versionInfo = configService.getConfig(NacosConst.KNOWLEDGE_VERSION_CONFIG, groupId, 3000);
            knowledgeBaseService.updateKnowledge(versionInfo, content);
            pubConfigService.addListener(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, new AbstractListener() {
                @Override
                public void receiveConfigInfo(String content) {
                    try {
                        String versionInfo = configService.getConfig(NacosConst.KNOWLEDGE_VERSION_CONFIG, groupId, 3000);
                        knowledgeBaseService.updateKnowledge(versionInfo, content);
                    } catch (NacosException e) {
                        log.error("Not found file {}", e);
                    }
                }
            });

            configService.addListener(NacosConst.KNOWLEDGE_VERSION_CONFIG, groupId, new AbstractListener() {
                @Override
                public void receiveConfigInfo(String versionInfo) {
                    try {
                        String content = pubConfigService.getConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, 3000);
                        knowledgeBaseService.updateKnowledge(versionInfo, content);
                    } catch (NacosException e) {
                        log.error("Not found file {}", e);
                    }
                }
            });
        } catch (NacosException e) {
            log.error("Not found file {}", e);
        }

    }
}

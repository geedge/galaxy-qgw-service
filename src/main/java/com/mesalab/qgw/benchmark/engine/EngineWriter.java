package com.mesalab.qgw.benchmark.engine;

import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.mesalab.common.nacos.NacosClientConfig;
import com.mesalab.qgw.benchmark.Writer;
import com.zdjizhi.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Component
public class EngineWriter extends Writer {

    private static final Log log = LogFactory.get();

    private static final String ENGINE_FILTER_DATA_ID = "galaxy-qgw-engine-filter";

    private static final String ENGINE_QUERIES_TEMPLATE_DATA_ID = "galaxy-qgw-engine-queries-template";

    private String engineFilterJsonStr;

    @Autowired
    private NacosClientConfig nacosClientConfig;

    @PostConstruct
    public void init() {
        initFilterJson();
        log.info("Initializing Engine Filter Complete");
        initQueriesTemplate();
        log.info("Initializing Engine Queries Template Complete");
    }

    private void initFilterJson() {
        try {
            ConfigService configService = NacosFactory.createConfigService(nacosClientConfig.getProperties(NAMESPACE_GALAXY));
            engineFilterJsonStr = configService.getConfig(ENGINE_FILTER_DATA_ID, DEFAULT_GROUP, 3000);
            configService.addListener(ENGINE_FILTER_DATA_ID, DEFAULT_GROUP, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("ReceiveConfigInfo Engine Filter {}", configInfo);
                    engineFilterJsonStr = configInfo;
                }
            });
        } catch (NacosException e) {
            log.info("NacosException:{}", e);
        }
    }

    private void initQueriesTemplate() {
        try {
            ConfigService configService = NacosFactory.createConfigService(nacosClientConfig.getProperties(NAMESPACE_GALAXY));
            configService.getConfig(ENGINE_QUERIES_TEMPLATE_DATA_ID, DEFAULT_GROUP, 3000);
            configService.addListener(ENGINE_QUERIES_TEMPLATE_DATA_ID, DEFAULT_GROUP, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("ReceiveConfigInfo Engine Queries Template {}", configInfo);
                }
            });
        } catch (NacosException e) {
            log.info("NacosException:{}", e);
        }
    }

    @Override
    public String buildPocSQL() {
        String pocSql = "";
        try {
            Map<String, List<String>> environment = getFilterOptions(engineFilterJsonStr);

            FileReader templateReader = new FileReader(buildSnapshotFilePath(ENGINE_QUERIES_TEMPLATE_DATA_ID));

            FileWriter writer = new FileWriter(new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + "engine_queries" + "_"
                    + DateUtils.getCurrentDate(DateUtils.YYYYMMDD) + ".sql");

            pocSql = generatePocSQL(environment, templateReader, writer);

        } catch (IOException e) {
            log.error("Not found file {}", e);
        }
        return pocSql;
    }
}

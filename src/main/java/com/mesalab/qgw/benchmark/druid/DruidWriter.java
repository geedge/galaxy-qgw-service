package com.mesalab.qgw.benchmark.druid;

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
public class DruidWriter extends Writer {

    private static final Log log = LogFactory.get();

    private static final String DRUID_FILTER_DATA_ID = "galaxy-qgw-druid-filter";

    private static final String DRUID_QUERIES_TEMPLATE_DATA_ID = "galaxy-qgw-druid-queries-template";

    private String druidFilterJsonStr;

    @Autowired
    private NacosClientConfig nacosClientConfig;

    @PostConstruct
    public void init() {
        initFilterJson();
        log.info("Initializing Druid Filter Complete");
        initQueriesTemplate();
        log.info("Initializing Druid Queries Template Complete");
    }

    public void initFilterJson() {
        try {
            ConfigService configService = NacosFactory.createConfigService(nacosClientConfig.getProperties(NAMESPACE_GALAXY));
            druidFilterJsonStr = configService.getConfig(DRUID_FILTER_DATA_ID, DEFAULT_GROUP, 3000);
            configService.addListener(DRUID_FILTER_DATA_ID, DEFAULT_GROUP, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("ReceiveConfigInfo Druid Filter {}", configInfo);
                    druidFilterJsonStr = configInfo;
                }
            });
        } catch (NacosException e) {
            log.info("NacosException:{}", e);
        }
    }

    private void initQueriesTemplate() {
        try {
            ConfigService configService = NacosFactory.createConfigService(nacosClientConfig.getProperties(NAMESPACE_GALAXY));
            configService.getConfig(DRUID_QUERIES_TEMPLATE_DATA_ID, DEFAULT_GROUP, 3000);
            configService.addListener(DRUID_QUERIES_TEMPLATE_DATA_ID, DEFAULT_GROUP, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("ReceiveConfigInfo Druid Queries Template {}", configInfo);
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
            Map<String, List<String>> environment = getFilterOptions(druidFilterJsonStr);

            FileReader templateReader = new FileReader(buildSnapshotFilePath(DRUID_QUERIES_TEMPLATE_DATA_ID));

            FileWriter druidWriter = new FileWriter(new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + "druid_queries" + "_"
                    + DateUtils.getCurrentDate(DateUtils.YYYYMMDD) + ".sql");

            pocSql = generatePocSQL(environment, templateReader, druidWriter);
        } catch (IOException e) {
            log.error("Not found file {}", e);
        }
        return pocSql;
    }
}

package com.mesalab.qgw.benchmark.clickhouse;

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
public class ClickhouseWriter extends Writer {

    private static final Log log = LogFactory.get();

    private static final String CK_FILTER_DATA_ID = "galaxy-qgw-ck-filter";

    private static final String CK_QUERIES_TEMPLATE_DATA_ID = "galaxy-qgw-ck-queries-template";

    private String ckFilterJsonStr;

    @Autowired
    private NacosClientConfig nacosClientConfig;

    @PostConstruct
    public void init() {
        initFilterJson();
        log.info("Initializing ClickHouse Filter Complete");
        initQueriesTemplate();
        log.info("Initializing ClickHouse Queries Template Complete");
    }

    @PostConstruct
    public void initFilterJson() {
        try {
            ConfigService configService = NacosFactory.createConfigService(nacosClientConfig.getProperties(NAMESPACE_GALAXY));
            ckFilterJsonStr = configService.getConfig(CK_FILTER_DATA_ID, DEFAULT_GROUP, 3000);
            configService.addListener(CK_FILTER_DATA_ID, DEFAULT_GROUP, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("ReceiveConfigInfo ClickHouse Filter {}", configInfo);
                    ckFilterJsonStr = configInfo;
                }
            });
        } catch (NacosException e) {
            log.info("NacosException:{}", e);
        }
    }

    private void initQueriesTemplate() {
        try {
            ConfigService configService = NacosFactory.createConfigService(nacosClientConfig.getProperties(NAMESPACE_GALAXY));
            configService.getConfig(CK_QUERIES_TEMPLATE_DATA_ID, DEFAULT_GROUP, 3000);
            configService.addListener(CK_QUERIES_TEMPLATE_DATA_ID, DEFAULT_GROUP, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("ReceiveConfigInfo ClickHouse Queries Template {}", configInfo);
                }
            });
        } catch (NacosException e) {
            log.info("NacosException:{}", e);
        }
    }

    public String buildPocSQL() {
        String pocSql = "";
        try {
            Map<String, List<String>> environment = getFilterOptions(ckFilterJsonStr);

            FileReader templateReader = new FileReader(buildSnapshotFilePath(CK_QUERIES_TEMPLATE_DATA_ID));

            FileWriter writer = new FileWriter(new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + "ck_queries" + "_"
                    + DateUtils.getCurrentDate(DateUtils.YYYYMMDD) + ".sql");

            pocSql = generatePocSQL(environment, templateReader, writer);


        } catch (IOException e) {
            log.error("Not found file {}", e);
        }
        return pocSql;
    }

}

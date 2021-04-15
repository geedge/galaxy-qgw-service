package com.mesalab.qgw.benchmark.druid;

import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.mesalab.common.nacos.NacosConfig;
import com.mesalab.common.nacos.NacosConst;
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


    private String druidFilterJson;

    @Autowired
    private NacosConfig nacosConfig;

    @Autowired
    private ConfigService configService;

    @PostConstruct
    public void init() {
        initFilterJson();
        log.info("Initializing Druid Filter Complete");
        initQueriesTemplate();
        log.info("Initializing Druid Queries Template Complete");
    }

    public void initFilterJson() {
        try {
            druidFilterJson = configService.getConfig(NacosConst.DRUID_FILTER_DATA_ID, nacosConfig.getGroup(), 3000);
            configService.addListener(NacosConst.DRUID_FILTER_DATA_ID, nacosConfig.getGroup(), new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("ReceiveConfigInfo Druid Filter {}", configInfo);
                    druidFilterJson = configInfo;
                }
            });
        } catch (NacosException e) {
            log.error("NacosException:{}", e);
        }
    }

    private void initQueriesTemplate() {
        try {
            configService.getConfig(NacosConst.DRUID_QUERIES_TEMPLATE_DATA_ID, nacosConfig.getGroup(), 3000);
            configService.addListener(NacosConst.DRUID_QUERIES_TEMPLATE_DATA_ID, nacosConfig.getGroup(), new Listener() {
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
            log.error("NacosException:{}", e);
        }
    }

    @Override
    public String buildPocSQL() {
        String pocSql = "";
        try {
            Map<String, List<String>> environment = getFilterOptions(druidFilterJson);
            String filePath;
            String localDataFilePath = buildLocalDataFilePath(NacosConst.DRUID_QUERIES_TEMPLATE_DATA_ID);
            String snapshotFilePath = buildSnapshotFilePath(NacosConst.DRUID_QUERIES_TEMPLATE_DATA_ID);
            File localFile = new File(localDataFilePath);
            //优先使用本地配置
            filePath = localFile.exists()?localDataFilePath:snapshotFilePath;

            FileReader templateReader = new FileReader(filePath);

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

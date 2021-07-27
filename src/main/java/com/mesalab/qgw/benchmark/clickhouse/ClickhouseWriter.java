package com.mesalab.qgw.benchmark.clickhouse;

import cn.hutool.core.io.FileUtil;
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
public class ClickhouseWriter extends Writer {

    private static final Log log = LogFactory.get();

    @Autowired
    private NacosConfig nacosConfig;

    @Autowired
    private ConfigService configService;

    @Override
    public String buildPocSQL() {
        String pocSql = "";
        try {
            String filter = configService.getConfig(NacosConst.CK_FILTER_DATA_ID, nacosConfig.getGroup(), 3000);
            Map<String, List<String>> environment = getFilterOptions(filter);

            File templateFile = new File("benchmark" + File.separator + NacosConst.CK_QUERIES_TEMPLATE_DATA_ID);
            String content = configService.getConfig(NacosConst.CK_QUERIES_TEMPLATE_DATA_ID, nacosConfig.getGroup(), 3000);
            templateFile = FileUtil.writeBytes(content.getBytes(), templateFile);
            FileReader templateReader = new FileReader(templateFile);

            File file = FileUtil.writeBytes("".getBytes(), new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + "ck_queries" + "_"
                    + DateUtils.getCurrentDate(DateUtils.YYYYMMDD) + ".sql");
            FileWriter writer = FileWriter.create(file);

            pocSql = generatePocSQL(environment, templateReader, writer);
        } catch (IOException | NacosException e) {
            log.error("Not found file {}", e);
        }
        return pocSql;
    }

}

package com.mesalab.qgw.benchmark.druid;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
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

    @Autowired
    private NacosConfig nacosConfig;

    @Autowired
    private ConfigService systemConfigService;

    @Override
    public String buildPocSQL() {
        String pocSql = "";
        try {
            String filter = systemConfigService.getConfig(NacosConst.DRUID_FILTER_DATA_ID, nacosConfig.getGroup(), 3000);
            Map<String, List<String>> environment = getFilterOptions(filter);

            File templateFile = new File("benchmark" + File.separator + NacosConst.DRUID_QUERIES_TEMPLATE_DATA_ID);
            String content = systemConfigService.getConfig(NacosConst.DRUID_QUERIES_TEMPLATE_DATA_ID, nacosConfig.getGroup(), 3000);
            templateFile = FileUtil.writeBytes(content.getBytes(), templateFile);
            FileReader templateReader = new FileReader(templateFile);

            File file = FileUtil.writeBytes("".getBytes(), new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + "druid_queries" + "_"
                    + DateUtils.getCurrentDate(DateUtils.YYYYMMDD) + ".sql");
            FileWriter writer = FileWriter.create(file);

            pocSql = generatePocSQL(environment, templateReader, writer);
        } catch (IOException | NacosException e) {
            log.error("Not found file {}", e);
        }
        return pocSql;
    }


}

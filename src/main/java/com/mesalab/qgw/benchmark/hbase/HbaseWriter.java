package com.mesalab.qgw.benchmark.hbase;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.mesalab.common.nacos.NacosConfig;
import com.mesalab.common.nacos.NacosConst;
import com.mesalab.qgw.benchmark.Writer;
import com.zdjizhi.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class HbaseWriter extends Writer {
    private static final Log log = LogFactory.get();

    @Autowired
    NacosConfig nacosConfig;

    @Autowired
    ConfigService configService;

    @Override
    public String buildPocSQL() {
        String pocSql = "";
        try {
            String filter = configService.getConfig(NacosConst.HBASE_FILTER_DATA_ID, nacosConfig.getGroup(), 3000);
            Map<String, List<String>> environment = getFilterOptions(filter);

            File templateFile = new File("benchmark" + File.separator + NacosConst.HBASE_QUERIES_TEMPLATE_DATA_ID);
            String content = configService.getConfig(NacosConst.HBASE_QUERIES_TEMPLATE_DATA_ID, nacosConfig.getGroup(), 3000);
            templateFile = FileUtil.writeBytes(content.getBytes(), templateFile);
            FileReader templateReader = new FileReader(templateFile);

            File file = FileUtil.writeBytes("".getBytes(), new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + "hbase_queries" + "_"
                    + DateUtils.getCurrentDate(DateUtils.YYYYMMDD) + ".sql");
            FileWriter writer = FileWriter.create(file);

            pocSql = generatePocSQL(environment, templateReader, writer);

        } catch (NacosException | IOException e) {
            log.error("Not Found file {}", e);
        }
        return pocSql;
    }
}

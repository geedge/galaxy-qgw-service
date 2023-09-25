package com.mesalab.qgw.benchmark;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.mesalab.common.nacos.NacosConfig;
import com.mesalab.common.nacos.NacosConst;
import com.geedgenetworks.utils.StringUtil;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.services.service.SQLDatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public abstract class Writer {

    @Autowired
    NacosConfig nacosConfig;
    @Autowired
    MetadataService metadataService;
    @Autowired
    SQLDatasetService sqlDatasetService;

    private static final Log log = LogFactory.get();
    public static final boolean IS_TITLE = false;
    public static final String TITLE_PREFIX = "--Q";
    public static final String LABEL_COMMON_FILTER = "@common_filter";
    public static final String LABEL_INDEX_FILTER = "@index_filter";
    public static final String LABEL_TIME_START = "@start";
    public static final String LABEL_TIME_END = "@end";

    public Map<String, List<String>> getFilterOptions(String jsonString) {
        Map<String, List<String>> environment = Maps.newLinkedHashMap();
        try {
            List<String> startTime = JsonPath.read(jsonString, "$.filters[?(@.name=='@start')].value");
            List<String> endTime = JsonPath.read(jsonString, "$.filters[?(@.name=='@end')].value");
            environment.put(LABEL_TIME_START, startTime);
            environment.put(LABEL_TIME_END, endTime);
            List<List<String>> commonFilter = JsonPath.read(jsonString, "$.filters[?(@.name=='@common_filter')].value");
            List<List<String>> indexFilter = JsonPath.read(jsonString, "$.filters[?(@.name=='@index_filter')].value");
            if (StringUtil.isNotEmpty(commonFilter)) {
                environment.put(LABEL_COMMON_FILTER, commonFilter.stream().findFirst().get());
            }
            if (StringUtil.isNotEmpty(indexFilter)) {
                environment.put(LABEL_INDEX_FILTER, indexFilter.stream().findFirst().get());
            }
        } catch (JsonPathException e) {
            log.error(" Poc SQL is not found {} ", e);
        }
        return environment;
    }

    protected String generatePocSQL(List<String> list, FileWriter writer) {
        String pocSQL = "";
        try {
            if (writer.getFile().exists()) {
                writer.getFile().createNewFile();
            }
            List<LinkedHashMap> variables = Lists.newArrayList();
            Object codeInfo = metadataService.getCfg(NacosConst.SQL_DATASETS_VARIABLES);
            if (StringUtil.isNotEmpty(codeInfo)) {
                Object json = JSON.toJSON(codeInfo);
                variables = (List<LinkedHashMap>) JSON.parseArray(json.toString(), LinkedHashMap.class);
            }
            List<String> resultLines = Lists.newArrayList();
            for (String line : list){
                String sql = sqlDatasetService.buildExecSQL(variables, line);
                resultLines.add(sql);
            }
            writer.appendLines(resultLines);
            resultLines.clear();
            pocSQL = writer.getFile().getAbsolutePath();
        } catch (IORuntimeException | IOException e) {
            log.error("Generate PocSQL Exception:{}", e);
        }
        return pocSQL;
    }


    @Value("${nacos.config.server-addr}")
    private String nacosServerAddr;

    protected String buildSnapshotDataFilePath(String dataId) {
        StringBuilder filePath = new StringBuilder(buildPrefixPath());
        filePath.append(NacosConst.SNAPSHOT_TENANT);
        filePath.append(File.separator);
        filePath.append(nacosConfig.getNamespace());
        filePath.append(File.separator);
        filePath.append(nacosConfig.getGroup());
        filePath.append(File.separator);
        filePath.append(dataId);
        log.info("dataId:{} snapshot path:{}", dataId, filePath.toString());
        return filePath.toString();
    }

    protected String buildLocalDataFilePath(String dataId) {
        StringBuilder filePath = new StringBuilder(buildPrefixPath());
        filePath.append(NacosConst.NACOS_LOCAL_DATA);
        filePath.append(File.separator);
        filePath.append(NacosConst.CONFIG_DATA_TENANT);
        filePath.append(File.separator);
        filePath.append(nacosConfig.getNamespace());
        filePath.append(File.separator);
        filePath.append(nacosConfig.getGroup());
        filePath.append(File.separator);
        filePath.append(dataId);
        log.info("dataId:{} local data path:{}", dataId, filePath.toString());
        return filePath.toString();
    }

    private String buildPrefixPath() {
        StringBuilder filePath = new StringBuilder(LocalConfigInfoProcessor.LOCAL_SNAPSHOT_PATH);
        filePath.append(File.separator);
        filePath.append(NacosConst.FIXED);
        filePath.append(NacosConst.RAMPANT);
        nacosServerAddr = nacosServerAddr.replaceAll(NacosConst.COLON, NacosConst.UNDERLINE);
        filePath.append(nacosServerAddr).append(NacosConst.RAMPANT);
        filePath.append(nacosConfig.getNamespace());
        filePath.append(NacosConst.UNDERLINE);
        filePath.append(NacosConst.NACOS);
        filePath.append(File.separator);
        return filePath.toString();
    }

    protected abstract String buildPocSQL(List<String> dialectList, String dialect, String category);


}

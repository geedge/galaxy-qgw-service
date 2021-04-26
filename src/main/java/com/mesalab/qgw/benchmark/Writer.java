package com.mesalab.qgw.benchmark;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.mesalab.common.nacos.NacosConfig;
import com.mesalab.common.nacos.NacosConst;
import com.zdjizhi.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

@Component
public abstract class Writer {

    @Autowired
    NacosConfig nacosConfig;

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

    protected String generatePocSQL(Map<String, List<String>> environment, FileReader templateReader, FileWriter writer) {
        String pocSQL = "";
        try {
            if (writer.getFile().exists()) {
                return writer.getFile().getAbsolutePath();
            }

            List<String> rawLines = templateReader.readLines();
            List<String> resultLines = Lists.newArrayList();
            rawLines.stream().forEach(line -> {

                if (line.contains(TITLE_PREFIX)) {
                    if (IS_TITLE) {
                        resultLines.add(line);
                    }
                } else {

                    if (line.contains(LABEL_COMMON_FILTER)) {
                        for (String filter : environment.get(LABEL_COMMON_FILTER)) {
                            String replaceLine = line.replaceAll(LABEL_TIME_START, environment.get(LABEL_TIME_START).get(0))
                                    .replaceAll(LABEL_TIME_END, environment.get(LABEL_TIME_END).get(0))
                                    .replaceAll(LABEL_COMMON_FILTER, filter);

                            resultLines.add(replaceLine);

                        }

                    } else if (line.contains(LABEL_INDEX_FILTER)) {
                        for (String filter : environment.get(LABEL_INDEX_FILTER)) {
                            String replaceLine = line.replaceAll(LABEL_TIME_START, environment.get(LABEL_TIME_START).get(0))
                                    .replaceAll(LABEL_TIME_END, environment.get(LABEL_TIME_END).get(0))
                                    .replaceAll(LABEL_INDEX_FILTER, filter);

                            resultLines.add(replaceLine);

                        }

                    } else {
                        resultLines.add(
                                line.replaceAll(LABEL_TIME_START, environment.get(LABEL_TIME_START).get(0))
                                        .replaceAll(LABEL_TIME_END, environment.get(LABEL_TIME_END).get(0)));
                    }


                }

            });
            writer.appendLines(resultLines);
            resultLines.clear();
            pocSQL = writer.getFile().getAbsolutePath();
        } catch (IORuntimeException e) {
            log.error("IORuntimeException:{}", e);
        }
        return pocSQL;
    }


    @Value("${nacos.config.server-addr}")
    private String nacosServerAddr;

    protected String buildSnapshotFilePath(String dataId) {
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
        String path = LocalConfigInfoProcessor.LOCAL_SNAPSHOT_PATH.replaceAll("\\\\", Matcher.quoteReplacement(File.separator));
        StringBuilder filePath = new StringBuilder(path);
        filePath.append(File.separator);
        filePath.append(NacosConst.FIXED);
        filePath.append(nacosServerAddr.replaceAll(":", "_")).append("-");
        filePath.append(nacosConfig.getNamespace()).append(NacosConst.NACOS);
        filePath.append(File.separator);
        return filePath.toString();
    }

    protected abstract String buildPocSQL();


}

package com.mesalab.qgw.benchmark;

import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.zdjizhi.utils.StringUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public abstract class Writer {

    private static final Log log = LogFactory.get();
    public static final boolean IS_TITLE = false;
    public static final String TITLE_PREFIX = "--Q";
    public static final String LABEL_COMMON_FILTER = "@common_filter";
    public static final String LABEL_INDEX_FILTER = "@index_filter";
    public static final String LABEL_TIME_START = "@start";
    public static final String LABEL_TIME_END = "@end";
    public static final String NAMESPACE_GALAXY = "nacos-namespace-galaxy";
    public static final String DEFAULT_GROUP = "DEFAULT_GROUP";
    public static final String SNAPSHOT_TENANT = "snapshot-tenant";

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
        } catch (Exception e) {
            log.error(" Poc SQL is not found {} ", e);
        }
        return environment;
    }

    protected String generatePocSQL(Map<String, List<String>> environment, FileReader templateReader, FileWriter writer) {
        String pocSQL;
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
        return pocSQL;
    }


    @Value("${nacos.config.server-addr}")
    private String nacosServerAddr;

    protected String buildSnapshotFilePath(String dataId) {
        String localSnapshotPath = LocalConfigInfoProcessor.LOCAL_SNAPSHOT_PATH.replaceAll("\\\\", "/");
        StringBuilder filePath = new StringBuilder(localSnapshotPath);
        filePath.append("/");
        filePath.append("fixed-");
        filePath.append(nacosServerAddr.replaceAll(":", "_")).append("-");
        filePath.append(NAMESPACE_GALAXY).append("_nacos");
        filePath.append("/");
        filePath.append(SNAPSHOT_TENANT);
        filePath.append("/");
        filePath.append(NAMESPACE_GALAXY);
        filePath.append("/");
        filePath.append(DEFAULT_GROUP);
        filePath.append("/");
        filePath.append(dataId);
        log.info("dataId:{} snapshot path:{}", dataId, filePath.toString());
        return filePath.toString();
    }

    protected abstract String buildPocSQL();


}

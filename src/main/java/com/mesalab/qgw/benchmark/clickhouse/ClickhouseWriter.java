package com.mesalab.qgw.benchmark.clickhouse;

import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ClickhouseWriter {
    private static final Log log = LogFactory.get();
    private static final String TITLE_PREFIX = "--Q";
    private static final String LABEL_COMMON_FILTER = "@common_filter";
    private static final String LABEL_INDEX_FILTER = "@index_filter";
    private static final String LABEL_TIME_START = "@start";
    private static final String LABEL_TIME_END = "@end";
    private static final Map<String, String> environment = Maps.newLinkedHashMap();
    private static final List<String> commonFilters = Lists.newArrayList();
    private static final  List<String> indexFilters = Lists.newArrayList();
    private static final boolean IS_TITLE = false;
    private static FileReader templateReader;
    private static FileReader filterReader;
    private static ClickhouseWriter writer = new ClickhouseWriter();

    private ClickhouseWriter(){}

    public static ClickhouseWriter builder() {
        try {
            templateReader = new FileReader(new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + "session_record_queries_template.sql");
            filterReader =  new FileReader(new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + "sessionRecordFilter.json");
            buildFilter(filterReader.readString());
        } catch (IOException e) {
            log.error("UnKnow Poc Files {} ", e);
        }


        return writer;
    }

    private static void buildFilter(String jsonString) {
        List<String> startTime  = JsonPath.read(jsonString,"$.filters[?(@.name=='@start')].value");
        List<String> endTime  = JsonPath.read(jsonString,"$.filters[?(@.name=='@end')].value");
        List<List<String>> commonFilter = JsonPath.read(jsonString,"$.filters[?(@.name=='@common_filter')].value");
        List<List<String>> indexFilter = JsonPath.read(jsonString,"$.filters[?(@.name=='@index_filter')].value");
        commonFilters.addAll(commonFilter.stream().findFirst().get());
        indexFilters.addAll(indexFilter.stream().findFirst().get());
        environment.put(LABEL_TIME_START, startTime.stream().findFirst().get());
        environment.put(LABEL_TIME_END, endTime.stream().findFirst().get());

    }

    public String buildPocSQL() {
        String pocSql = "";
        try {
            FileWriter writer = new FileWriter(new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + "session_record_queries" + "_"
                    + DateUtils.getCurrentDate(DateUtils.YYYYMMDD) +  ".sql");

            if (writer.getFile().exists()) {
                return writer.getFile().getAbsolutePath();
            }

            List<String> rawLines = templateReader.readLines();
            List<String> resultLines = Lists.newArrayList();
            rawLines.stream().forEach(line -> {

                if (line.contains(TITLE_PREFIX)) {
                    if(IS_TITLE) {
                        resultLines.add(line);
                    }
                } else {

                    if (line.contains(LABEL_COMMON_FILTER)) {
                        for (String filter : commonFilters) {
                            String replaceLine = line.replaceAll(LABEL_TIME_START, environment.get(LABEL_TIME_START))
                                    .replaceAll(LABEL_TIME_END, environment.get(LABEL_TIME_END))
                                    .replaceAll(LABEL_COMMON_FILTER, filter);

                            resultLines.add(replaceLine);

                        }

                    } else if (line.contains(LABEL_INDEX_FILTER)){
                        for (String filter : indexFilters) {
                            String replaceLine = line.replaceAll(LABEL_TIME_START, environment.get(LABEL_TIME_START))
                                    .replaceAll(LABEL_TIME_END, environment.get(LABEL_TIME_END))
                                    .replaceAll(LABEL_INDEX_FILTER, filter);

                            resultLines.add(replaceLine);

                        }

                    } else {
                        resultLines.add(
                                line.replaceAll(LABEL_TIME_START, environment.get(LABEL_TIME_START))
                                        .replaceAll(LABEL_TIME_END, environment.get(LABEL_TIME_END)));
                    }



                }

            });
            writer.appendLines(resultLines);
            resultLines.clear();
            pocSql = writer.getFile().getAbsolutePath();
        } catch (IOException e) {
            log.error("Not found file {}", "sample.sql");
        }
        return  pocSql;
    }


}

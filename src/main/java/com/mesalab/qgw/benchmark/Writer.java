package com.mesalab.qgw.benchmark;

import cn.hutool.core.io.file.FileReader;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.zdjizhi.utils.CommonUtil;
import com.zdjizhi.utils.StringUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public abstract class Writer
{
    private static final Log log = LogFactory.get();
    public static final boolean IS_TITLE = false;
    public static final String TITLE_PREFIX = "--Q";
    public static final String LABEL_COMMON_FILTER = "@common_filter";
    public static final String LABEL_INDEX_FILTER = "@index_filter";
    public static final String LABEL_TIME_START = "@start";
    public static final String LABEL_TIME_END = "@end";

    public Map<String, List<String>> getFilterOptions(String filterFile) {
        Map<String, List<String>> environment = Maps.newLinkedHashMap();
        try {
            FileReader filterReader = new FileReader(filterFile);
            String jsonString = filterReader.readString();
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

    public abstract  String buildPocSQL();




}

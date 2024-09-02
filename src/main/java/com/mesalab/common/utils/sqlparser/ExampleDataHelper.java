package com.mesalab.common.utils.sqlparser;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.CharsetUtil;
import com.alibaba.fastjson2.JSONPath;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.common.utils.RandomNumberGenerator;
import com.mesalab.qgw.constant.DataTypeConst;
import com.mesalab.qgw.constant.ExampleDataModeConst;
import com.mesalab.qgw.constant.MetaConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TODO
 *
 * @Classname ExampleDataHelper
 * @Date 2024/3/4 18:49
 * @Author wWei
 */
@Slf4j
public class ExampleDataHelper {

    public static final String QUERY_TYPE_TIMESERIES = "Timeseries";
    public static final String QUERY_TYPE_GROUP_BY = "GroupBy";
    public static final String QUERY_TYPE_AGG_STATISTICS = "AggStatistics";
    public static final String QUERY_TYPE_OTHER = "Other";
    private static final String[] JSONPATH_SPECIAL_CHARS = {"/", ".", "[", "]", "'", "\""};

    private ExampleDataHelper() {
    }

    private static String exampleJsonData;

    static {
        initJsonData();
    }


    public static void initJsonData() {
        String filePath;
        try {
            filePath = new File("").getCanonicalPath() + File.separator + "dat" + File.separator + "example_data.json";
            exampleJsonData = FileUtil.readString(filePath, CharsetUtil.UTF_8);
        } catch (IOException e) {
            log.error("Init Example Data Error: Read File Error, Error is:{}", e.getMessage());
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), e.getMessage()));
        }
    }

    /**
     * Desc: TODO
     *
     * @param queryType      Timeseries, TopN, GroupBy, AggStatistics, Other: Scan
     * @param meta           Column Meta
     * @param sqlRowCount    SQL结果行数
     * @param schemaDataDict Json Schema
     * @return {@link List<Object>}
     * @created by wWei
     * @date 2024/3/6 16:59
     */
    public static List<Object> buildExampleData(@NotNull String queryType, List<Map<String, String>> meta, Long sqlRowCount, Map<String, List<Object>> schemaDataDict, Object queryTypeParameters) {
        int rowCount = (sqlRowCount == null) ? RandomNumberGenerator.generateRangeRandomInt(10, 50) : Math.min(sqlRowCount.intValue(), 50);
        List<LinkedHashMap<String, Object>> result;
        switch (queryType) {
            case QUERY_TYPE_TIMESERIES:
                result = buildExampleDataOfTimeseries(queryTypeParameters.toString(), meta, sqlRowCount, schemaDataDict);
                break;
            case QUERY_TYPE_GROUP_BY:
                result = buildExampleDataOfGroupBy(meta, rowCount, Boolean.parseBoolean(queryTypeParameters.toString()), schemaDataDict);
                break;
            case QUERY_TYPE_AGG_STATISTICS:
                result = buildExampleDataOfMergeAggregation(meta, schemaDataDict);
                break;
            default:
                result = buildExampleDataOfScan(meta, rowCount, schemaDataDict);
                break;
        }
        return result.stream()
                .map(map -> (Object) map)
                .collect(Collectors.toList());
    }

    private static List<LinkedHashMap<String, Object>> buildExampleDataOfTimeseries(String timeGranLabel, List<Map<String, String>> meta, Long sqlRowCount, Map<String, List<Object>> schemaDataDict) {
        Map<String, String> timeGranMeta = meta.stream()
                .filter(item -> timeGranLabel.equals(item.get(MetaConst.META_NAME)))
                .findFirst()
                .orElse(Maps.newHashMap());
        List<LinkedHashMap<String, Object>> timeseriesList = new ArrayList<>();
        if (!timeGranMeta.isEmpty()) {
            String timeGranDataType = timeGranMeta.get(MetaConst.META_DATA_TYPE);
            timeGranDataType = timeGranDataType == null ? timeGranMeta.get(MetaConst.META_TYPE) : timeGranDataType;
            Date currentDate = DateUtil.beginOfHour(new Date());
            int iteratorRowCount = (sqlRowCount == null) ? RandomNumberGenerator.generateRangeRandomInt(10, 50) : Math.min(sqlRowCount.intValue(), 50);
            while (iteratorRowCount-- > 0) {
                Object value = getDateTimeValue(timeGranDataType, iteratorRowCount, currentDate);
                LinkedHashMap<String, Object> timeseries = Maps.newLinkedHashMap();
                timeseries.put(timeGranLabel, value);
                timeseriesList.add(timeseries);
            }
        }

        int counter = 0;
        List<LinkedHashMap<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < meta.size(); i++) {
            Map<String, String> item = meta.get(i);
            if (MetaConst.META_CATEGORY_DIMENSION.equals(item.get(MetaConst.META_CATEGORY)) && !timeGranLabel.equals(item.get(MetaConst.META_NAME))) {
                if (counter >= 1) {
                    timeseriesList = new ArrayList<>(result);
                    result = new ArrayList<>();
                }
                counter++;
                List exampleValue = getExampleValue(item, null, null, false, RandomNumberGenerator.generateRangeRandomInt(1, 5), schemaDataDict);
                for (int i1 = 0; i1 < exampleValue.size(); i1++) {
                    List<LinkedHashMap<String, Object>> maps = new ArrayList<>();
                    for (int j = 0; j < timeseriesList.size(); j++) {
                        LinkedHashMap<String, Object> copiedMap = new LinkedHashMap<>(timeseriesList.get(j));
                        copiedMap.put(item.get(MetaConst.META_NAME), exampleValue.get(i1));
                        maps.add(copiedMap);
                    }
                    result.addAll(maps);
                }
            }
        }

        List<LinkedHashMap<String, Object>> finalResult = result.isEmpty() ? timeseriesList : result;
        meta.stream().filter(item -> MetaConst.META_CATEGORY_METRIC.equals(item.get(MetaConst.META_CATEGORY)))
                .forEach(item -> {
                    List exampleValue = getExampleValue(item, null, null, true, finalResult.size(), schemaDataDict);
                    for (int i = 0; i < finalResult.size(); i++) {
                        finalResult.get(i).put(item.get(MetaConst.META_NAME), exampleValue.get(i));
                    }
                });
        return (sqlRowCount == null) ? finalResult : finalResult.subList(0, Math.min(sqlRowCount.intValue(), finalResult.size()));
    }

    private static List<LinkedHashMap<String, Object>> buildExampleDataOfGroupBy(List<Map<String, String>> meta, Integer rowCount, boolean isDesc, Map<String, List<Object>> schemaDataDict) {
        for (Map<String, String> item : meta) {
            if (inExampleData(item, schemaDataDict)) {
                Integer exampleDataSize = getExampleDataSize(item, schemaDataDict);
                rowCount = exampleDataSize == null ? rowCount : Math.min(rowCount, exampleDataSize);
            }
        }
        List<LinkedHashMap<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            result.add(Maps.newLinkedHashMap());
        }
        Integer finalRowCount = rowCount;
        meta.stream().filter(item -> MetaConst.META_CATEGORY_DIMENSION.equals(item.get(MetaConst.META_CATEGORY)))
                .forEach(item -> {
                    List exampleValue = getExampleValue(item, null, null, false, finalRowCount, schemaDataDict);
                    for (int i = 0; i < result.size(); i++) {
                        result.get(i).put(item.get(MetaConst.META_NAME), exampleValue.get(i));
                    }
                });
        Integer finalRowCount1 = rowCount;
        meta.stream().filter(item -> MetaConst.META_CATEGORY_METRIC.equals(item.get(MetaConst.META_CATEGORY)))
                .forEach(item -> {
                    List exampleValue = getExampleValue(item, null, isDesc, true, finalRowCount1, schemaDataDict);
                    for (int i = 0; i < result.size(); i++) {
                        result.get(i).put(item.get(MetaConst.META_NAME), exampleValue.get(i));
                    }
                });
        return result;
    }

    private static List<LinkedHashMap<String, Object>> buildExampleDataOfMergeAggregation(List<Map<String, String>> meta, Map<String, List<Object>> schemaDataDict) {
        List<LinkedHashMap<String, Object>> result = new ArrayList<>();
        result.add(Maps.newLinkedHashMap());
        meta.stream().filter(item -> MetaConst.META_CATEGORY_METRIC.equals(item.get(MetaConst.META_CATEGORY)))
                .forEach(item -> {
                    List exampleValue = getExampleValue(item, null, null, true, 1, schemaDataDict);
                    for (int i = 0; i < result.size(); i++) {
                        result.get(i).put(item.get(MetaConst.META_NAME), exampleValue.get(0));
                    }
                });
        return result;
    }

    private static List<LinkedHashMap<String, Object>> buildExampleDataOfScan(List<Map<String, String>> meta, int rowCount, Map<String, List<Object>> schemaDataDict) {
        for (Map<String, String> item : meta) {
            if (inExampleData(item, schemaDataDict)) {
                Integer exampleDataSize = getExampleDataSize(item, schemaDataDict);
                rowCount = exampleDataSize == null ? rowCount : Math.min(rowCount, exampleDataSize);
            }
        }
        List<LinkedHashMap<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            result.add(Maps.newLinkedHashMap());
        }
        int finalRowCount = rowCount;
        meta.forEach(item -> {
            List exampleValue = getExampleValue(item, null, null, true, finalRowCount, schemaDataDict);
            for (int i = 0; i < result.size(); i++) {
                result.get(i).put(item.get(MetaConst.META_NAME), exampleValue.get(i));
            }
        });
        return result;
    }

    private static List getExampleValue(Map<String, String> meta, Object initValue, Boolean isDesc, boolean repeatable, int size, Map<String, List<Object>> schemaDataDict) {
        boolean isArray = BooleanUtil.toBoolean(meta.get(MetaConst.IS_ARRAY));
        List result = inExampleData(meta, schemaDataDict)
                ? getExampleDataValues(meta, repeatable, size, schemaDataDict)
                : getOtherExampleValues(meta, isDesc, size);
        return isArray ? convertToArrayList(result) : result;
    }

    private static List<List<Object>> convertToArrayList(List<Object> result) {
        return result.stream()
                .map(Lists::newArrayList)
                .collect(Collectors.toList());
    }

    private static List getOtherExampleValues(Map<String, String> meta, Boolean isDesc, int size) {
        String dataType = meta.get(MetaConst.META_DATA_TYPE) == null ? meta.get(MetaConst.META_TYPE) : meta.get(MetaConst.META_DATA_TYPE);
        if (DataTypeMapping.LONG.equals(dataType) || DataTypeMapping.INT.equals(dataType)) {
            return getExampleIntegerValues(null, isDesc, size);
        } else if (DataTypeMapping.DOUBLE.equals(dataType) || DataTypeMapping.FLOAT.equals(dataType)) {
            return getExampleDoubleValues(null, isDesc, size);
        } else if (DataTypeMapping.BOOLEAN.equals(dataType)) {
            return getExampleBooleanValues(size);
        } else if (DataTypeMapping.DATE.equals(dataType) || DataTypeMapping.TIMESTAMP.equals(dataType)
                || DataTypeConst.TIMESTAMP_FORMAT.equals(dataType)
                || DataTypeConst.TIMESTAMP_MS_FORMAT.equals(dataType) || DataTypeConst.UNIX_TIMESTAMP.equals(dataType)
                || DataTypeConst.UNIX_TIMESTAMP_MS.equals(dataType)
                || DataTypeConst.DATE_TIME_64.equals(dataType)) {
            return getExampleDateTimeValues(dataType, size);
        } else {
            return getExampleStrValues(size);
        }
    }

    private static List<Object> getExampleDateTimeValues(String dataType, int rowCount) {
        List<Object> result = new ArrayList<>();
        Date currentDate = DateUtil.beginOfHour(new Date());
        while (rowCount-- > 0) {
            result.add(getDateTimeValue(dataType, rowCount, currentDate));
        }
        return result;
    }

    private static Object getDateTimeValue(String dataType, int rowCount, Date currentDate) {
        Object value;
        switch (dataType) {
            case DataTypeConst.TIMESTAMP:
                value = DateUtil.format(DateUtil.offsetHour(DateUtil.beginOfHour(currentDate), -rowCount), DatePattern.UTC_WITH_ZONE_OFFSET_PATTERN);
                break;
            case DataTypeConst.TIMESTAMP_MS_FORMAT:
            case DataTypeConst.DATE_TIME_64:
                value = DateUtil.format(DateUtil.offsetHour(currentDate, -rowCount), DatePattern.NORM_DATETIME_MS_FORMAT);
                break;
            case DataTypeConst.UNIX_TIMESTAMP:
                value = DateUtil.offsetHour(currentDate, -rowCount).toInstant().getEpochSecond();
                break;
            case DataTypeConst.UNIX_TIMESTAMP_MS:
                value = DateUtil.offsetHour(currentDate, -rowCount).toInstant().toEpochMilli();
                break;
            case DataTypeConst.DATE:
                value = DateUtil.format(DateUtil.offsetHour(currentDate, -rowCount), DatePattern.NORM_DATE_PATTERN);
                break;
            default:
                value = DateUtil.format(DateUtil.offsetHour(currentDate, -rowCount), DatePattern.NORM_DATETIME_FORMAT);
                break;
        }
        return value;
    }

    private static List<Boolean> getExampleBooleanValues(int size) {
        List<Boolean> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            result.add(new Random().nextBoolean());
        }
        return result;
    }

    private static List<Double> getExampleDoubleValues(Double initValue, Boolean increase, int size) {
        List<Double> result = new ArrayList<>();
        Double relativeV = initValue;
        for (int i = 0; i < size; i++) {
            relativeV = RandomNumberGenerator.generateRelativeRandomDouble(relativeV, increase);
            result.add(relativeV);
        }
        return result;
    }

    private static List<Integer> getExampleIntegerValues(Integer initValue, Boolean idDesc, int size) {
        List<Integer> result = new ArrayList<>();
        Integer relativeV = initValue;
        for (int i = 0; i < size; i++) {
            relativeV = RandomNumberGenerator.generateRelativeRandomInt(relativeV, idDesc);
            result.add(relativeV);
        }
        return result;
    }

    private static List<Object> getExampleDataValues(Map<String, String> meta, boolean repeatable, int size, Map<String, List<Object>> schemaDataDict) {
        List<Object> result = new ArrayList<>();

        if (schemaDataDict.containsKey(meta.get(MetaConst.META_FIELD_NAME))) {
            List<Object> list = schemaDataDict.get(meta.get(MetaConst.META_FIELD_NAME));
            buildEnumDataList(repeatable, size, result, list);
            return result;
        }


        String dataType = meta.get(MetaConst.META_DATA_TYPE) == null ? meta.get(MetaConst.META_TYPE) : meta.get(MetaConst.META_DATA_TYPE);
        String path = "$." + dataType;
        Object mode = JSONPath.extract(exampleJsonData, path + ".mode");
        if (mode == null) {
            for (int i = 0; i < size; i++) {
                result.add(null);
            }
        } else if (ExampleDataModeConst.RANGE.equalsIgnoreCase(mode.toString())) {
            Integer start = (Integer) JSONPath.extract(exampleJsonData, path + ".start");
            Integer end = (Integer) JSONPath.extract(exampleJsonData, path + ".end");
            for (int i = 0; i < size; i++) {
                int v = RandomNumberGenerator.generateRangeRandomInt(start, end);
                result.add(v);
            }
        } else if (ExampleDataModeConst.ENUM.equalsIgnoreCase(mode.toString())) {
            List list = (List) JSONPath.extract(exampleJsonData, path + ".values");
            buildEnumDataList(repeatable, size, result, list);
        } else if (ExampleDataModeConst.SEQUENCE.equalsIgnoreCase(mode.toString())) {
            List list = (List) JSONPath.extract(exampleJsonData, path + ".values");
            result.addAll(list.subList(0, size));
        }
        return result;
    }

    private static void buildEnumDataList(boolean repeatable, int size, List<Object> result, List<Object> list) {
        if (repeatable) {
            for (int i = 0; i < size; i++) {
                int index = RandomNumberGenerator.generateRangeRandomInt(0, list.size() - 1);
                result.add(list.get(index));
            }
        } else {
            Collections.shuffle(list);
            result.addAll(list.subList(0, Math.min(size, list.size())));
        }
    }

    private static Integer getExampleDataSize(Map<String, String> meta, Map<String, List<Object>> schemaDataDict) {
        if (schemaDataDict.containsKey(meta.get(MetaConst.META_FIELD_NAME))) {
            return schemaDataDict.get(meta.get(MetaConst.META_FIELD_NAME)).size();
        }

        String dataType = meta.get(MetaConst.META_DATA_TYPE) == null ? meta.get(MetaConst.META_TYPE) : meta.get(MetaConst.META_DATA_TYPE);
        String path = "$." + dataType;
        Object mode = JSONPath.extract(exampleJsonData, path + ".mode");
        if (ExampleDataModeConst.ENUM.equalsIgnoreCase(mode.toString())) {
            List list = (List) JSONPath.extract(exampleJsonData, path + ".values");
            return list.size();
        } else if (ExampleDataModeConst.SEQUENCE.equalsIgnoreCase(mode.toString())) {
            List list = (List) JSONPath.extract(exampleJsonData, path + ".values");
            return list.size();
        }
        return null;
    }


    /**
     * Desc: Generate random data of type List<Object>
     *
     * @param size result size
     * @return {@link List<Object>}
     * @created by wWei
     * @date 2024/3/9 15:00
     */
    private static List<Object> getExampleStrValues(int size) {
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            result.add(RandomStringUtils.randomAlphanumeric(3, 10));
        }
        return result;
    }

    /**
     * Desc: Determine if the current result column is in the example data file or schema dictionary data
     *
     * @param meta           meta information
     * @param schemaDataDict schema data dictionary
     * @return {@link boolean}
     * @created by wWei
     * @date 2024/3/9 15:02
     */
    private static boolean inExampleData(Map<String, String> meta, Map<String, List<Object>> schemaDataDict) {
        if (schemaDataDict == null) {
            return false;
        }
        if (schemaDataDict.containsKey(meta.get(MetaConst.META_FIELD_NAME))) {
            return true;
        }

        String dataType = meta.get(MetaConst.META_DATA_TYPE) == null ? meta.get(MetaConst.META_TYPE) : meta.get(MetaConst.META_DATA_TYPE);
        // 对特殊字符进行转义处理
        for (String specialChar : JSONPATH_SPECIAL_CHARS) {
            dataType = dataType.replace(specialChar, "\\" + specialChar);
        }
        String path = "$." + dataType;
        return JSONPath.extract(exampleJsonData, path) != null;
    }
}

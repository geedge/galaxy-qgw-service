package com.mesalab.common.utils.sqlparser;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.CharsetUtil;
import com.alibaba.fastjson2.JSONPath;
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
     * @param queryType   Timeseries, TopN, GroupBy, AggStatistics, Other: Scan
     * @param meta        Column Meta
     * @param sqlRowCount 结果行数
     * @param jsonSchema  Json Schema
     * @return {@link List<Object>}
     * @created by wWei
     * @date 2024/3/6 16:59
     */
    public static List<Object> buildExampleData(@NotNull String queryType, List<Map<String, String>> meta, Long sqlRowCount, Map<String, Object> jsonSchema, Object queryTypeParameters) {
        int rowCount = (sqlRowCount == null) ? RandomNumberGenerator.generateRangeRandomInt(10, 50) : Math.min(sqlRowCount.intValue(), 50);
        List<Map<String, Object>> result;
        switch (queryType) {
            case QUERY_TYPE_TIMESERIES:
                result = buildExampleDataOfTimeseries(queryTypeParameters.toString(), meta, rowCount);
                break;
            case QUERY_TYPE_GROUP_BY:
                result = buildExampleDataOfGroupBy(meta, rowCount, Boolean.parseBoolean(queryTypeParameters.toString()));
                break;
            case QUERY_TYPE_AGG_STATISTICS:
                result = buildExampleDataOfMergeAggregation(meta);
                break;
            default:
                result = buildExampleDataOfScan(meta, rowCount);
                break;
        }
        return result.stream()
                .map(map -> (Object) map)
                .collect(Collectors.toList());
    }

    private static List<Map<String, Object>> buildExampleDataOfTimeseries(String timeGranLabel, List<Map<String, String>> meta, Integer rowCount) {
        Map<String, String> timeGranMeta = meta.stream()
                .filter(item -> timeGranLabel.equals(item.get(MetaConst.META_NAME)))
                .findFirst()
                .orElse(Maps.newHashMap());
        List<Map<String, Object>> result = new ArrayList<>();
        if (!timeGranMeta.isEmpty()) {
            String timeGranDataType = timeGranMeta.get(MetaConst.META_DATA_TYPE);
            timeGranDataType = timeGranDataType == null ? timeGranMeta.get(MetaConst.META_TYPE) : timeGranDataType;
            Date currentDate = DateUtil.beginOfHour(new Date());
            int iteratorRowCount = rowCount;
            while (iteratorRowCount-- > 0) {
                Object value = getDateTimeValue(timeGranDataType, iteratorRowCount, currentDate);
                Map<String, Object> timeseries = Maps.newHashMap();
                timeseries.put(timeGranLabel, value);
                result.add(timeseries);
            }
        }
        meta.stream().filter(item -> MetaConst.META_CATEGORY_DIMENSION.equals(item.get(MetaConst.META_CATEGORY)) && !timeGranLabel.equals(item.get(MetaConst.META_NAME)))
                .forEach(item -> {
                    String dataType = item.get(MetaConst.META_DATA_TYPE) == null ? item.get(MetaConst.META_TYPE) : item.get(MetaConst.META_DATA_TYPE);
                    List exampleValue = getExampleValue(dataType, null, null, true, RandomNumberGenerator.generateRangeRandomInt(1, 5));
                    for (int i = 0; i < result.size(); i++) {
                        result.get(i).put(item.get(MetaConst.META_NAME), exampleValue.get(RandomNumberGenerator.generateRangeRandomInt(0, exampleValue.size() - 1)));
                    }
                });

        meta.stream().filter(item -> MetaConst.META_CATEGORY_METRIC.equals(item.get(MetaConst.META_CATEGORY)))
                .forEach(item -> {
                    String dataType = item.get(MetaConst.META_DATA_TYPE) == null ? item.get(MetaConst.META_TYPE) : item.get(MetaConst.META_DATA_TYPE);
                    List exampleValue = getExampleValue(dataType, null, null, true, result.size());
                    for (int i = 0; i < result.size(); i++) {
                        result.get(i).put(item.get(MetaConst.META_NAME), exampleValue.get(i));
                    }
                });
        return result;
    }

    private static List<Map<String, Object>> buildExampleDataOfGroupBy(List<Map<String, String>> meta, Integer rowCount, boolean isDesc) {
        for (Map<String, String> item : meta) {
            String dataType = item.get(MetaConst.META_DATA_TYPE) == null ? item.get(MetaConst.META_TYPE) : item.get(MetaConst.META_DATA_TYPE);
            if (inExampleDataFile(dataType)) {
                Integer exampleDataSize = getExampleDataSize(dataType);
                rowCount = exampleDataSize == null ? rowCount : Math.min(rowCount, exampleDataSize);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            result.add(Maps.newHashMap());
        }
        Integer finalRowCount = rowCount;
        meta.stream().filter(item -> MetaConst.META_CATEGORY_DIMENSION.equals(item.get(MetaConst.META_CATEGORY)))
                .forEach(item -> {
                    String dataType = item.get(MetaConst.META_DATA_TYPE) == null ? item.get(MetaConst.META_TYPE) : item.get(MetaConst.META_DATA_TYPE);
                    List exampleValue = getExampleValue(dataType, null, null, false, finalRowCount);
                    for (int i = 0; i < result.size(); i++) {
                        result.get(i).put(item.get(MetaConst.META_NAME), exampleValue.get(i));
                    }
                });
        Integer finalRowCount1 = rowCount;
        meta.stream().filter(item -> MetaConst.META_CATEGORY_METRIC.equals(item.get(MetaConst.META_CATEGORY)))
                .forEach(item -> {
                    String dataType = item.get(MetaConst.META_DATA_TYPE) == null ? item.get(MetaConst.META_TYPE) : item.get(MetaConst.META_DATA_TYPE);
                    List exampleValue = getExampleValue(dataType, null, isDesc, true, finalRowCount1);
                    for (int i = 0; i < result.size(); i++) {
                        result.get(i).put(item.get(MetaConst.META_NAME), exampleValue.get(i));
                    }
                });
        return result;
    }

    private static List<Map<String, Object>> buildExampleDataOfMergeAggregation(List<Map<String, String>> meta) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(Maps.newHashMap());
        meta.stream().filter(item -> MetaConst.META_CATEGORY_METRIC.equals(item.get(MetaConst.META_CATEGORY)))
                .forEach(item -> {
                    String dataType = item.get(MetaConst.META_DATA_TYPE) == null ? item.get(MetaConst.META_TYPE) : item.get(MetaConst.META_DATA_TYPE);
                    List exampleValue = getExampleValue(dataType, null, null, true, 1);
                    for (int i = 0; i < result.size(); i++) {
                        result.get(i).put(item.get(MetaConst.META_NAME), exampleValue.get(0));
                    }
                });
        return result;
    }

    private static List<Map<String, Object>> buildExampleDataOfScan(List<Map<String, String>> meta, int rowCount) {
        for (Map<String, String> item : meta) {
            String dataType = item.get(MetaConst.META_DATA_TYPE) == null ? item.get(MetaConst.META_TYPE) : item.get(MetaConst.META_DATA_TYPE);
            if (inExampleDataFile(dataType)) {
                Integer exampleDataSize = getExampleDataSize(dataType);
                rowCount = exampleDataSize == null ? rowCount : Math.min(rowCount, exampleDataSize);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < rowCount; i++) {
            result.add(Maps.newHashMap());
        }
        int finalRowCount = rowCount;
        meta.forEach(item -> {
            String dataType = item.get(MetaConst.META_DATA_TYPE) == null ? item.get(MetaConst.META_TYPE) : item.get(MetaConst.META_DATA_TYPE);
            List exampleValue = getExampleValue(dataType, null, null, true, finalRowCount);
            for (int i = 0; i < result.size(); i++) {
                result.get(i).put(item.get(MetaConst.META_NAME), exampleValue.get(i));
            }
        });
        return result;
    }

    private static List getExampleValue(String dataType, Object initValue, Boolean isDesc, boolean canRepeat, int size) {
        if (inExampleDataFile(dataType)) {
            return getExampleDataValues(dataType, canRepeat, size);
        }
        if (DataTypeMapping.LONG.equals(dataType) || DataTypeMapping.INT.equals(dataType)) {
            return getExampleIntegerValues(null, isDesc, size);
        }
        if (DataTypeMapping.DOUBLE.equals(dataType) || DataTypeMapping.FLOAT.equals(dataType)) {
            return getExampleDoubleValues(null, isDesc, size);
        }
        if (DataTypeMapping.BOOLEAN.equals(dataType)) {
            return getExampleBooleanValues(size);
        }
        if (DataTypeMapping.DATE.equals(dataType) || DataTypeMapping.TIMESTAMP.equals(dataType)
                || DataTypeConst.TIMESTAMP_FORMAT.equals(dataType)
                || DataTypeConst.TIMESTAMP_MS_FORMAT.equals(dataType) || DataTypeConst.UNIX_TIMESTAMP.equals(dataType)
                || DataTypeConst.UNIX_TIMESTAMP_MS.equals(dataType)
        ) {
            return getExampleDateTimeValues(dataType, size);
        }
        return getExampleStrValues(size);
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

    private static List<Object> getExampleDataValues(String dateType, boolean canRepeat, int size) {
        String path = "$." + dateType;
        Object mode = JSONPath.extract(exampleJsonData, path + ".mode");
        List<Object> result = new ArrayList<>();

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
            if (canRepeat) {
                for (int i = 0; i < size; i++) {
                    int index = RandomNumberGenerator.generateRangeRandomInt(0, list.size() - 1);
                    result.add(list.get(index));
                }
            } else {
                result.addAll(list);
            }
        }
        return result;
    }

    private static Integer getExampleDataSize(String dateType) {
        String path = "$." + dateType;
        Object mode = JSONPath.extract(exampleJsonData, path + ".mode");
        if (ExampleDataModeConst.ENUM.equalsIgnoreCase(mode.toString())) {
            List list = (List) JSONPath.extract(exampleJsonData, path + ".values");
            return list.size();
        }
        return null;
    }


    /**
     * Desc: 随机生成List<String>类型的数据
     *
     * @param size 结果数组大小
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
     * Desc: 判断指定类型数据是否存在于示例数据文件中
     *
     * @param dataType 数据类型
     * @return {@link boolean}
     * @created by wWei
     * @date 2024/3/9 15:02
     */
    private static boolean inExampleDataFile(String dataType) {
        // 对特殊字符进行转义处理
        for (String specialChar : JSONPATH_SPECIAL_CHARS) {
            dataType = dataType.replace(specialChar, "\\" + specialChar);
        }
        String path = "$." + dataType;
        return JSONPath.extract(exampleJsonData, path) != null;
    }
}

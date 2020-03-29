package com.mesalab.qgw.model.api.udf;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.mesalab.common.utils.ConvertUtil;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;

import java.util.*;

@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TIME_FLOOR_WITH_FILL implements UDF{
    private String name;
    private String timestamp;
    private String period;
    private String fill;

    public TIME_FLOOR_WITH_FILL(UDFElements udfElements) {
        this.name = udfElements.getName();

        this.timestamp = udfElements.getParams().get(0).toString();
        this.period = ((StringValue) udfElements.getParams().get(1)).getValue();
        if (udfElements.getParams().size() >= 3) {
            this.fill = ((StringValue) udfElements.getParams().get(2)).getValue();
        }
    }


    @Override
    public Object execute(SQLQuerySource sqlQuerySource, List<Object> results) {
        List<Map<String, String>>  targetResult = null;
        String groupLabel= "";
        String timeGranLabel = "";



        try {

            Map<String, String> groupDimension = sqlQuerySource.getGroupDimension();

            for (String groupKey: groupDimension.keySet()) {
                if (groupDimension.get(groupKey).contains(name)) {
                    timeGranLabel = groupKey;
                } else {
                    groupLabel = Joiner.on(",").join(groupKey, groupLabel);//todo 分组多key拼接
                }

            }

            if (groupLabel.split(",").length == 1) {
                groupLabel = groupLabel.substring(0,groupLabel.length()-1);
            }

            Map<String, Map<String, Map<String, String>>> targetTimeseriesMap = ConvertUtil.convertResultListToTimeseries(results,
                    groupLabel, timeGranLabel);


            List<Date> dateRangeList = null;
            SQLQuerySource.PartitionTimeRange partitionTimeRange = sqlQuerySource.getPartitionTimeRange();

            if (StringUtil.isNotEmpty(partitionTimeRange)) {

                if (StringUtil.isNotEmpty(partitionTimeRange.getLeft()) && StringUtil.isNotEmpty(partitionTimeRange.getRight()) ) {
                    dateRangeList = DateUtils.getDateRange(DateUtils.convertStringToDate(((StringValue)partitionTimeRange.getLeft()).getValue(), DateUtils.YYYY_MM_DD_HH24_MM_SS),
                            DateUtils.convertStringToDate( ((StringValue)partitionTimeRange.getRight()).getValue(), DateUtils.YYYY_MM_DD_HH24_MM_SS), period.toString());

                } else if (StringUtil.isNotEmpty(partitionTimeRange.getLeft()) && StringUtil.isEmpty(partitionTimeRange.getRight())) {

                    dateRangeList = DateUtils.getDateRange(DateUtils.convertStringToDate(((StringValue)partitionTimeRange.getLeft()).getValue(), DateUtils.YYYY_MM_DD_HH24_MM_SS),
                            DateUtils.convertStringToDate(DateUtils.getCurrentDate(DateUtils.YYYY_MM_DD_HH24_MM_SS), DateUtils.YYYY_MM_DD_HH24_MM_SS), period.toString());

                }


            }


            for (String key : targetTimeseriesMap.keySet()) {
                TreeMap<String, Map<String, String>> seriesMap = (TreeMap <String, Map<String, String>>) targetTimeseriesMap.get(key);

                if(StringUtil.isEmpty(dateRangeList)){ //尝试补全中间结果
                    dateRangeList = DateUtils.getDateRange(DateUtils.convertStringToDate(seriesMap.firstKey(), DateUtils.YYYY_MM_DD_HH24_MM_SS),
                            DateUtils.convertStringToDate(seriesMap.lastKey(), DateUtils.YYYY_MM_DD_HH24_MM_SS), period.toString());
                }

                targetTimeseriesMap.put(key,ConvertUtil.completeTimeseries(seriesMap, dateRangeList, fill.toString()));

            }
            targetResult =  ConvertUtil.convertTimeseriesToList(targetTimeseriesMap, groupLabel, timeGranLabel);
        } catch (Exception e) {
            log.error("Parser function : TIME_FLOOR_WITH_FILL error : {} ", e);
        }


        return targetResult;

    }
}

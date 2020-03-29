package com.mesalab.qgw.model.api.udf;

import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.utils.ConvertUtil;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.sf.jsqlparser.expression.Expression;
import org.apache.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TIME_FLOOR_WITH_FILL implements UDF{
    private String name;
    private Expression timestamp;
    private Expression period;
    private Expression fill;

    public TIME_FLOOR_WITH_FILL(UDFElements udfElements) {
        this.name = udfElements.getName();
        this.timestamp = udfElements.getParams().get(0);
        this.period = udfElements.getParams().get(1);
        this.fill = udfElements.getParams().get(2);
    }


    @Override
    public Object execute(SQLQuerySource sqlQuerySource, List<Object> results) {
        String groupLabel = "http_domain";
        String timeGranLabel = "stat_time";

        Map<String, Object> groupDimension = sqlQuerySource.getGroupDimension();
        System.out.println(groupDimension);

        System.out.println( sqlQuerySource.getPartitionRange());

        Map<String, Map<String, Map<String, String>>> targetTimeseriesMap = ConvertUtil.convertResultListToTimeseries(results,
                groupLabel, timeGranLabel);


        for (String key : targetTimeseriesMap.keySet()) {
            TreeMap<String, Map<String, String>> groupMap = (TreeMap <String, Map<String, String>>) targetTimeseriesMap.get(key);

            Map <String, Map<String, String>>  completeMap = ConvertUtil.completeTimeseries(groupMap,
                    DateUtils.getDateRange(DateUtils.convertStringToDate("2019-12-13 09:00:12", DateUtils.YYYY_MM_DD_HH24_MM_SS),
                            DateUtils.convertStringToDate("2019-12-14 10:00:12", DateUtils.YYYY_MM_DD_HH24_MM_SS), "PT5M"), "none");

            targetTimeseriesMap.put(key,completeMap);

        }

        List<Map<String, String>>  targetResult =  ConvertUtil.convertTimeseriesToList(targetTimeseriesMap, groupLabel, timeGranLabel);

        return targetResult;

    }
}

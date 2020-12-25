package com.mesalab.calcite.function;

import com.google.common.collect.Maps;
import com.zdjizhi.utils.DateUtils;
import java.util.Map;

public class DateFunction {

    private static Map<String, String> formatMapping = Maps.newHashMap();

    static {
        formatMapping.put("%Y-%m-%d %H:%i:%s", DateUtils.YYYY_MM_DD_HH24_MM_SS);
        formatMapping.put("%Y-%m-%d", DateUtils.YYYY_MM_DD);
    }

    public String fromUnixTimeToDateTime24(Object i) {
        Long timestamp = Long.valueOf(String.valueOf(i));
        return DateUtils.convertTimestampToString(timestamp, DateUtils.YYYY_MM_DD_HH24_MM_SS);
    }

    public String dateFormat(String date, String format) {
        long timestamp = DateUtils.convertStringToTimestamp(date, DateUtils.YYYY_MM_DD_HH24_MM_SS);
        return DateUtils.convertTimestampToString(timestamp, formatMapping.get(format));
    }
}

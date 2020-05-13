package com.mesalab.calcite.function;

import com.zdjizhi.utils.DateUtils;

public class DateFunction {

    public String fromUnixTimeToDateTime24(Object i) {
        Long aLong = Long.valueOf(String.valueOf(i));
        String s = DateUtils.convertTimestampToString(aLong, DateUtils.YYYY_MM_DD_HH24_MM_SS);
        return s;
    }
}

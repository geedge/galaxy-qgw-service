package com.mesalab.timeZoneTests;

import com.zdjizhi.utils.DateUtils;
import org.junit.Test;
import org.springframework.stereotype.Component;

import java.util.Date;
/*
* 判断stringToDate08和stringToDate00的date类型时间是否相等
* 判断timestampToString08和timestampToString00的字符串是否相差8小时
* 判断stringToTimestamp08和stringToTimestamp00的时间戳是否相差把现实
* */

@Component
public class TimeZoneTest {
    @Test
    public void timeZoneTest08(){
//        改变jvm的时区为GMT+08

        Date stringToDate08 = DateUtils.convertStringToDate("2019-09-09 09:09:09", DateUtils.YYYY_MM_DD_HH24_MM_SS);
        String timestampToString08 = DateUtils.convertTimestampToString(1567991349, DateUtils.YYYY_MM_DD_HH24_MM_SS);
        long stringToTimestamp08 = DateUtils.convertStringToTimestamp("2019-09-09 09:09:09", DateUtils.YYYY_MM_DD_HH24_MM_SS);
        System.out.println(stringToDate08);
        System.out.println(timestampToString08);
        System.out.println(stringToTimestamp08);
    }
    @Test
    public void timeZoneTest00(){
        //        改变jvm的时区为GMT+00
        Date stringToDate00 = DateUtils.convertStringToDate("2019-09-09 09:09:09", DateUtils.YYYY_MM_DD_HH24_MM_SS);
        String timestampToString00 = DateUtils.convertTimestampToString(1567991349, DateUtils.YYYY_MM_DD_HH24_MM_SS);
        long stringToTimestamp00 = DateUtils.convertStringToTimestamp("2019-09-09 09:09:09", DateUtils.YYYY_MM_DD_HH24_MM_SS);
        System.out.println(stringToDate00);
        System.out.println(timestampToString00);
        System.out.println(stringToTimestamp00);
    }
}

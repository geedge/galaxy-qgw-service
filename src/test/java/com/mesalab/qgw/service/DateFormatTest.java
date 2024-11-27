package com.mesalab.qgw.service;

import cn.hutool.core.date.DateException;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.google.common.collect.Lists;
import com.mesalab.GalaxyQGWApplicationTests;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;


@EnableAutoConfiguration
public class DateFormatTest extends GalaxyQGWApplicationTests {

    @Test
    public void testDateParserTimeZoneJVM() {
        ArrayList<String> dateTimeStrList = Lists.newArrayList();
        dateTimeStrList.add("2000-01-01T00:00:00Z");
        dateTimeStrList.add("2000-01-01T00:00:00.865Z");
        dateTimeStrList.add("1999-12-31T20:00:00.992847200-04:00");
        dateTimeStrList.add("2000-01-01T08:00:00.865+08:00");
        try {
            for (String dateTimeStr : dateTimeStrList) {
                DateTime parse = DateUtil.parse(dateTimeStr).setTimeZone(TimeZone.getDefault());
                Assertions.assertEquals("2000-01-01 00:00:00", parse.toString());
            }
        } catch (DateException e) {
            throw new DateException(e);
        }
    }

    @Test
    public void testDateParserTimeZoneDefault() {
        List<String> dateTimeStrList = Lists.newArrayList();
        dateTimeStrList.add("2000-01-01T00:00:00Z");
        dateTimeStrList.add("2000-01-01T00:00:00.865Z");
        dateTimeStrList.add("2000-01-01T00:00:00.992847200-04:00");
        dateTimeStrList.add("2000-01-01T00:00:00.865+08:00");
        try {
            for (String dateTimeStr : dateTimeStrList) {
                Assertions.assertEquals("2000-01-01 00:00:00", DateUtil.parse(dateTimeStr).toString());
            }
        } catch (DateException e) {
            throw new DateException(e);
        }
    }
}

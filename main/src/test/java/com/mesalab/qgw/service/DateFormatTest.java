package com.mesalab.qgw.service;

import cn.hutool.core.date.DateException;
import cn.hutool.core.date.DateUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Lists;
import com.mesalab.GalaxyQGWApplicationTests;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.util.ArrayList;


@EnableAutoConfiguration
public class DateFormatTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();
    private final static ArrayList<String> dateTimeStrList = Lists.newArrayList();

    static {
        dateTimeStrList.add("2021-08-13T14:20:18.992847200-04:00");
        dateTimeStrList.add("2023-03-15T01:57:58.865Z");
        dateTimeStrList.add("2018-10-01T10:00:00Z");
        dateTimeStrList.add("2023-03-15T01:57:58.865+08:00");
    }

    @Test
    public void testDateParser() {
        try {
            for (String dateTimeStr : dateTimeStrList) {
                log.info(String.valueOf(DateUtil.parse(dateTimeStr)));
            }
        } catch (DateException e) {
            throw new DateException(e);
        }
    }
}

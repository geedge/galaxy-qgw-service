package com.mesalab.qgw.service;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.qgw.service.impl.TestSqlServiceImpl;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

@EnableAutoConfiguration
public class DruidTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();

    @Autowired
    private TestSqlServiceImpl testSqlService;

    /**
     * ck相关测试
     */
    @Test
    public void parseSqlOfDruid() {
        List list = testSqlService.druidSql();

        SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd HH:00:00");
        SimpleDateFormat format2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date(System.currentTimeMillis());
        for (Object sql : list) {
            String s = String.valueOf(sql);
            try {
                s = s.replaceAll("\\$start_time", "'" + format1.format(date) + "'");
                s = s.replaceAll("\\$end_time", "'" + format2.format(date) + "'");
                Statement parse = CCJSqlParserUtil.parse(s);
                log.info(parse.toString());
            } catch (JSQLParserException e) {
                log.error("解析异常:{}", s);
            }
        }
    }

    @Before
    public void testBefore() {
        log.info("=================================================DRUID TEST BEGIN=================================================");
    }

    @After
    public void testAfter() {
        log.info("=================================================DRUID TEST  END=================================================");

    }





}

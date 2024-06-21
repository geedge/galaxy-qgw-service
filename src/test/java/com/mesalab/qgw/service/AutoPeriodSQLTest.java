package com.mesalab.qgw.service;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.utils.sqlparser.AutoPeriodHelper;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * TODO
 *
 * @Classname AutoPeriodTest
 * @Date 2024/6/20 09:24
 * @Author wWei
 */
@EnableAutoConfiguration
public class AutoPeriodSQLTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();
    private static final DateTime now;

    static {
        now = new DateTime();
    }

    private static final String originalSQL = "SELECT\n" +
            "    FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(stat_time, CHART_GRANULARITY('${start_time}', '${end_time}'), 'zero')) AS stat_time,\n" +
            "    AVG( in_pkts_per_sec ) AS avg_in_pkts_per_sec\n" +
            "FROM\n" +
            "    (\n" +
            "    SELECT\n" +
            "        TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time), SAMPLE_GRANULARITY('${start_time}', '${end_time}')) AS stat_time,\n" +
            "        RATE( in_pkts, SAMPLE_GRANULARITY('${start_time}', '${end_time}'), 1) AS in_pkts_per_sec\n" +
            "    FROM\n" +
            "        traffic_general_stat\n" +
            "    WHERE\n" +
            "        __time >= '${start_time}'\n" +
            "        AND __time < '${end_time}'\n" +
            "        AND vsys_id IN ( 1)\n" +
            "        AND ( 1 = 1 )\n" +
            "    GROUP BY\n" +
            "        TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time), SAMPLE_GRANULARITY('${start_time}', '${end_time}'))\n" +
            "    HAVING TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time), SAMPLE_GRANULARITY('${start_time}', '${end_time}')) >= '${start_time}'\n" +
            "    ORDER BY TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time), SAMPLE_GRANULARITY('${start_time}', '${end_time}')) DESC\n" +
            "    )\n" +
            "GROUP BY\n" +
            "    FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(stat_time, CHART_GRANULARITY('${start_time}', '${end_time}'), 'zero'))\n" +
            "ORDER BY\n" +
            "    stat_time ASC\n" +
            "LIMIT 10";

    @Test
    public void testSQLLast5Minutes() throws JSQLParserException {
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetMinute(now, -5), "yyyy-MM-dd HH:mm:ss");
        String afterReplaceSQL = originalSQL.replace("${start_time}", startTime);
        afterReplaceSQL = afterReplaceSQL.replace("${end_time}", endTime);
        Statement statement = CCJSqlParserUtil.parse(afterReplaceSQL);
        afterReplaceSQL = AutoPeriodHelper.buildSqlGranularity(statement).toString();
        log.info(afterReplaceSQL);
        Assert.assertFalse("ERROR: contains CHART/SAMPLE_GRANULARITY", afterReplaceSQL.contains("CHART_GRANULARITY") || afterReplaceSQL.contains("SAMPLE_GRANULARITY"));
        Assert.assertTrue("ERROR: not contains PT1S", afterReplaceSQL.contains("PT1S"));
    }

    @Test
    public void testSQLLast30Minutes() throws JSQLParserException {
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetMinute(now, -30), "yyyy-MM-dd HH:mm:ss");
        String afterReplaceSQL = originalSQL.replace("${start_time}", startTime);
        afterReplaceSQL = afterReplaceSQL.replace("${end_time}", endTime);
        Statement statement = CCJSqlParserUtil.parse(afterReplaceSQL);
        afterReplaceSQL = AutoPeriodHelper.buildSqlGranularity(statement).toString();
        log.info(afterReplaceSQL);
        Assert.assertFalse("ERROR: contains CHART/SAMPLE_GRANULARITY", afterReplaceSQL.contains("CHART_GRANULARITY") || afterReplaceSQL.contains("SAMPLE_GRANULARITY"));
        Assert.assertTrue("ERROR: don't expect period", afterReplaceSQL.contains("PT1S") && afterReplaceSQL.contains("PT30S"));
    }

    @Test
    public void testSQLLast1Hour() throws JSQLParserException {
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetHour(now, -1), "yyyy-MM-dd HH:mm:ss");
        String afterReplaceSQL = originalSQL.replace("${start_time}", startTime);
        afterReplaceSQL = afterReplaceSQL.replace("${end_time}", endTime);
        Statement statement = CCJSqlParserUtil.parse(afterReplaceSQL);
        afterReplaceSQL = AutoPeriodHelper.buildSqlGranularity(statement).toString();
        log.info(afterReplaceSQL);
        Assert.assertFalse("ERROR: contains CHART/SAMPLE_GRANULARITY", afterReplaceSQL.contains("CHART_GRANULARITY") || afterReplaceSQL.contains("SAMPLE_GRANULARITY"));
        Assert.assertTrue("ERROR: don't expect period", afterReplaceSQL.contains("PT1S") && afterReplaceSQL.contains("PT30S"));
    }

    @Test
    public void testSQLLast6Hour() throws JSQLParserException {
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetHour(now, -6), "yyyy-MM-dd HH:mm:ss");
        String afterReplaceSQL = originalSQL.replace("${start_time}", startTime);
        afterReplaceSQL = afterReplaceSQL.replace("${end_time}", endTime);
        Statement statement = CCJSqlParserUtil.parse(afterReplaceSQL);
        afterReplaceSQL = AutoPeriodHelper.buildSqlGranularity(statement).toString();
        log.info(afterReplaceSQL);
        Assert.assertFalse("ERROR: contains CHART/SAMPLE_GRANULARITY", afterReplaceSQL.contains("CHART_GRANULARITY") || afterReplaceSQL.contains("SAMPLE_GRANULARITY"));
        Assert.assertTrue("ERROR: don't expect period", afterReplaceSQL.contains("PT30S") && afterReplaceSQL.contains("PT1M"));
    }

    @Test
    public void testSQLLast12Hour() throws JSQLParserException {
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetHour(now, -6), "yyyy-MM-dd HH:mm:ss");
        String afterReplaceSQL = originalSQL.replace("${start_time}", startTime);
        afterReplaceSQL = afterReplaceSQL.replace("${end_time}", endTime);
        Statement statement = CCJSqlParserUtil.parse(afterReplaceSQL);
        afterReplaceSQL = AutoPeriodHelper.buildSqlGranularity(statement).toString();
        log.info(afterReplaceSQL);
        Assert.assertFalse("ERROR: contains CHART/SAMPLE_GRANULARITY", afterReplaceSQL.contains("CHART_GRANULARITY") || afterReplaceSQL.contains("SAMPLE_GRANULARITY"));
        Assert.assertTrue("ERROR: don't expect period", afterReplaceSQL.contains("PT30S") && afterReplaceSQL.contains("PT1M"));
    }

    @Test
    public void testSQLLast1Day() throws JSQLParserException {
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetDay(now, -1), "yyyy-MM-dd HH:mm:ss");
        String afterReplaceSQL = originalSQL.replace("${start_time}", startTime);
        afterReplaceSQL = afterReplaceSQL.replace("${end_time}", endTime);
        Statement statement = CCJSqlParserUtil.parse(afterReplaceSQL);
        afterReplaceSQL = AutoPeriodHelper.buildSqlGranularity(statement).toString();
        log.info(afterReplaceSQL);
        Assert.assertFalse("ERROR: contains CHART/SAMPLE_GRANULARITY", afterReplaceSQL.contains("CHART_GRANULARITY") || afterReplaceSQL.contains("SAMPLE_GRANULARITY"));
        Assert.assertTrue("ERROR: don't expect period", afterReplaceSQL.contains("PT1M") && afterReplaceSQL.contains("PT5M"));
    }

    @Test
    public void testSQLLast2Day() throws JSQLParserException {
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetDay(now, -2), "yyyy-MM-dd HH:mm:ss");
        String afterReplaceSQL = originalSQL.replace("${start_time}", startTime);
        afterReplaceSQL = afterReplaceSQL.replace("${end_time}", endTime);
        Statement statement = CCJSqlParserUtil.parse(afterReplaceSQL);
        afterReplaceSQL = AutoPeriodHelper.buildSqlGranularity(statement).toString();
        log.info(afterReplaceSQL);
        Assert.assertFalse("ERROR: contains CHART/SAMPLE_GRANULARITY", afterReplaceSQL.contains("CHART_GRANULARITY") || afterReplaceSQL.contains("SAMPLE_GRANULARITY"));
        Assert.assertTrue("ERROR: don't expect period", afterReplaceSQL.contains("PT1M") && afterReplaceSQL.contains("PT5M"));
    }

    @Test
    public void testSQLLast3Day() throws JSQLParserException {
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetDay(now, -3), "yyyy-MM-dd HH:mm:ss");
        String afterReplaceSQL = originalSQL.replace("${start_time}", startTime);
        afterReplaceSQL = afterReplaceSQL.replace("${end_time}", endTime);
        Statement statement = CCJSqlParserUtil.parse(afterReplaceSQL);
        afterReplaceSQL = AutoPeriodHelper.buildSqlGranularity(statement).toString();
        log.info(afterReplaceSQL);
        Assert.assertFalse("ERROR: contains CHART/SAMPLE_GRANULARITY", afterReplaceSQL.contains("CHART_GRANULARITY") || afterReplaceSQL.contains("SAMPLE_GRANULARITY"));
        Assert.assertTrue("ERROR: don't expect period", afterReplaceSQL.contains("PT1M") && afterReplaceSQL.contains("PT5M"));
    }

    @Test
    public void testSQLLast1Week() throws JSQLParserException {
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetWeek(now, -1), "yyyy-MM-dd HH:mm:ss");
        String afterReplaceSQL = originalSQL.replace("${start_time}", startTime);
        afterReplaceSQL = afterReplaceSQL.replace("${end_time}", endTime);
        Statement statement = CCJSqlParserUtil.parse(afterReplaceSQL);
        afterReplaceSQL = AutoPeriodHelper.buildSqlGranularity(statement).toString();
        log.info(afterReplaceSQL);
        Assert.assertFalse("ERROR: contains CHART/SAMPLE_GRANULARITY", afterReplaceSQL.contains("CHART_GRANULARITY") || afterReplaceSQL.contains("SAMPLE_GRANULARITY"));
        Assert.assertTrue("ERROR: don't expect period", afterReplaceSQL.contains("PT5M") && afterReplaceSQL.contains("PT1H"));
    }

    @Test
    public void testSQLLast1Month() throws JSQLParserException {
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetDay(now, -30), "yyyy-MM-dd HH:mm:ss");
        String afterReplaceSQL = originalSQL.replace("${start_time}", startTime);
        afterReplaceSQL = afterReplaceSQL.replace("${end_time}", endTime);
        Statement statement = CCJSqlParserUtil.parse(afterReplaceSQL);
        afterReplaceSQL = AutoPeriodHelper.buildSqlGranularity(statement).toString();
        log.info(afterReplaceSQL);
        Assert.assertFalse("ERROR: contains CHART/SAMPLE_GRANULARITY", afterReplaceSQL.contains("CHART_GRANULARITY") || afterReplaceSQL.contains("SAMPLE_GRANULARITY"));
        Assert.assertTrue("ERROR: don't expect period", afterReplaceSQL.contains("PT5M") && afterReplaceSQL.contains("PT1H"));
    }

    @Test
    public void testSQLLast365Day() throws JSQLParserException {
        String endTime = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");
        String startTime = DateUtil.format(DateUtil.offsetDay(now, -365), "yyyy-MM-dd HH:mm:ss");
        String afterReplaceSQL = originalSQL.replace("${start_time}", startTime);
        afterReplaceSQL = afterReplaceSQL.replace("${end_time}", endTime);
        Statement statement = CCJSqlParserUtil.parse(afterReplaceSQL);
        afterReplaceSQL = AutoPeriodHelper.buildSqlGranularity(statement).toString();
        log.info(afterReplaceSQL);
        Assert.assertFalse("ERROR: contains CHART/SAMPLE_GRANULARITY", afterReplaceSQL.contains("CHART_GRANULARITY") || afterReplaceSQL.contains("SAMPLE_GRANULARITY"));
        Assert.assertTrue("ERROR: don't expect period", afterReplaceSQL.contains("PT1H") && afterReplaceSQL.contains("P1D"));
    }
}

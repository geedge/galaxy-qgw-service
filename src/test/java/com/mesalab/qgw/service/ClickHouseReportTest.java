package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.configuration.TestProperties;
import com.mesalab.common.utils.JsonMapper;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.Map;

@Slf4j
@EnableAutoConfiguration
public class ClickHouseReportTest extends GalaxyQGWApplicationTests {

    @Autowired
    public TestRestTemplate restTemplate;

    @Autowired
    private TestProperties properties;




    @Before
    public void testBefore() {
        log.info("=================================================CLICKHOUSE REPORT TEST BEGIN=================================================");
    }


    @Test
    public void testCustomizableReport() {

        String sql = "select toDateTime(intDiv(toUInt32(toDateTime(toDateTime(recv_time))), 300)*300) as _time ,  domain as `Domain`,COUNT(DISTINCT(client_ip)) as `nums` from session_record_log where recv_time >= toStartOfDay($exe_time)-86400 and recv_time <= toStartOfDay($exe_time)and notEmpty(domain) and  domain in ( select  domain from session_record_log where recv_time >= toStartOfDay($exe_time)-86400 and recv_time <= toStartOfDay($exe_time) and notEmpty(domain) group by  domain order by sum(s2c_byte_num+c2s_byte_num) DESC  limit 10 )  group by toDateTime(intDiv(toUInt32(toDateTime(toDateTime(recv_time))), 300)*300), domain order by toDateTime(intDiv(toUInt32(toDateTime(toDateTime(recv_time))), 300)*300) DESC  limit 500";

        String url = getPrefixUrl() + "&option=long-term&reportId=121&query=" + replaceExecuteTime(sql) ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertNotEquals((int)map.get("status"), 500);
    }

    @Test
    public void testTop100SourceBySessions() {
        String sql = "select client_ip,count(*) as sessions from session_record_log where recv_time>= toStartOfDay($exe_time)-604800 and recv_time< toStartOfDay($exe_time) group by client_ip order by sessions desc limit 0,100";
        String url = getPrefixUrl() +  "&query=" + replaceExecuteTime(sql) ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertNotEquals((int)map.get("status"), 500);
    }


    @Test
    public void testTop100DestinationsBySessions() {
        String sql = "select server_ip,count(*) as sessions from session_record_log where recv_time>= toStartOfDay($exe_time)-604800 and recv_time< toStartOfDay($exe_time) group by server_ip order by sessions desc limit 0,100";
        String url = getPrefixUrl() +  "&query=" + replaceExecuteTime(sql) ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    @Test
    public void testTop100WebsiteDomainsByBandwidth() {
        String sql = "SELECT domain AS domain,SUM(coalesce(c2s_byte_num, 0)) AS sent_bytes,SUM(coalesce(s2c_byte_num, 0)) AS received_bytes,SUM(plus(coalesce(c2s_byte_num, 0),coalesce(s2c_byte_num, 0))) AS bytes FROM session_record_log WHERE recv_time >= toStartOfDay($exe_time)-86400 AND recv_time < toStartOfDay($exe_time) and notEmpty(domain) GROUP BY domain ORDER BY bytes DESC LIMIT 100";
        String url = getPrefixUrl() +  "&query=" + replaceExecuteTime(sql) ;
        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testDateTimeThisDay() {
        String sql = "select toDateTime(recv_time) from policy_event_log where recv_time>=toStartOfDay($exe_time) and recv_time<toStartOfDay($exe_time)+86400 order by recv_time desc limit 10";
        String url = getPrefixUrl() +  "&query=" + replaceExecuteTime(sql) ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testDateTimeLastDay() {
        String sql = "select toDateTime(recv_time) from policy_event_log where recv_time>=toStartOfDay($exe_time)-86400 and recv_time<toStartOfDay($exe_time) order by recv_time desc limit 10";
        String url = getPrefixUrl() +  "&query=" + replaceExecuteTime(sql) ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }



    @Test
    public void testDateTimeToStartOfFiveMinute() {
        String sql = "select toDateTime(recv_time) from policy_event_log where recv_time>=toStartOfFiveMinute($exe_time) and recv_time<toStartOfFiveMinute($exe_time)+300 order by recv_time desc limit 10";

        String url = getPrefixUrl() +  "&query=" + replaceExecuteTime(sql) ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testDateTimetToStartOfHour() {
        String sql = "select toStartOfHour($exe_time) as startTime,toStartOfHour($exe_time)+3600 as endime,toDateTime(recv_time) as recvTime from policy_event_log where recv_time>=toStartOfHour(toDateTime('2019-05-31 18:37:45')) and recv_time<toStartOfHour(toDateTime('2019-05-31 18:37:45'))+3600 order by recv_time desc limit 10";
        String url = getPrefixUrl() +  "&query=" + replaceExecuteTime(sql) ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testDateTimeToStartOfMonth() {
        String sql = "select toDateTime(toStartOfMonth($exe_time)) as startTime,toDateTime(toStartOfMonth(addMonths($exe_time,1))) as endime,toDateTime(recv_time) as recvTime from policy_event_log where recv_time>=toDateTime(toStartOfMonth($exe_time)) and recv_time<toDateTime(toStartOfMonth(addMonths($exe_time,1))) order by recv_time desc limit 10";
        String url = getPrefixUrl() +  "&query=" + replaceExecuteTime(sql) ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testDateTimeToStartOfYear() {
        String sql = "select toDateTime(toStartOfYear($exe_time)) as startTime,toDateTime(toStartOfYear(addYears($exe_time,1))) as endime,toDateTime(recv_time) as recvTime from policy_event_log where recv_time>=toDateTime(toStartOfYear($exe_time)) and recv_time<toDateTime(toStartOfYear(addYears($exe_time,1))) order by recv_time desc limit 10";
        String url = getPrefixUrl() +  "&query=" + replaceExecuteTime(sql) ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }




    @Test
    public void testDateTimeToMonday() {
        String sql = "select toDateTime(toMonday($exe_time))-86400 as startTime,toDateTime(toMonday($exe_time))+518400 as endTIme, toDateTime(recv_time) from policy_event_log where recv_time>=toDateTime(toMonday($exe_time))-86400 and recv_time<toDateTime(toMonday($exe_time))+518400 order by recv_time desc limit 10";
        String url = getPrefixUrl() +  "&query=" + replaceExecuteTime(sql) ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testDateTimeLastQuater() {
        String sql = "select toStartOfDay($exe_time) - 8035200 as startTime,toStartOfDay($exe_time) as endTIme, toDateTime(recv_time) from policy_event_log where recv_time>=toStartOfDay($exe_time)-8035200 and recv_time<toStartOfDay($exe_time) order by recv_time desc limit 10";
        String url = getPrefixUrl() +  "&query=" + replaceExecuteTime(sql) ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testDateTimePreviousWeek() {
        String sql = "select toDateTime(toMonday(subtractWeeks($exe_time, 1))) - 86400 as startTime,toDateTime(toMonday(subtractWeeks($exe_time, 1))) + 518400 as endTIme, toDateTime(recv_time) from policy_event_log where recv_time>=toDateTime(toMonday(subtractWeeks($exe_time, 1))) - 86400 and recv_time<toDateTime(toMonday(subtractWeeks($exe_time, 1))) + 518400 order by recv_time desc limit 10";
        String url = getPrefixUrl() +  "&query=" + replaceExecuteTime(sql) ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testFQDNByBandwidth() {
        String sql = "SELECT host AS fqdn,SUM(coalesce(c2s_byte_num, 0)) as sent_bytes,SUM(coalesce(s2c_byte_num, 0)) as received_bytes,SUM(coalesce(c2s_byte_num, 0) + coalesce(s2c_byte_num, 0)) as bytes FROM session_record_log WHERE recv_time>=toDateTime(toMonday(subtractWeeks($exe_time, 1))) - 86400 and recv_time<toDateTime(toMonday(subtractWeeks($exe_time, 1))) + 518400 and host like '%google%' and notEmpty(host) GROUP BY host ORDER BY bytes DESC";
        String url = getPrefixUrl() +  "&query=" + replaceExecuteTime(sql) ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    private String getPrefixUrl() {

        return "http://" + properties.getUrl() + "/?test=1";
    }


    private String replaceExecuteTime(String sql) {

        return sql.replaceAll("\\$exe_time", getExecuteTime(properties.getExecuteTIme()));
    }

    private String getExecuteTime(String executeTime) {

        if (StringUtil.isNotEmpty(DateUtils.convertStringToDate(executeTime, DateUtils.YYYY_MM_DD_HH24_MM_SS))) {
            return "toDateTime( '"+executeTime+"' )";
        } else {
            return "toDateTime( '"+DateUtils.getCurrentDate(DateUtils.YYYY_MM_DD_HH24_MM_SS)+"' )";
        }
    }


















    @After
    public void testAfter() {
        log.info("=================================================CLICKHOUSE REPORT TEST  END=================================================");

    }



}

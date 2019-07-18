package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.utils.JsonMapper;
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

    private String executeTime = "toDateTime('2019-06-01 18:37:45')";

    private String url = "http://127.0.0.1:9999/?test=1";




    @Before
    public void testBefore() {
        log.info("=================================================CLICKHOUSE REPORT TEST BEGIN=================================================");
    }


    @Test
    public void testCustomizableReport() {
        String sql = "select toDateTime(intDiv(toUInt32(toDateTime(toDateTime(recv_time))), 300)*300) as _time ,  domain as `Domain`,COUNT(DISTINCT(client_ip)) as `nums` from session_record_log where recv_time >= toStartOfDay($exe_time)-86400 and recv_time <= toStartOfDay($exe_time)and notEmpty(domain) and  domain in ( select  domain from session_record_log where recv_time >= toStartOfDay($exe_time)-86400 and recv_time <= toStartOfDay($exe_time) and notEmpty(domain) group by  domain order by sum(s2c_byte_num+c2s_byte_num) DESC  limit 10 )  group by toDateTime(intDiv(toUInt32(toDateTime(toDateTime(recv_time))), 300)*300), domain order by toDateTime(intDiv(toUInt32(toDateTime(toDateTime(recv_time))), 300)*300) DESC  limit 500";
        sql = sql.replaceAll("\\$exe_time", executeTime);
        url = url + "&option=long-term&reportId=121&query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    @Test
    public void testTop100SourceBySessions() {
        String sql = "select client_ip,count(*) as sessions from session_record_log where recv_time>= toStartOfDay($exe_time)-604800 and recv_time< toStartOfDay($exe_time) group by client_ip order by sessions desc limit 0,100";
        sql = sql.replaceAll("\\$exe_time", executeTime);
        url = url + "&query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertNotEquals((int)map.get("status"), 500);
    }


    @Test
    public void testTop100DestinationsBySessions() {
        String sql = "select server_ip,count(*) as sessions from session_record_log where recv_time>= toStartOfDay($exe_time)-604800 and recv_time< toStartOfDay($exe_time) group by server_ip order by sessions desc limit 0,100";
        sql = sql.replaceAll("\\$exe_time", executeTime);
        url = url + "&query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }

    @Test
    public void testTop100WebsiteDomainsByBandwidth() {
        String sql = "SELECT domain AS domain,SUM(coalesce(c2s_byte_num, 0)) AS sent_bytes,SUM(coalesce(s2c_byte_num, 0)) AS received_bytes,SUM(plus(coalesce(c2s_byte_num, 0),coalesce(s2c_byte_num, 0))) AS bytes FROM session_record_log WHERE recv_time >= toStartOfDay($exe_time)-86400 AND recv_time < toStartOfDay($exe_time) and notEmpty(domain) GROUP BY domain ORDER BY bytes DESC LIMIT 100";
        sql = sql.replaceAll("\\$exe_time", executeTime);
        url = url + "&query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testDateTimeThisDay() {
        String sql = "select toDateTime(recv_time) from policy_event_log where recv_time>=toStartOfDay(toDateTime('2019-05-31 18:37:45')) and recv_time<toStartOfDay(toDateTime('2019-05-31 18:37:45'))+86400 order by recv_time desc limit 10";
        url = url + "&query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testDateTimeLastDay() {
        String sql = "select toDateTime(recv_time) from policy_event_log where recv_time>=toStartOfDay(toDateTime('2019-05-31 18:37:45'))-86400 and recv_time<toStartOfDay(toDateTime('2019-05-31 18:37:45')) order by recv_time desc limit 10";
        url = url + "&query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }



    @Test
    public void testDateTimeToStartOfFiveMinute() {
        String sql = "select toDateTime(recv_time) from policy_event_log where recv_time>=toStartOfFiveMinute(toDateTime('2019-05-31 18:37:45')) and recv_time<toStartOfFiveMinute(toDateTime('2019-05-31 18:37:45'))+300 order by recv_time desc limit 10";
        url = url + "&query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testDateTimetToStartOfHour() {
        String sql = "select toStartOfHour(toDateTime('2019-05-31 18:37:45')) as startTime,toStartOfHour(toDateTime('2019-05-31 18:37:45'))+3600 as endime,toDateTime(recv_time) as recvTime from policy_event_log where recv_time>=toStartOfHour(toDateTime('2019-05-31 18:37:45')) and recv_time<toStartOfHour(toDateTime('2019-05-31 18:37:45'))+3600 order by recv_time desc limit 10";
        url = url + "&query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testDateTimeToStartOfMonth() {
        String sql = "select toDateTime(toStartOfMonth(toDateTime('2019-05-31 18:37:45'))) as startTime,toDateTime(toStartOfMonth(addMonths(toDateTime('2019-05-31 18:37:45'),1))) as endime,toDateTime(recv_time) as recvTime from policy_event_log where recv_time>=toDateTime(toStartOfMonth(toDateTime('2019-05-31 18:37:45'))) and recv_time<toDateTime(toStartOfMonth(addMonths(toDateTime('2019-05-31 18:37:45'),1))) order by recv_time desc limit 10";
        url = url + "&query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testDateTimeToStartOfYear() {
        String sql = "select toDateTime(toStartOfYear(toDateTime('2019-05-31 18:37:45'))) as startTime,toDateTime(toStartOfYear(addYears(toDateTime('2019-05-31 18:37:45'),1))) as endime,toDateTime(recv_time) as recvTime from policy_event_log where recv_time>=toDateTime(toStartOfYear(toDateTime('2019-05-31 18:37:45'))) and recv_time<toDateTime(toStartOfYear(addYears(toDateTime('2019-05-31 18:37:45'),1))) order by recv_time desc limit 10";
        url = url + "&query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }




    @Test
    public void testDateTimeToMonday() {
        String sql = "select toDateTime(toMonday(toDateTime('2019-05-31 18:37:45')))-86400 as startTime,toDateTime(toMonday(toDateTime('2019-05-31 18:37:45')))+518400 as endTIme, toDateTime(recv_time) from policy_event_log where recv_time>=toDateTime(toMonday(toDateTime('2019-05-31 18:37:45')))-86400 and recv_time<toDateTime(toMonday(toDateTime('2019-05-31 18:37:45')))+518400 order by recv_time desc limit 10";

        url = url + "&query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testDateTimeLastQuater() {
        String sql = "select toStartOfDay(toDateTime('2019-05-31 18:37:45')) - 8035200 as startTime,toStartOfDay(toDateTime('2019-05-31 18:37:45')) as endTIme, toDateTime(recv_time) from policy_event_log where recv_time>=toStartOfDay(toDateTime('2019-05-31 18:37:45'))-8035200 and recv_time<toStartOfDay(toDateTime('2019-05-31 18:37:45')) order by recv_time desc limit 10";

        url = url + "&query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testDateTimePreviousWeek() {
        String sql = "select toDateTime(toMonday(subtractWeeks(toDateTime('2019-06-05 18:37:45'), 1))) - 86400 as startTime,toDateTime(toMonday(subtractWeeks(toDateTime('2019-06-05 18:37:45'), 1))) + 518400 as endTIme, toDateTime(recv_time) from policy_event_log where recv_time>=toDateTime(toMonday(subtractWeeks(toDateTime('2019-06-05 18:37:45'), 1))) - 86400 and recv_time<toDateTime(toMonday(subtractWeeks(toDateTime('2019-06-05 18:37:45'), 1))) + 518400 order by recv_time desc limit 10";

        url = url + "&query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


    @Test
    public void testFQDNByBandwidth() {
        String sql = "SELECT host AS fqdn,SUM(coalesce(c2s_byte_num, 0)) as sent_bytes,SUM(coalesce(s2c_byte_num, 0)) as received_bytes,SUM(coalesce(c2s_byte_num, 0) + coalesce(s2c_byte_num, 0)) as bytes FROM session_record_log WHERE recv_time>=toDateTime(toMonday(subtractWeeks(toDateTime('2019-06-05 18:37:45'), 1))) - 86400 and recv_time<toDateTime(toMonday(subtractWeeks(toDateTime('2019-06-05 18:37:45'), 1))) + 518400 and host like '%qq%' and notEmpty(host) GROUP BY host ORDER BY bytes DESC";

        url = url + "&query=" + sql ;

        Map map = restTemplate.getForObject(url, Map.class);

        log.info(JsonMapper.toJsonString(map));
        Assert.assertEquals((int)map.get("status"), 200);
    }


















    @After
    public void testAfter() {
        log.info("=================================================CLICKHOUSE REPORT TEST  END=================================================");

    }



}

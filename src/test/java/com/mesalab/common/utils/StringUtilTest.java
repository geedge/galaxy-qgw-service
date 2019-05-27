package com.mesalab.common.utils;

import com.mesalab.GalaxyQGWApplicationTests;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

@Slf4j
@EnableAutoConfiguration
public class StringUtilTest  extends GalaxyQGWApplicationTests {

    @Test
    public void testMd5() {
       String srcResult = "select app_id as appid, count(distinct(s_ip)) as sunqnum,count(distinct(d_ip)) as dunqnum,entrance_id as entranceid,device_id as deviceid,count(*) as linknum,sum(c2s_pkt_num) as c2spktnum ,sum(s2c_pkt_num) as s2cpktnum ,sum(c2s_byte_num) as c2sbytelen,sum(s2c_byte_num) as s2cbytelen from tbs_ods_ntc_conn_record_log t WHERE found_time >= toDateTime('2019-02-18 23:55:00') AND found_time < toDateTime('2019-02-19 00:00:00') GROUP BY app_id,entrance_id,device_id";

       log.info(DigestUtils.md5Hex(srcResult));//73e4b42a740d11a140f28e79fc394039
        log.info(StringUtil.md5(srcResult));
    }

}




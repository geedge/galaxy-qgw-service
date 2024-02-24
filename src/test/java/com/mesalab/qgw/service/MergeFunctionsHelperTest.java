package com.mesalab.qgw.service;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.utils.sqlparser.FunctionsMergeHelper;
import net.sf.jsqlparser.JSQLParserException;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * TODO
 *
 * @Classname MergeFunctionsHelperTest
 * @Date 2024/2/24 14:22
 * @Author wWei
 */
@EnableAutoConfiguration
public class MergeFunctionsHelperTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();

    @Test
    public void testPlainSelectSQL() throws JSQLParserException {
        String originalSQL = "SELECT FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(column), 'PT5M', 'zero')) as a ,TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(column), 'PT5M', 'zero') as b ,TIME_FLOOR_WITH_FILL(long_column, 'PT5M', 'zero') as c, SUM(bytes) AS bytes  FROM table  GROUP BY FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(column), 'PT5M', 'zero')) ,TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(column), 'PT5M', 'zero'),TIME_FLOOR_WITH_FILL(long_column, 'PT5M', 'zero') order by  FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(column), 'PT5M', 'zero')) asc ,TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(column), 'PT5M', 'zero') desc ,TIME_FLOOR_WITH_FILL(long_column, 'PT5M', 'zero') desc, bytes asc, a desc limit 1";
        log.info(FunctionsMergeHelper.build(originalSQL));
    }

    @Test
    public void testNestedSQL() throws JSQLParserException {
        String originalSQL = "SELECT FROM_UNIXTIME(TIME_FLOOR_WITH_FILL( stat_time, 'PT5S','zero' )) AS stat_time, SUM(bytes) AS bytes FROM ( SELECT TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time),'PT1S') AS stat_time, SUM(in_bytes + out_bytes) AS bytes FROM statistics_rule WHERE __time >= '2024-02-01 00:00:00'  and __time < '2024-02-02 00:00:00' and rule_id = 301 GROUP BY TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time),'PT1S') ORDER BY TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time),'PT1S') ASC ) GROUP BY FROM_UNIXTIME(TIME_FLOOR_WITH_FILL( stat_time,'PT5S','zero' )) ORDER BY stat_time ASC, bytes DESC LIMIT 10";
        log.info(FunctionsMergeHelper.build(originalSQL));
    }
}

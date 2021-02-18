package com.mesalab.qgw.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.lang.Assert;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Strings;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.utils.SQLHelper;
import com.mesalab.qgw.benchmark.clickhouse.ClickhouseWriter;
import com.mesalab.qgw.service.impl.TestSqlServiceImpl;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
//clickhouse-benchmark  -i 93  --host 127.0.0.1 --port 9001 --user default --password "ceiec2019" --database tsg_galaxy_v3 < /root/session_record_queries_20210113203103.sql@EnableAutoConfiguration
@EnableAutoConfiguration
public class ClickHouseTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();
    private ClickhouseWriter writer = ClickhouseWriter.builder();

    @Before
    public void testBefore() {
        log.info("=================================================CLICKHOUSE TEST BEGIN=================================================");
    }


    @Test
    public void testSqlParser() throws IOException {
        String pocFile = writer.buildPocSQL();
        Optional<File> ckTestFile =  Optional.of(new File(pocFile));
        if (ckTestFile.isPresent()) {
            if (!ckTestFile.get().isFile()) {
                log.warn("UnKnow File " + ckTestFile.get());
                return;
            }

            List<String> rawLines = new FileReader(ckTestFile.get()).readLines();
            rawLines.stream().forEach(line -> {
                if (Strings.isNullOrEmpty(line)) {
                    return;
                }
                Statement parse = null;
                try {
                    parse = CCJSqlParserUtil.parse(line);
                } catch (JSQLParserException e) {
                    log.error("Parse SQL ERROR : {}, SQL is {} " , e, line);
                }

                Assert.notNull(parse);
            });

            FileUtil.del(ckTestFile.get());

        }
    }

    @After
    public void testAfter() {
        log.info("=================================================CLICKHOUSE TEST  END=================================================");

    }

}

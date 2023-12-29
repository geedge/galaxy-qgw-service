package com.mesalab.qgw.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.lang.Assert;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.enums.DBType;
import com.mesalab.qgw.benchmark.DialectWriter;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.io.File;
import java.util.List;
import java.util.Optional;

@EnableAutoConfiguration
public class DruidTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();

    @Autowired
    private DialectWriter dialectWriter;



    @Test
    public void testSqlParser() {
        String pocFile = dialectWriter.buildPocSQL(Lists.newLinkedList(), DBType.DRUID.getValue(), null);
        Optional<File> ckTestFile =  Optional.of(new File(pocFile));
        if (ckTestFile.isPresent()) {
            if (!ckTestFile.get().isFile()) {
                log.warn("Not a file." + ckTestFile.get());
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



    @Before
    public void testBefore() {
        log.info("=================================================DRUID TEST BEGIN=================================================");
    }

    @After
    public void testAfter() {
        log.info("=================================================DRUID TEST  END=================================================");

    }





}

package com.mesalab.benchmark.clickhouse;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.io.file.FileWriter;
import cn.hutool.core.lang.Assert;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.GalaxyQGWApplicationTests;
import com.zdjizhi.utils.DateUtils;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
//clickhouse-benchmark  -i 93  --host 127.0.0.1 --port 9001 --user default --password "ceiec2019" --database tsg_galaxy_v3 < /root/session_record_queries_20210113203103.sql@EnableAutoConfiguration
@EnableAutoConfiguration
public class ClickhouseWriterStatementTest extends GalaxyQGWApplicationTests {
    private static final Log log = LogFactory.get();
    private static final String TITLE_PREFIX = "--Q";
    private static final String LABEL_COMMON_FILTER = "\\$common_filter";
    private static final String LABEL_INDEX_FILTER = "\\$index_filter";
    private static final String LABEL_TIME_START = "\\$start";
    private static final String LABEL_TIME_END = "\\$end";
    public static final Map<String, String> environment = Maps.newLinkedHashMap();
    public static final Map<String, String> commonFilters = Maps.newLinkedHashMap();
    public static final Map<String, String> indexFilters = Maps.newLinkedHashMap();
    public static final Pattern commonFilterPattern = Pattern.compile("\\$common_filter");
    public static final Pattern indexFilterPattern = Pattern.compile("\\$index_filter");
    public static final boolean IS_TITLE = false;
    static {
        environment.put(LABEL_TIME_START, "2021-01-11 10:00:00");
        environment.put(LABEL_TIME_END, "2021-01-13 11:00:00");

        commonFilters.put("01", "common_client_ip='192.168.44.21'and common_server_port=443 ");
        commonFilters.put("02", "common_server_ip='8.8.8.8' and common_server_port=443 ");
        commonFilters.put("03", "http_domain like '%baidu%' and common_server_port=443 ");
        commonFilters.put("04", "http_domain like '%baidu%'and common_server_port=443 ");

        indexFilters.put("01", "common_client_ip='192.168.44.21'");
        indexFilters.put("02", "common_server_ip='8.8.8.8'");
        indexFilters.put("03", "http_domain like '%baidu%'");
        indexFilters.put("04", "common_sled_ip='%192.168%'");
        indexFilters.put("05", "common_subscriber_id='%test%'");

    }

    private void buildQuery() {
        try {
            FileReader sampleReader = new FileReader(new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + "session_record_queries_sample.sql");
            FileWriter writer = new FileWriter(new File("").getCanonicalPath()
                    + File.separator + "benchmark" + File.separator + "session_record_queries" + "_"
                    + DateUtils.getCurrentDate(DateUtils.YYYYMMDDHH24MMSS)+ ".sql");
            List<String> rawLines = sampleReader.readLines();
            List<String> resultLines = Lists.newArrayList();
            rawLines.stream().forEach(line -> {

                if (line.contains(TITLE_PREFIX)) {
                    if(IS_TITLE) {
                        resultLines.add(line);
                    }
                } else {
                    if (commonFilterPattern.matcher(line).find()) {
                        for (String filterKey : commonFilters.keySet()) {
                            String replaceLine = line.replaceAll(LABEL_TIME_START, environment.get(LABEL_TIME_START))
                                    .replaceAll(LABEL_TIME_END, environment.get(LABEL_TIME_END))
                                    .replaceAll(LABEL_COMMON_FILTER, commonFilters.get(filterKey));

                            resultLines.add(replaceLine);

                        }

                    } else if(indexFilterPattern.matcher(line).find()) {
                        for (String filterKey : indexFilters.keySet()) {
                            String replaceLine = line.replaceAll(LABEL_TIME_START, environment.get(LABEL_TIME_START))
                                    .replaceAll(LABEL_TIME_END, environment.get(LABEL_TIME_END))
                                    .replaceAll(LABEL_INDEX_FILTER, indexFilters.get(filterKey));

                            resultLines.add(replaceLine);

                        }

                    } else {
                        resultLines.add(
                                line.replaceAll(LABEL_TIME_START, environment.get(LABEL_TIME_START))
                                        .replaceAll(LABEL_TIME_END, environment.get(LABEL_TIME_END)));
                    }

                }

            });
            writer.appendLines(resultLines);
            resultLines.clear();

        } catch (IOException e) {
            log.error("Not found file {}", "sample.sql");
        }
    }

    @Before
    public void testBefore() {
        log.info("======  Begin Clickhouse Writer Statement Test  ======  ");
    }

    @Test
    public void testSqlParser() throws IOException {
        buildQuery();
        log.info("Clickhouse parse SQL  beginning!");
        Optional<File> ckTestFile =  FileUtil.loopFiles(new File("").getCanonicalPath()
                + File.separator + "benchmark").stream()
                .sorted(Comparator.comparing(File::lastModified).reversed()).findFirst();

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

        log.info("Clickhouse parse SQL completed!");
    }






}

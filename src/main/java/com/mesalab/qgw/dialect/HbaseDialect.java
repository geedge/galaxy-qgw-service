package com.mesalab.qgw.dialect;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.mesalab.calcite.CalciteMemoryUtils;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.HBaseHttpSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.model.api.udf.UDF;
import com.mesalab.qgw.service.impl.APIServiceImpl;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Alias;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HbaseDialect extends  AbstractDataSourceDialect {
    private HBaseHttpSource hBaseHttpSource = (HBaseHttpSource) SpringContextUtil.getBean("hBaseHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");
    private Stopwatch watch = Stopwatch.createUnstarted();

    public HbaseDialect(ApiParam param) {
        initialize(param);
    }

    @Override
    public boolean supportsLimit() {
        return false;
    }

    @Override
    public String convertQuery(String sql) {
        return sql;
    }

    @Override
    public String findBestQuery() {
        return param.getSql();
    }


    @Override
    public Dialect executeQuery() {
        watch.start();
        StringBuilder urlBuilder = new StringBuilder("http://")
                .append(hBaseHttpSource.getUrl()).append("/");

        String queryId = StringUtil.isNotBlank(param.getResultId()) ? param.getResultId() : param.getReportId();
        StringBuilder queryparamBuilder = new StringBuilder(hBaseHttpSource.getDbName())
                .append(":").append(hBaseHttpSource.getTableName()).append("/")
                .append(getQueryId(queryId, param.getQuery())).append("/")
                .append(hBaseHttpSource.getColumnFamily()).append(":").append(hBaseHttpSource.getColumnName());

        Map<String, String> headers = Maps.newHashMap();
        headers.put("Accept", "application/json");

        Map<String, String> results = httpClientService.httpGet(urlBuilder.toString() + queryparamBuilder.toString(), headers);

        if (results.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), null,
                    JsonMapper.fromJsonString(decryptHbaseResult(results.get("result")), Map.class), null, null, QueryFormatEnum.JSON.getValue());
            getAndParserFormatSql();
        } else {
            baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), null,
                    results.get("result"), null, null, param.getFormat());
        }
        return this;
    }

    private Map<String, String> getFormatSql() {
        StringBuilder urlBuilder = new StringBuilder("http://")
                .append(hBaseHttpSource.getUrl()).append("/");
        String queryId = StringUtil.isNotBlank(param.getResultId()) ? param.getResultId() : param.getReportId();
        StringBuilder queryparamBuilder = new StringBuilder(hBaseHttpSource.getDbName())
                .append(":").append(hBaseHttpSource.getTableName()).append("/")
                .append(getQueryId(queryId, param.getQuery())).append("/")
                .append("detail:format_sql");
        log.info("Report Query：{}, queryId Id: {}, sql:{}", urlBuilder.toString() + queryparamBuilder.toString(), queryId, param.getQuery());

        Map<String, String> headers = Maps.newHashMap();

        headers.put("Accept", "application/json");

        Map<String, String> formatSql = httpClientService.httpGet(urlBuilder.toString() + queryparamBuilder.toString(), headers);
        if (formatSql.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            formatSql.put("result", decryptHbaseResult(formatSql.get("result")));
        }


        return formatSql;
    }



    @Override
    public Dialect executeSyntaxCheck() {
        return this;
    }



    @Override
    public BaseResult build() {
        Object dataObject = baseResult.getData();

        if (baseResult.isSuccess()) {
            Map<String, Object> maps = (Map<String, Object>) dataObject;
            Map<String, Object> statisticMap = (Map<String, Object>) maps.get("statistics");

            Map<String, Object> statistics = Maps.newLinkedHashMap();
            Double executeTime = watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0;
            statistics.put("elapsed", String.format("%.2f", (double) statisticMap.get("elapsed")+ executeTime));
            statistics.put("rows_read", statisticMap.get("rows_read"));

            int resultSize = StringUtil.isEmpty(maps.get("data")) ? 0 : JsonMapper.toJsonString(maps.get("data")).getBytes().length;

            statistics.put("result_size", resultSize);
            baseResult.setData(maps.get("data"));
            baseResult.setMeta(maps.get("meta"));
            baseResult.setStatistics(statistics);


        } else if (baseResult.getStatus().equals(HttpStatus.SC_NOT_FOUND)) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND, String.valueOf(dataObject));
        } else if (baseResult.getStatus().equals(HttpStatus.SC_INTERNAL_SERVER_ERROR)) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, String.valueOf(dataObject));
        } else {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, String.valueOf(dataObject));
        }
        return baseResult;
    }

    /**
     * 获取formatSql并解析
     */
    private void getAndParserFormatSql() {
        if (baseResult.isSuccess()) {
            Map<String, String> formatSql = getFormatSql();
            String sql = formatSql.get("result");
            log.info("从离线报表获取formatSql，解析并重制SQLQuerySource对象: " + sql);
            APIServiceImpl.convertQueryRecursive(param, APIServiceImpl.parserSQLByAst(sql));
            param.setQuery(param.getDbQuerySource().getSqlBody());
        }
    }


    /**
     * 解析json并将结果进行base64解密
     * @param hbaseResult
     * @return
     */
    private String  decryptHbaseResult(String hbaseResult) {

        String result = "";

        try {
            Map<String, Object> maps =(Map<String, Object>) JsonMapper.fromJsonString(hbaseResult,  Map.class);

            List<Object> rowList = (ArrayList)maps.get("Row");

            if (StringUtil.isNotEmpty(rowList)) {
                Map<String ,Object> keyMap = (Map<String ,Object>)rowList.get(0);
                String encryptResult = ((Map<String ,Object>)((ArrayList)keyMap.get("Cell")).get(0)).get("$").toString();
                result = new String(Base64.getDecoder().decode(encryptResult), StandardCharsets.UTF_8);

            }


        } catch (Exception e) {

            log.error("Parser HBase Result error: {}", e.getMessage());

        }


        return result;

    }


    /**
     * 通过SQL生成查询唯一kEY
     * @param sql
     * @param  reportId
     * @return
     */
    private  static String getQueryId(String reportId, String sql) {
        return DigestUtils.md5Hex(reportId + sql.trim());
    }




}

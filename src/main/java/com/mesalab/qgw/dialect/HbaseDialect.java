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
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HbaseDialect implements Dialect{
    private HBaseHttpSource hBaseHttpSource = (HBaseHttpSource) SpringContextUtil.getBean("hBaseHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");
    private Map<String, String> results;
    private ApiParam param;
    private Stopwatch watch = Stopwatch.createUnstarted();

    public HbaseDialect(ApiParam param) {

        this.param = param;
        results = Maps.newHashMap();
    }

    @Override
    public boolean supportsLimit() {
        return false;
    }

    @Override
    public String getLimitString(String sql, int offset, int limit) {
        return sql;
    }


    public String getLimitString(String sql, SQLQuerySource sqlQuerySource) {

        return sql;
    }

    @Override
    public String queryTransformation(String sql) {

        return sql;
    }

    @Override
    public String queryOptimization(String sql) {
        return sql;
    }

    @Override
    public Dialect queryDefaultPreProcess() {
        return this;
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

        log.info("Report Query：{}, queryId Id: {}, sql:{}", urlBuilder.toString() + queryparamBuilder.toString(), queryId, param.getQuery());

        Map<String, String> headers = Maps.newHashMap();

        headers.put("Accept", "application/json");

        results = httpClientService.httpGet(urlBuilder.toString() + queryparamBuilder.toString(), headers);
        if (results.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            results.put("result", decryptHbaseResult(results.get("result")));
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
    public Dialect executeMemoryQuery() {
        if (!param.getSqlQuerySource().isNeedMemoryQuery()) {
            return this;
        }
        if (!results.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            throw new BusinessException(BaseResultEnum.NOT_FOUND.getCode(), "report query error, result: " + results.get("result"), null);
        }
        try {
            String result = results.get("result");
            Map<String, Object> maps = (Map<String, Object>) JsonMapper.fromJsonString(result, Map.class);
            List<Map<String, Object>> data = (List<Map<String, Object>>) maps.get("data");
            List<Map<String, String>> meta = (List<Map<String, String>>) maps.get("meta");
            List list = CalciteMemoryUtils.calciteQueryMemoryData(meta, data, param);
            maps.put("data", list);
            results.put("result", JsonMapper.toJsonString(maps));
        } catch (Exception ex) {
            throw new BusinessException(BaseResultEnum.SERVER_ERROR.getCode(), "calcite execute query memory is error", ex);
        }
        return this;
    }

    @Override
    public Dialect executeSyntaxCheck() {
        return this;
    }

    @Override
    public Dialect executeUDF() {
        SQLQuerySource sqlQuerySource;
        if (param.getSqlQuerySource().isNeedMemoryQuery()) {
            sqlQuerySource = param.getSqlQuerySource().getSubSqlQuerySources().get(0);
        } else {
            sqlQuerySource = param.getSqlQuerySource();
        }
        Set<UDF> udfSet = sqlQuerySource.getUdfSet();
        if (!udfSet.isEmpty()) {
            getAndParserFormatSql();
            if (param.getSqlQuerySource().isNeedMemoryQuery()) {
                sqlQuerySource = param.getSqlQuerySource().getSubSqlQuerySources().get(0);
            } else {
                sqlQuerySource = param.getSqlQuerySource();
            }
        }
        for (UDF udf : udfSet) {
            if (StringUtil.isNotEmpty(results)) {
                if (results.get("status").equals(String.valueOf(HttpStatus.SC_OK)) &&
                        param.getFormat().equalsIgnoreCase(QueryFormatEnum.JSON.getValue())) {

                    String result = results.get("result");
                    log.info("执行自定义函数操作result结果字符长度: " + result.length());
                    Map<String, Object> maps = (Map<String, Object>) JsonMapper.fromJsonString(result, Map.class);
                    List<Object> lists = (List<Object>) maps.get("data");

                    List<Map<String, String>> targetResult = (List<Map<String, String>>) udf.execute(sqlQuerySource, lists);
                    maps.put("data", targetResult);
                    results.put("result", JsonMapper.toJsonString(maps));
                    maps.clear();

                }
            }

        }
        return this;
    }

    private void getAndParserFormatSql() {
        if (StringUtil.isNotEmpty(results)) {
            if (results.get("status").equals(String.valueOf(HttpStatus.SC_OK)) &&
                    param.getFormat().equalsIgnoreCase(QueryFormatEnum.JSON.getValue())) {
                Map<String, String> formatSql = getFormatSql();
                String sql = formatSql.get("result");
                log.info("从离线报表获取formatSql，解析并重制SQLQuerySource对象: " + sql);
                param.setQuery(sql);
                param.setSqlQuerySource(APIServiceImpl.parserSQLByAst(sql));
            }
        }
    }


    @Override
    public BaseResult build() {
        BaseResult baseResult = null;

        if (StringUtil.isEmpty(results)) {
            baseResult =  BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "服务繁忙，请联系平台方！");
        } else {
            if (results.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {

                String result = results.get("result");
                Map<String, Object> maps =(Map<String, Object>) JsonMapper.fromJsonString(result,  Map.class);
                Map<String, Object> statisticMap = (Map<String, Object>) maps.get("statistics");

                Map<String, Object> statistics = Maps.newHashMap();
                Double executeTime = watch.elapsed(TimeUnit.MILLISECONDS)/1000.0;
                statistics.put("elapsed", "stat:" + statisticMap.get("elapsed") + ",result: "+ executeTime);

                statistics.put("rows_read", statisticMap.get("rows_read"));

                int resultSize = StringUtil.isEmpty(maps.get("data")) ? 0 : JsonMapper.toJsonString(maps.get("data")).getBytes().length;

                statistics.put("result_size", resultSize);
                baseResult = BaseResultGenerator.success("ok", maps.get("data"), statistics);

            } else if (results.get("status").equals(String.valueOf(HttpStatus.SC_NOT_FOUND))) {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND, results.get("result"));
            } else if (results.get("status").equals(String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR))) {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, JsonMapper.fromJsonString(results.get("result"), Object.class).toString());
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, JsonMapper.fromJsonString(results.get("result"), Object.class).toString());
            }
        }


        return  baseResult;
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

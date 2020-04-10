package com.mesalab.qgw.dialect;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SQLFunctionUtil;
import com.mesalab.common.utils.SQLHelper;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.DruidIoHttpSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.model.api.udf.UDF;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DruidDialect implements  Dialect {

    private DruidIoHttpSource druidIoHttpSource = (DruidIoHttpSource) SpringContextUtil.getBean("druidIoHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");
    private ApiParam param;

    private int pageOffset;

    private Map<String, String> results;

    private Stopwatch watch = Stopwatch.createUnstarted();

    public DruidDialect(ApiParam param) {

        this.param = param;

        results = Maps.newHashMap();

    }

    @Override
    public boolean supportsLimit() {
        return true;
    }

    @Override
    public String getLimitString(String sql, int offset, int limit) {
        StringBuilder stringBuilder = new StringBuilder(sql);
        stringBuilder.append(" limit ");
        stringBuilder.append(limit);
        return stringBuilder.toString();
    }


    private String generatePageSql(String sql, SQLQuerySource sqlQuerySource) {
        if (StringUtil.isBlank(sqlQuerySource.getLimit())) {
            return getLimitString(sql, 0, 200);
        } else {

            if (sqlQuerySource.getLimit().split(",").length >= 2 ) {
                String[] limits = sqlQuerySource.getLimit().trim().split(",");
                int offset = Integer.valueOf(limits[0].trim());
                int rowNum = Integer.valueOf(limits[1].trim());
                pageOffset = offset;
               return  SQLHelper.removeLimit(sql) + " limit " + (offset + rowNum);
            }
            return sql;
        }

    }

    @Override
    public Dialect sqlDialectTransform() {
        param.setQuery(generatePageSql(param.getQuery(), param.getSqlQuerySource()));
        param.setQuery(SQLFunctionUtil.generateDateFunction(param.getQuery(), DBTypeEnum.DRUID.getValue()));
        return this;
    }

    @Override
    public Dialect executeHttpQuery() {
        watch.start();
        StringBuilder urlBuilder = new StringBuilder("http://") .append(druidIoHttpSource.getUrl());
        DruidDialect.DruidQueryParam druidQueryParam = new DruidDialect.DruidQueryParam();

        druidQueryParam.setQuery(param.getQuery());
        druidQueryParam.getContext().put("sqlTimeZone", druidIoHttpSource.getSqlTimeZone());
        druidQueryParam.getContext().put("skipEmptyBuckets", druidIoHttpSource.getSkipEmptyBuckets());

        log.info("TSDB Query ：{}, SQL: {}", urlBuilder.toString() , param.getQuery());

        if (param.getFormat().equals(QueryFormatEnum.JSON.getValue())) {
            druidQueryParam.setResultFormat("object");
        } else if (param.getFormat().equals(QueryFormatEnum.CSV.getValue())) {
            druidQueryParam.setResultFormat("csv");
        }

        results = httpClientService.httpPost(urlBuilder.toString(), JsonMapper.toJsonString(druidQueryParam));
        executeUDF();
        return this;

    }

    @Override
    public Dialect executeUDF() {
        Set<UDF> udfSet = param.getSqlQuerySource().getUdfSet();
        for (UDF udf : udfSet) {

            if (StringUtil.isNotEmpty(results)) {
                if (results.get("status").equals(String.valueOf(HttpStatus.SC_OK)) &&
                        param.getFormat().equalsIgnoreCase(QueryFormatEnum.JSON.getValue()) ) {

                    List<Object> lists =  (List<Object>)JsonMapper.fromJsonString(results.get("result"), List.class);

                    List<Map<String, String>>  targetResult  =  (List<Map<String, String>>) udf.execute(param.getSqlQuerySource(), lists);

                    results.put("result", JsonMapper.toJsonString(targetResult));

                }
            }

        }

        return this;
    }

    @Override
    public BaseResult build() {

        BaseResult baseResult = null;

        if (StringUtil.isEmpty(results)) {
            baseResult =  BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "服务繁忙，请联系数据平台方！");
        } else {
            if (results.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {

                if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.CSV.getValue())) {
                    String result = results.get("result");

                    String[] results = result.split("\n");

                    if (pageOffset > 0 && StringUtil.isNotEmpty(results)) {
                        if (pageOffset >= results.length) {
                            result = "";
                        } else {
                            String[] subResult = ArrayUtils.subarray(results, pageOffset, results.length);
                            result = Joiner.on("\n").join(subResult).toString();


                        }
                    }


                    baseResult = BaseResultGenerator.success("ok", result , null);


                } else if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.JSON.getValue())) {
                    List<Object> allResults = (List<Object>) JsonMapper.fromJsonString(results.get("result"), Object.class);
                    if (pageOffset > 0 && StringUtil.isNotEmpty(allResults)) {
                        if (pageOffset >= allResults.size()) {
                            allResults = new ArrayList<>();
                        } else {
                            allResults = allResults.subList(pageOffset, allResults.size());
                        }
                    }
                    Double executeTime = watch.elapsed(TimeUnit.MILLISECONDS)/1000.0;
                    Map<String, Object> statistics = Maps.newHashMap();
                    statistics.put("elapsed", String.format("%.2f", executeTime));
                    statistics.put("rows_read", allResults.size());

                    int resultSize = StringUtil.isEmpty(allResults) ? 0 : JsonMapper.toJsonString(allResults).getBytes().length;
                    statistics.put("result_size", resultSize);

                    baseResult = BaseResultGenerator.success("ok", allResults , statistics);
                }



            } else if (results.get("status").equals(String.valueOf(HttpStatus.SC_NOT_FOUND))) {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND,  JsonMapper.fromJsonString(results.get("result"), Object.class).toString());
            } else if (results.get("status").equals(String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR))) {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, JsonMapper.fromJsonString(results.get("result"), Object.class).toString());
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, JsonMapper.fromJsonString(results.get("result"), Object.class).toString());
            }
        }

        return baseResult;
    }


    @Data
    class DruidQueryParam {
        private String query;
        private Map<String, String> context = Maps.newHashMap();
        private String resultFormat;

    }

}

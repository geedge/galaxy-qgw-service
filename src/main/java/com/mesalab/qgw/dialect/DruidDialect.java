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
import com.mesalab.qgw.model.api.EngineConfigSource;
import com.mesalab.qgw.model.api.DruidIoHttpSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DruidDialect extends  AbstractDataSourceDialect {

    private DruidIoHttpSource druidIoHttpSource = (DruidIoHttpSource) SpringContextUtil.getBean("druidIoHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");
    private EngineConfigSource engineConfigSource = (EngineConfigSource) SpringContextUtil.getBean("engineConfigSource");
    private int pageOffset;
    private Stopwatch watch = Stopwatch.createUnstarted();

    public DruidDialect(ApiParam param) {
        initialize(param);
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


    private String generateInnerSqlLimit(SQLQuerySource sqlQuerySource) {
        if (StringUtil.isBlank(sqlQuerySource.getLimit())) {
            return getLimitString(sqlQuerySource.getSqlBody(), 0, engineConfigSource.getMaxCacheNum());
        } else {

            if (sqlQuerySource.getLimit().split(",").length >= 2 ) {
                String[] limits = sqlQuerySource.getLimit().trim().split(",");
                int offset = Integer.valueOf(limits[0].trim());
                int rowNum = Integer.valueOf(limits[1].trim());
                pageOffset = offset;
               return  SQLHelper.removeLimit(sqlQuerySource.getSqlBody()) + " limit " + (offset + rowNum);
            }
            return sqlQuerySource.getSqlBody();
        }

    }



    @Override
    public String convertQuery(String sql) {
        sql = SQLFunctionUtil.generateDateFunction(sql, DBTypeEnum.DRUID.getValue());
        return sql;
    }

    @Override
    public String findBestQuery(String sql) {
        return sql;
    }

    @Override
    public Dialect executeQuery() {

        String query = getExecuteDBSql();

        watch.start();
        StringBuilder urlBuilder = new StringBuilder("http://") .append(druidIoHttpSource.getUrl());
        DruidDialect.DruidQueryParam druidQueryParam = new DruidDialect.DruidQueryParam();
        druidQueryParam.setQuery(query);
        druidQueryParam.getContext().put("sqlTimeZone", druidIoHttpSource.getSqlTimeZone());
        druidQueryParam.getContext().put("skipEmptyBuckets", druidIoHttpSource.getSkipEmptyBuckets());

        log.info("TSDB Query ：{}, SQL: {}", urlBuilder.toString() , param.getDbQuerySource().getSqlBody());

        if (param.getFormat().equals(QueryFormatEnum.JSON.getValue())) {
            druidQueryParam.setResultFormat("object");
        } else if (param.getFormat().equals(QueryFormatEnum.CSV.getValue())) {
            druidQueryParam.setResultFormat("csv");
        }

        Map<String, String> results = httpClientService.httpPost(urlBuilder.toString(), JsonMapper.toJsonString(druidQueryParam));

        if (!results.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, results.get("result"));
        } else {
            if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.JSON.getValue())) {
                baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), null,
                        (List<Object>) JsonMapper.fromJsonString(results.get("result"), Object.class), null, null, param.getFormat());
            } else {
                baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), null, null,
                        results.get("result"), null, param.getFormat());
            }
        }
        return this;

    }



    @Override
    public Dialect executeSyntaxCheck() {
        SQLQuerySource sqlQuerySource = param.getDbQuerySource();
        String sql = convertQuery(sqlQuerySource.getSqlBody());
        param.getDbQuerySource().setSqlBody("select 1");
        executeQuery();
        baseResult.setMessage(sql);
        return this;
    }



    @Override
    public BaseResult build() {

        Object dataObject = baseResult.getData();

        if (baseResult.isSuccess()) {
            if (StringUtil.isBlank(baseResult.getMessage())) {
                baseResult.setMessage("ok");
            }
            if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.CSV.getValue())) {
                String result = String.valueOf(dataObject);

                String[] results = result.split("\n");

                if (pageOffset > 0 && StringUtil.isNotEmpty(results)) {
                    if (pageOffset >= results.length) {
                        result = "";
                    } else {
                        String[] subResult = ArrayUtils.subarray(results, pageOffset, results.length);
                        result = Joiner.on("\n").join(subResult).toString();
                    }
                }
                baseResult.setData(result);
            } else if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.JSON.getValue())) {
                List<Object> allResults = (List<Object>) dataObject;
                if (pageOffset > 0 && StringUtil.isNotEmpty(allResults)) {
                    if (pageOffset >= allResults.size()) {
                        allResults = new ArrayList<>();
                    } else {
                        allResults = allResults.subList(pageOffset, allResults.size());
                    }
                }
                Double executeTime = watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0;
                Map<String, Object> statistics = Maps.newHashMap();
                statistics.put("elapsed", String.format("%.2f", executeTime));
                statistics.put("rows_read", allResults.size());

                int resultSize = StringUtil.isEmpty(allResults) ? 0 : JsonMapper.toJsonString(allResults).getBytes().length;
                statistics.put("result_size", resultSize);
                baseResult.setData(allResults);
                baseResult.setStatistics(statistics);
            }
        } else if (baseResult.getStatus().equals(HttpStatus.SC_NOT_FOUND)) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND, dataObject.toString());
        } else if (baseResult.getStatus().equals(HttpStatus.SC_INTERNAL_SERVER_ERROR)) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, dataObject.toString());
        } else {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, dataObject.toString());
        }
        return baseResult;
    }


    /**
     * 设置sqlBody最大查询条数limit限制
     */
    private void generateSetMaxLimit() {
        SQLQuerySource innerSqlQuerySource = param.getDbQuerySource();
        while (StringUtil.isNotEmpty(innerSqlQuerySource.getSubSelect())) {
            innerSqlQuerySource = innerSqlQuerySource.getSubSqlQuerySources().get(0);
        }
        String sql = generateInnerSqlLimit(innerSqlQuerySource);

        SQLQuerySource sqlQuerySource = param.getDbQuerySource();
        while (StringUtil.isNotEmpty(sqlQuerySource)) {
            sqlQuerySource.setSqlBody(sqlQuerySource.getSqlBody().replace(innerSqlQuerySource.getSqlBody(), sql));
            if (sqlQuerySource.getSubSqlQuerySources().isEmpty()) {
                break;
            }
            sqlQuerySource = sqlQuerySource.getSubSqlQuerySources().get(0);
        }
    }


    /**
     * 获取在DB中实际执行的sql
     *
     * @return
     */
    private String getExecuteDBSql() {
        generateSetMaxLimit();
        SQLQuerySource sqlQuerySource = param.getDbQuerySource();
        String sql = sqlQuerySource.getSqlBody();
        sql = findBestQuery(sql);
        sql = convertQuery(sql);
        return sql;
    }





    @Data
    class DruidQueryParam {
        private String query;
        private Map<String, String> context = Maps.newHashMap();
        private String resultFormat;

    }

}

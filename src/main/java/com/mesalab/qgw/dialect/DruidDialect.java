package com.mesalab.qgw.dialect;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.mesalab.calcite.storage.DataTypeMapping;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.enums.ResultCodeEnum;
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
import org.apache.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashMap;
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
    public String convertQuery(String sql) {
        return new Converter(sql).generateStandard().generateDateFunction().build();

    }

    @Override
    public String findBestQuery() {

        return new Optimizer().generateQueryLimit().generateSQL().build();

    }

    @Override
    public Dialect executeQuery() {

        String sql = convertQuery(findBestQuery());

        watch.start();
        Map<String, String> results = executeHttpPost(sql, QueryFormatEnum.JSON.getValue());

        if (results.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), ResultCodeEnum.EXECUTE_SUCCESS.getCode(),  null,
                    (List<Object>) JsonMapper.fromJsonString(results.get("result"), Object.class), null, null, QueryFormatEnum.JSON.getValue());
        } else {
            baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(), null,
                    results.get("result"), null, null, param.getFormat());
        }
        return this;

    }

    @Override
    public Dialect executeAdministrativeQuery() {
        Map<String, String> results = executeHttpPost(param.getQuery(), QueryFormatEnum.JSON.getValue());

        if (results.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), ResultCodeEnum.EXECUTE_SUCCESS.getCode(),  null,
                    (List<Object>) JsonMapper.fromJsonString(results.get("result"), Object.class), null, null, QueryFormatEnum.JSON.getValue());
        } else {
            baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(), null,
                    results.get("result"), null, null, param.getFormat());
        }
        return this;
    }


    /**
     * Druid Http Query Interface
     * @param sql SQL语句
     * @param format 结果显示格式,JSON OR CSV
     * @return
     */
    private Map<String, String> executeHttpPost(String sql, String format) {
        StringBuilder urlBuilder = new StringBuilder("http://") .append(druidIoHttpSource.getUrl());
        DruidQueryParam druidQueryParam = new DruidQueryParam();
        druidQueryParam.setQuery(sql);
        druidQueryParam.getContext().put("sqlTimeZone", druidIoHttpSource.getSqlTimeZone());
        druidQueryParam.getContext().put("skipEmptyBuckets", druidIoHttpSource.getSkipEmptyBuckets());

        log.info("DB Engine is :{}, Execute Query is: {}", DBTypeEnum.DRUID.getValue(), JsonMapper.toJsonString(druidQueryParam));

        if (format.equals(QueryFormatEnum.JSON.getValue())) {
            druidQueryParam.setResultFormat("object");
        } else if (format.equals(QueryFormatEnum.CSV.getValue())) {
            druidQueryParam.setResultFormat("csv");
        }

        return httpClientService.httpPost(urlBuilder.toString(), JsonMapper.toJsonString(druidQueryParam));
    }


    @Override
    public Dialect executeSyntaxCheck() {
        String query = convertQuery(param.getDbQuerySource().getSqlBody());
        Map<String, String> results = executeHttpPost("select 1", param.getFormat());

        baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")),null, query,
                (List<Object>) JsonMapper.fromJsonString(results.get("result"), Object.class), null, null, param.getFormat());
        log.info("option=syntax-check:  original sql: {}, after transformation(return message) info: {}", param.getQuery(), query);
        return this;
    }



    @Override
    public BaseResult build() {

        Object dataObject = baseResult.getData();

        if (baseResult.isSuccess()) {
            if (StringUtil.isBlank(baseResult.getMessage())) {
                baseResult.setMessage("ok");
            }
            List<Object> allResults = (List<Object>) dataObject;
            if (pageOffset > 0 && StringUtil.isNotEmpty(allResults)) {
                if (pageOffset >= allResults.size()) {
                    allResults = new ArrayList<>();
                } else {
                    allResults = allResults.subList(pageOffset, allResults.size());
                }
            }
            Double executeTime = watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0;
            Map<String, Object> statistics = Maps.newLinkedHashMap();
            statistics.put("elapsed", String.format("%.2f", executeTime));
            statistics.put("rows_read", allResults.size());
            baseResult.setData(allResults);
            baseResult.setMeta(getMeta(allResults));
            baseResult.setStatistics(statistics);
        } else if (baseResult.getStatus().equals(HttpStatus.SC_NOT_FOUND)) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND, ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(),
                    StringUtil.isEmpty(dataObject) ? "System Runtime Exception,Please contact Galaxy Data Platform Project manager. " :String.valueOf(dataObject));
        } else if (baseResult.getStatus().equals(HttpStatus.SC_INTERNAL_SERVER_ERROR)) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(),
                    StringUtil.isEmpty(dataObject) ? "System Runtime Exception,Please contact Galaxy Data Platform Project manager. " :String.valueOf(dataObject));
        } else {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(),
                    StringUtil.isEmpty(dataObject) ? "System Runtime Exception,Please contact Galaxy Data Platform Project manager. " :String.valueOf(dataObject));
        }
        return baseResult;
    }

    private List getMeta(List<Object> results) {
        List meta = new ArrayList<Map<String, String>>();
        if (results.size() > 0) {
            Map<String, Object> map = (Map) results.get(0);
            for (String key : map.keySet()) {
                Object value = map.get(key);
                Map<String, String> temp = new HashMap<>();
                temp.put("name", key);
                if (value instanceof Integer) {
                    temp.put("type", DataTypeMapping.BIGINT);
                } else if (value instanceof Double) {
                    temp.put("type", DataTypeMapping.DOUBLE);
                } else {
                    temp.put("type", DataTypeMapping.VARCHAR);
                }
                meta.add(temp);
            }
        }
        return meta;
    }

    class Converter {
        String sql = null;

        public Converter(String sql) {
            this.sql = sql;
        }

        /**
         * SQL转换为DRUID语法格式
         * 1. 不支持limit offset,rows 转为 limit rows
         * @return
         */
        public Converter generateStandard(){
            SQLQuerySource dbQuerySource = param.getDbQuerySource();
            if (StringUtil.isNotBlank(dbQuerySource.getLimit()) &&
                    dbQuerySource.getLimit().split(",").length >= 2 ) {

                String[] limits = dbQuerySource.getLimit().trim().split(",");
                int offset = Integer.valueOf(limits[0].trim());
                int rowNum = Integer.valueOf(limits[1].trim());
                pageOffset = offset;
                sql = SQLHelper.getLimitString(dbQuerySource.getSqlBody(),0, (offset + rowNum));
            }
            return this;
        }


        public Converter generateDateFunction() {
            sql = SQLFunctionUtil.generateDateFunction(sql, DBTypeEnum.DRUID.getValue());
            return this;
        }




        public String build() {
            return sql;
        }
    }


    class Optimizer {
        String sql = null;
        /**
         * 对SQL最内层的结果数据集进行限制
         * 参见查询网关系统参数：engine.maxCacheNum
         *
         * @return
         */
        public Optimizer generateQueryLimit() {

            setDefaultResultRows(StringUtil.isEmpty(param.getEngineQuerySource()) ? param.getDbQuerySource() : param.getEngineQuerySource(), engineConfigSource.getDefaultResultNum());

            setMaxCacheResultRows(param.getDbQuerySource(), engineConfigSource.getMaxCacheNum());


            return this;
        }


        private  void setDefaultResultRows(SQLQuerySource sqlQuerySource, int defaultResultRows) {
            if (StringUtil.isBlank(sqlQuerySource.getLimit())) {
                sqlQuerySource.setSqlBody(sqlQuerySource.getSqlBody() + " limit " + defaultResultRows);
                sqlQuerySource.setLimit(String.valueOf(defaultResultRows));
            }

        }

        /**
         * 设置最大缓存数据集
         * @param sqlQuerySource
         * @return
         */
        private void setMaxCacheResultRows(SQLQuerySource sqlQuerySource, int maxCacheResultRows) {
            SQLQuerySource innerSqlQuerySource = sqlQuerySource;
            while (StringUtil.isNotEmpty(innerSqlQuerySource.getSubSelect())) {
                innerSqlQuerySource = innerSqlQuerySource.getSubSqlQuerySources().get(0);
            }

            if (!innerSqlQuerySource.isEnableLimit()) {
                return;
            }

            String orginInnerSql = innerSqlQuerySource.getSqlBody();

            String limitInnerSql = innerSqlQuerySource.getSqlBody();
            if (StringUtil.isNotBlank(innerSqlQuerySource.getLimit())) {

                String[] limits = innerSqlQuerySource.getLimit().split(",");

                if (Integer.valueOf(limits[limits.length-1]) > maxCacheResultRows) {
                    limits[limits.length-1] = String.valueOf(maxCacheResultRows);

                    if (limits.length == 2 ) {
                        limitInnerSql = SQLHelper.getLimitString(limitInnerSql, Integer.valueOf(limits[0]), Integer.valueOf(limits[1]));
                    } else {
                        limitInnerSql = SQLHelper.getLimitString(limitInnerSql,0, Integer.valueOf(limits[0]));
                    }

                    innerSqlQuerySource.setLimit(String.join(",", limits).toString());
                    innerSqlQuerySource.setSqlBody(limitInnerSql);

                }

            } else {
                limitInnerSql = innerSqlQuerySource.getSqlBody() + " limit " + maxCacheResultRows;
                innerSqlQuerySource.setLimit(String.valueOf(maxCacheResultRows));
                innerSqlQuerySource.setSqlBody(limitInnerSql);

            }

            if (StringUtil.isNotEmpty(sqlQuerySource.getSubSqlQuerySources())) {
                sqlQuerySource.setSqlBody(sqlQuerySource.getSqlBody().replace(orginInnerSql, limitInnerSql));
            }

        }

        public Optimizer generateSQL() {
            SQLQuerySource sqlQuerySource = param.getDbQuerySource();
            sql = sqlQuerySource.getSqlBody();
            return this;
        }

        public String build() {
            return sql;
        }


    }




    @Data
    class DruidQueryParam {
        private String query;
        private Map<String, String> context = Maps.newHashMap();
        private String resultFormat;

    }

}

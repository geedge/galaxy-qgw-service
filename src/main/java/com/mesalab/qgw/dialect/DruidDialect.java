package com.mesalab.qgw.dialect;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.mesalab.common.utils.sqlparser.DruidSampleSQLHelper;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.enums.*;
import com.mesalab.common.utils.*;
import com.mesalab.common.utils.sqlparser.SQLFunctionUtil;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.model.basic.QueryContext;
import com.geedgenetworks.utils.StringUtil;
import com.mesalab.qgw.service.impl.HttpClientServiceV2;
import lombok.Data;
import net.sf.jsqlparser.JSQLParserException;
import org.apache.commons.compress.utils.Lists;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class DruidDialect extends AbstractDataSourceDialect {
    private static final Log log = LogFactory.get();
    private DruidIoHttpSource druidIoHttpSource = (DruidIoHttpSource) SpringContextUtil.getBean("druidIoHttpSource");
    private HttpClientServiceV2 httpClientService = (HttpClientServiceV2) SpringContextUtil.getBean("httpClientServiceV2");
    private EngineConfigSource engineConfigSource = (EngineConfigSource) SpringContextUtil.getBean("engineConfigSource");
    private HttpConfig httpConfig = (HttpConfig) SpringContextUtil.getBean("httpConfig");
    private int pageOffset;
    private static final String CLAZZ_NAME_CALCITE_EXCEPTION = "com.zdjizhi.calcite.runtime.CalciteContextException: ";
    private Stopwatch watch = Stopwatch.createUnstarted();
    public QueryContext queryContext;
    public BaseResult baseResult;

    public DruidDialect(QueryContext queryContext) {
        this.queryContext = queryContext;
    }

    @Override
    void init() {
        if (StringUtil.isNotEmpty(queryContext.getDbQuerySource())) {
            queryContext.getDbQuerySource().setExecSQL(queryContext.getDbQuerySource().getSqlBody());
        } else {
            SQLQuerySource sqlQuerySource = new SQLQuerySource();
            sqlQuerySource.setExecSQL(queryContext.getQuery());
            queryContext.setDbQuerySource(sqlQuerySource);
        }
        baseResult = BaseResultGenerator.success();
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
    public String getBestQuery() {

        return new Optimizer().generateQueryLimit().generateSQL().build();

    }

    @Override
    public String getOriginalQuery() {
        return queryContext.getQuery();
    }

    /**
     * 获取语法检测sql
     *
     * @return
     */
    @Override
    public String getSampleQuery() {
        String sampleSQL = queryContext.getDbQuerySource().getSqlBody();
        try {
            sampleSQL = convertQuery(convertSampleSql(sampleSQL));
        } catch (JSQLParserException e) {
            log.error("syntax-check sql error {}, execute original sql: {}, error is: {} ",
                    sampleSQL, sampleSQL = queryContext.getDbQuerySource().getSqlBody(), e.getMessage() == null ? e.getCause() : e.getMessage());
        }
        return sampleSQL;
    }

    @Override
    String getExplainQuery() {
        return "EXPLAIN PLAN FOR " + convertQuery(getBestQuery());
    }

    @Override
    public BaseResult executeKillQuery(String queryId) {
        String url = druidIoHttpSource.getUrl();
        url = url.substring(0, url.indexOf("/"));
        String deleteUrl = URLUtil.normalize(url).concat("/druid/v2/").concat(queryId);
        HttpResponseResult httpResponseResult = httpClientService.delete(deleteUrl);
        if (httpResponseResult.getStatusCode() == HttpStatusCodeEnum.SUCCESS.getCode()
                || httpResponseResult.getStatusCode() == HttpStatusCodeEnum.ACCEPTED.getCode()) {
            return BaseResultGenerator.generate(HttpStatusCodeEnum.ACCEPTED.getCode(), null, "ok", null, null, null, null);
        } else {
            return BaseResultGenerator.failure(httpResponseResult.getStatusCode(), httpResponseResult.getErrorMessage());
        }
    }

    @Override
    public BaseResult getProcesses(String queryId) {
        return BaseResultGenerator.success4Message("ok");
    }

    /**
     * Druid Http Query Interface
     *
     * @param sql SQL语句
     * @return
     */
    private HttpResponseResult executeHttpPost(String sql) {
        String queryURL = URLUtil.normalize(druidIoHttpSource.getUrl());
        DruidQueryParam druidQueryParam = enrichContextAndBuildQueryParameter(sql);
        log.info("Query ID: {}, SQL: {}", queryContext.getQueryId(), sql);
        if (log.isDebugEnabled()) {
            log.debug("DB engine is: {}, : {}", DBEngineType.DRUID.getValue(), JSON.toJSONString(druidQueryParam)
                     + ". body sql is:" + sql);
        }
        return httpClientService.post(queryURL, queryContext.getTimeout(), JSON.toJSONString(druidQueryParam));
    }

    private DruidQueryParam enrichContextAndBuildQueryParameter(String sql) {
        queryContext.setTimeout(httpConfig.getDruidSocketTimeOut());
        if (StringUtil.isBlank(queryContext.getQueryId())) {
            queryContext.setQueryId(DigestUtil.md5Hex(StringUtil.createUUID()));
        }
        DruidQueryParam druidQueryParam = new DruidQueryParam();
        druidQueryParam.setQuery(sql);
        druidQueryParam.getContext().put("skipEmptyBuckets", druidIoHttpSource.getSkipEmptyBuckets());
        druidQueryParam.getContext().put("sqlQueryId", queryContext.getQueryId());
        druidQueryParam.setResultFormat("object");
        return druidQueryParam;
    }

    @Override
    public BaseResult generateBaseResult(String sql, Optional<String> message) {
        watch.start();

        HttpResponseResult responseResult = executeHttpPost(sql);

        if (responseResult.getStatusCode() == HttpStatusCodeEnum.SUCCESS.getCode()) {
            baseResult = BaseResultGenerator.generate(responseResult.getStatusCode(), ResultCodeEnum.SUCCESS.getCode(), message.get(),
                    (List<Object>) JSON.parseObject(responseResult.getResponseBody(), Object.class), null, null, OutputMode.JSON.getValue());
        } else {
            baseResult = BaseResultGenerator.generate(responseResult.getStatusCode(),
                    CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), message.get()),
                    responseResult.getErrorMessage(), null, null, queryContext.getFormat());
        }
        return build();
    }


    private BaseResult build() {

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
            Map<String, Object> statistics = Maps.newLinkedHashMap();
            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            statistics.put("rows_read", allResults.size());
            baseResult.setData(allResults);
            baseResult.setMeta(unifyMeta(allResults));
            baseResult.setStatistics(statistics);
        } else {
            if (baseResult.getStatus() >= HttpStatusCodeEnum.BAD_REQUEST.getCode() && baseResult.getStatus() < HttpStatusCodeEnum.SERVER_ERROR.getCode()) {
                throw new QGWBusinessException(baseResult.getStatus(), CommonErrorCode.BAD_REQUEST_SQL_EXECUTION_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.BAD_REQUEST_SQL_EXECUTION_EXCEPTION.getMessage(), simplyErrorMessage(dataObject)));
            }
            throw new QGWBusinessException(baseResult.getStatus(), CommonErrorCode.SQL_EXECUTION_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.SQL_EXECUTION_EXCEPTION.getMessage(), simplyErrorMessage(dataObject)));
        }
        return baseResult;
    }

    @Override
    public String simplyErrorMessage(Object dataObject) {
        String message = String.valueOf(dataObject);
        try {
            message = StringUtil.isNotEmpty(dataObject) ? JsonPath.read(dataObject.toString(), "$.errorMessage") : "System Runtime Exception, Please contact Galaxy Data Platform Project manager";
            message = StringUtil.isEmpty(message) ? dataObject.toString() : message;
        } catch (JsonPathException e) {
            log.error("Build error druid-errorMessage, data is: {}", dataObject);
        }
        message = message.startsWith(CLAZZ_NAME_CALCITE_EXCEPTION) ? message.substring(CLAZZ_NAME_CALCITE_EXCEPTION.length()) : message;
        return StrUtil.removeAllLineBreaks(message).trim();
    }


    /**
     * 做时间范围条件过滤: 去除原有最内层条件, 重新添加时间范围
     *
     * @param sql
     * @return
     * @throws JSQLParserException
     */
    private String convertSampleSql(String sql) throws JSQLParserException {
        String filterExpr = String.format("%s >= CURRENT_TIMESTAMP - INTERVAL %s AND %s < CURRENT_TIMESTAMP",
                queryContext.getDbQuerySource().getPartitionKey(), "'1' MINUTE", queryContext.getDbQuerySource().getPartitionKey());
        return new DruidSampleSQLHelper(sql, filterExpr, true).build();
    }

    private List<Map<String, String>> unifyMeta(List<Object> list) {
        List<Map<String, String>> metaList = Lists.newArrayList();
        if (StringUtil.isEmpty(list)) {
            return metaList;
        }
        Map<String, Object> data = JsonPath.read(list.get(0), "$");
        for (String key : data.keySet()) {
            Map<String, String> temp = Maps.newHashMap();
            temp.put("name", key);
            Object value = data.get(key);
            if (value instanceof Integer) {
                temp.put("type", DataTypeMapping.INT);
            } else if (value instanceof Long) {
                temp.put("type", DataTypeMapping.LONG);
            } else if (value instanceof Float) {
                temp.put("type", DataTypeMapping.FLOAT);
            } else if (value instanceof Double) {
                temp.put("type", DataTypeMapping.DOUBLE);
            } else {
                temp.put("type", DataTypeMapping.STRING);
            }
            metaList.add(temp);
        }
        return metaList;
    }

    class Converter {
        String sql = null;

        public Converter(String sql) {
            this.sql = sql;
        }

        /**
         * SQL转换为DRUID语法格式
         * 1. 不支持limit offset,rows 转为 limit rows
         *
         * @return
         */
        public Converter generateStandard() {
            SQLQuerySource dbQuerySource = queryContext.getDbQuerySource();
            if (StringUtil.isNotBlank(dbQuerySource.getLimit()) &&
                    dbQuerySource.getLimit().split(",").length >= 2) {

                String[] limits = dbQuerySource.getLimit().trim().split(",");
                int offset = Integer.valueOf(limits[0].trim());
                int rowNum = Integer.valueOf(limits[1].trim());
                pageOffset = offset;
                sql = SQLHelper.getLimitString(dbQuerySource.getSqlBody(), 0, (offset + rowNum));
            }
            return this;
        }


        public Converter generateDateFunction() {
            sql = SQLFunctionUtil.generateDateFunction(sql, DBEngineType.DRUID.getValue());
            return this;
        }


        public String build() {
            return sql;
        }
    }


    class Optimizer {
        String sql = null;

        /**
         * 对SQL执行结果数据量进行限制
         * 参见查询网关系统参数：engine.maxCacheNum
         *
         * @return
         */
        public Optimizer generateQueryLimit() {
            if (StringUtil.isEmpty(queryContext.getEngineQuerySource())) {
                setDefaultResultRows(queryContext.getDbQuerySource(), engineConfigSource.getDefaultResultNum());
                setMaxCacheResultRows(queryContext.getDbQuerySource(), engineConfigSource.getMaxCacheNum());
            } else {
                setDefaultResultRows(queryContext.getEngineQuerySource(), engineConfigSource.getDefaultResultNum());
                setMaxCacheResultRows(queryContext.getDbQuerySource(), engineConfigSource.getMaxCacheNum());
                setMaxCacheResultRows(queryContext.getEngineQuerySource(), engineConfigSource.getMaxCacheNum());
            }
            return this;
        }


        private void setDefaultResultRows(SQLQuerySource sqlQuerySource, int defaultResultRows) {
            if (StringUtil.isBlank(sqlQuerySource.getLimit())) {
                sqlQuerySource.setSqlBody(sqlQuerySource.getSqlBody() + " limit " + defaultResultRows);
                sqlQuerySource.setLimit(String.valueOf(defaultResultRows));
            }

        }

        /**
         * 设置最大缓存数据集
         *
         * @param sqlQuerySource
         * @return
         */
        private void setMaxCacheResultRows(SQLQuerySource sqlQuerySource, int maxCacheResultRows) {
            if (!sqlQuerySource.isEnableLimit()) {
                return;
            }
            String sqlBody = sqlQuerySource.getSqlBody();
            if (StringUtil.isNotBlank(sqlQuerySource.getLimit())) {
                String[] limits = sqlQuerySource.getLimit().split(",");
                if (Integer.valueOf(limits[limits.length - 1]) > maxCacheResultRows) {
                    limits[limits.length - 1] = String.valueOf(maxCacheResultRows);
                    if (limits.length == 2) {
                        sqlBody = SQLHelper.getLimitString(sqlBody, Integer.valueOf(limits[0]), Integer.valueOf(limits[1]));
                    } else {
                        sqlBody = SQLHelper.getLimitString(sqlBody, 0, Integer.valueOf(limits[0]));
                    }
                    sqlQuerySource.setLimit(String.join(",", limits).toString());
                    sqlQuerySource.setSqlBody(sqlBody);
                }
            } else {
                sqlBody = sqlQuerySource.getSqlBody() + " limit " + maxCacheResultRows;
                sqlQuerySource.setLimit(String.valueOf(maxCacheResultRows));
                sqlQuerySource.setSqlBody(sqlBody);
            }
        }

        public Optimizer generateSQL() {
            SQLQuerySource sqlQuerySource = queryContext.getDbQuerySource();
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

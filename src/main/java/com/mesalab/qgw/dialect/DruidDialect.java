package com.mesalab.qgw.dialect;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
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
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.qgw.model.api.HttpConfig;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.DruidIoHttpSource;
import com.mesalab.qgw.model.api.EngineConfigSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import net.sf.jsqlparser.JSQLParserException;
import org.apache.commons.compress.utils.Lists;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class DruidDialect extends AbstractDataSourceDialect {
    private static final Log log = LogFactory.get();
    private DruidIoHttpSource druidIoHttpSource = (DruidIoHttpSource) SpringContextUtil.getBean("druidIoHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");
    private EngineConfigSource engineConfigSource = (EngineConfigSource) SpringContextUtil.getBean("engineConfigSource");
    private HttpConfig httpConfig = (HttpConfig) SpringContextUtil.getBean("httpConfig");
    private int pageOffset;
    private static final String CLAZZ_NAME_CALCITE_EXCEPTION = "com.zdjizhi.calcite.runtime.CalciteContextException: ";
    private Stopwatch watch = Stopwatch.createUnstarted();
    public ApiParam param;
    public BaseResult baseResult;

    public DruidDialect(ApiParam param) {
        this.param = param;
    }

    @Override
    void init() {
        if (StringUtil.isNotEmpty(param.getDbQuerySource())) {
            param.setSql(param.getDbQuerySource().getSqlBody());
        } else {
            param.setSql(param.getQuery());
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
        return param.getQuery();
    }

    /**
     * 获取语法检测sql
     *
     * @return
     */
    @Override
    public String getSampleQuery() {
        String sampleSQL = param.getDbQuerySource().getSqlBody();
        try {
            sampleSQL = convertQuery(convertSampleSql(sampleSQL));
        } catch (JSQLParserException e) {
            log.error("syntax-check sql error {}, execute original sql: {}, error is: {} ",
                    sampleSQL, sampleSQL = param.getDbQuerySource().getSqlBody(), e.getMessage() == null ? e.getCause() : e.getMessage());
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
        Map<String, String> map = httpClientService.httpDelete(deleteUrl, 1000);
        if (Integer.parseInt(map.get("status")) == ResultStatusEnum.SUCCESS.getCode()
                || Integer.parseInt(map.get("status")) == ResultStatusEnum.ACCEPTED.getCode()) {
            return BaseResultGenerator.generate(ResultStatusEnum.ACCEPTED.getCode(), null, "ok", null, null, null, null);
        } else {
            return BaseResultGenerator.failure(Integer.parseInt(map.get("status")), map.get("result"));
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
    private Map<String, String> executeHttpPost(String sql) {
        String queryURL = URLUtil.normalize(druidIoHttpSource.getUrl());
        DruidQueryParam druidQueryParam = getDruidQueryParam(sql);
        log.info("DB engine is :{},execute query is: {}", DBTypeEnum.DRUID.getValue(), JsonMapper.toJsonString(druidQueryParam));
        int socketTimeOut = httpConfig.getDruidSocketTimeOut();
        return httpClientService.httpPost(queryURL, JsonMapper.toJsonString(druidQueryParam), socketTimeOut);
    }

    private DruidQueryParam getDruidQueryParam(String sql) {
        DruidQueryParam druidQueryParam = new DruidQueryParam();
        druidQueryParam.setQuery(sql);
        druidQueryParam.getContext().put("skipEmptyBuckets", druidIoHttpSource.getSkipEmptyBuckets());
        druidQueryParam.getContext().put("sqlQueryId", StringUtil.isNotBlank(param.getQueryId()) ? param.getQueryId() : DigestUtil.md5Hex(StringUtil.createUUID()));
        druidQueryParam.setResultFormat("object");
        return druidQueryParam;
    }

    @Override
    public BaseResult generateBaseResult(String sql, Optional<String> message) {
        watch.start();

        Map<String, String> results = executeHttpPost(sql);

        int status = Integer.parseInt(results.get("status"));
        if (status == ResultStatusEnum.SUCCESS.getCode()) {
            baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), ResultCodeEnum.SUCCESS.getCode(), message.get(),
                    (List<Object>) JsonMapper.fromJsonString(results.get("result"), Object.class), null, null, QueryFormatEnum.JSON.getValue());
        } else {
            baseResult = BaseResultGenerator.generate(Integer.parseInt(results.get("status")),

                    ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.format(ResultCodeEnum.UNKNOWN_EXCEPTION.getMessage(), message.get()),
                    StringUtil.isNotEmpty(results.get("message")) ? results.get("message") : results.get("result"), null, null, param.getFormat());
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
            if (baseResult.getStatus() >= ResultStatusEnum.BAD_REQUEST.getCode() && baseResult.getStatus() < ResultStatusEnum.SERVER_ERROR.getCode()) {
                throw new QGWBusinessException(baseResult.getStatus(), QGWErrorCode.SQL_EXECUTION_BAD_REQUEST_EXCEPTION.getCode(),
                        String.format(QGWErrorCode.SQL_EXECUTION_BAD_REQUEST_EXCEPTION.getMessage(), simplyErrorMessage(dataObject)));
            }
            throw new QGWBusinessException(baseResult.getStatus(), QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getMessage(), simplyErrorMessage(dataObject)));
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
        String filterExpr = String.format("%s >= CURRENT_TIMESTAMP - INTERVAL '%s' HOUR ",
                param.getDbQuerySource().getPartitionKey(), BooleanUtil.isTrue(param.getSampled()) ? 12 : 0);
        return new DruidSampleSQLHelper(sql, filterExpr).build();
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
            SQLQuerySource dbQuerySource = param.getDbQuerySource();
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

                if (Integer.valueOf(limits[limits.length - 1]) > maxCacheResultRows) {
                    limits[limits.length - 1] = String.valueOf(maxCacheResultRows);

                    if (limits.length == 2) {
                        limitInnerSql = SQLHelper.getLimitString(limitInnerSql, Integer.valueOf(limits[0]), Integer.valueOf(limits[1]));
                    } else {
                        limitInnerSql = SQLHelper.getLimitString(limitInnerSql, 0, Integer.valueOf(limits[0]));
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

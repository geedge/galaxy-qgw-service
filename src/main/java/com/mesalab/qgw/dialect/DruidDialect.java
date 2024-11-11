package com.mesalab.qgw.dialect;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONPath;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.mesalab.common.entity.SchemaBase;
import com.mesalab.common.utils.sqlparser.*;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.enums.*;
import com.mesalab.common.utils.*;
import com.mesalab.qgw.constant.MetaConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.model.basic.SQLQueryContext;
import com.geedgenetworks.utils.StringUtil;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.qgw.service.impl.HttpClientServiceV2;
import lombok.Data;
import lombok.SneakyThrows;
import net.sf.jsqlparser.JSQLParserException;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;


public class DruidDialect extends AbstractDataSourceDialect {
    private static final Log log = LogFactory.get();
    public static final Pattern pAggregateFunSpecificOfDruid = Pattern.compile("\\b(QUANTILE_HDR|HDR_HISTOGRAM|HLLD|APPROX_COUNT_DISTINCT_HLLD|PERCENTILES_HDR)\\s*\\(", Pattern.CASE_INSENSITIVE);
    private DruidIoHttpSource druidIoHttpSource = (DruidIoHttpSource) SpringContextUtil.getBean("druidIoHttpSource");
    private HttpClientServiceV2 httpClientService = (HttpClientServiceV2) SpringContextUtil.getBean("httpClientServiceV2");
    private EngineConfigSource engineConfigSource = (EngineConfigSource) SpringContextUtil.getBean("engineConfigSource");
    private HttpConfig httpConfig = (HttpConfig) SpringContextUtil.getBean("httpConfig");
    private DatabaseService databaseService = (DatabaseService) SpringContextUtil.getBean("databaseService");
    private int pageOffset;
    private static final String CLAZZ_NAME_CALCITE_EXCEPTION = "com.zdjizhi.calcite.runtime.CalciteContextException: ";
    private Stopwatch watch = Stopwatch.createUnstarted();

    public BaseResult baseResult;

    private final static Map<String, String> metaMap = Maps.newHashMap();

    static {
        metaMap.put("CHAR", DataTypeMapping.STRING);
        metaMap.put("VARCHAR", DataTypeMapping.STRING);
        metaMap.put("FLOAT", DataTypeMapping.FLOAT);
        metaMap.put("DECIMAL", DataTypeMapping.DOUBLE);
        metaMap.put("REAL", DataTypeMapping.DOUBLE);
        metaMap.put("DOUBLE", DataTypeMapping.DOUBLE);
        metaMap.put("BOOLEAN", DataTypeMapping.BOOLEAN);
        metaMap.put("TINYINT", DataTypeMapping.INT);
        metaMap.put("SMALLINT", DataTypeMapping.INT);
        metaMap.put("INTEGER", DataTypeMapping.INT);
        metaMap.put("BIGINT", DataTypeMapping.LONG);
        metaMap.put("TIMESTAMP", DataTypeMapping.DATE);
        metaMap.put("DATE", DataTypeMapping.DATE);

        metaMap.put("ARRAY", null);
        metaMap.put("OTHER", null);
    }

    public DruidDialect(SQLQueryContext queryContext) {
        super(queryContext);
    }

    @Override
    void init() {
        if (StringUtil.isNotEmpty(sqlQueryContext.getDbSelectStatement())) {
            sqlQueryContext.getDbSelectStatement().setExecSQL(sqlQueryContext.getDbSelectStatement().getSqlBody());
        } else {
            SelectStatement sqlQuerySource = new SelectStatement();
            sqlQuerySource.setExecSQL(sqlQueryContext.getOriginalSQL());
            sqlQueryContext.setDbSelectStatement(sqlQuerySource);
        }
        baseResult = BaseResultGenerator.success();
    }


    @Override
    public boolean supportsLimit() {
        return true;
    }


    @Override
    public String convertQuery(String sql) {
        return new Converter(sql).generateStandard().mergeFunctions().generateDateFunction().build();

    }

    @Override
    public String generateRuleBaseOptimizedQuery() {

        return new Optimizer().generateQueryLimit().generateSQL().build();

    }

    /**
     * 获取语法检测sql
     *
     * @return
     */
    @SneakyThrows
    @Override
    public String getSampleQuery() {
        String sampleSQL = sqlQueryContext.getDbSelectStatement().getSqlBody();
        try {
            sampleSQL = convertQuery(convertSampleSql(sampleSQL));
        } catch (JSQLParserException e) {
            log.error("syntax-check sql error {}, execute original sql: {}, error is: {} ",
                    sampleSQL, sampleSQL = sqlQueryContext.getDbSelectStatement().getSqlBody(), e.getMessage() == null ? e.getCause() : e.getMessage());
        }
        return sampleSQL;
    }

    @Override
    String getDescQuery(String table) {
        return "SELECT COLUMN_NAME as name, DATA_TYPE as type FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + table + "'";
    }

    @Override
    String getExplainQuery() {
        return "EXPLAIN PLAN FOR " + convertQuery(generateRuleBaseOptimizedQuery());
    }

    @Override
    public BaseResult executeKillQuery(String queryId) {
        String url = druidIoHttpSource.getUrl();
        url = url.substring(0, url.indexOf("/"));
        String deleteUrl = URLUtil.normalize(url).concat("/druid/v2/").concat(queryId);
        HttpResponseResult httpResponseResult = httpClientService.delete(deleteUrl);
        if (httpResponseResult.getStatusCode() == HttpStatusCodeEnum.SUCCESS.getCode()
                || httpResponseResult.getStatusCode() == HttpStatusCodeEnum.ACCEPTED.getCode()) {
            return BaseResultGenerator.generate(HttpStatusCodeEnum.ACCEPTED.getCode(), null, true, "ok", null, null, null, null);
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
        log.info("Query ID: {}, SQL: {}", sqlQueryContext.getQueryId(), sql);
        if (log.isDebugEnabled()) {
            log.debug("DB engine is: {}, : {}", DBEngineType.DRUID.getValue(), JSON.toJSONString(druidQueryParam)
                    + ". body sql is:" + sql);
        }
        long start = System.currentTimeMillis();
        HttpResponseResult responseResult = httpClientService.post(queryURL, sqlQueryContext.getTimeout(), JSON.toJSONString(druidQueryParam));
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > engineConfigSource.getHighLatencyThreshold()) {
            log.warn("DB engine is: {}, queryId: {}, elapsed: {} ms, sql: {}", DBEngineType.DRUID.getValue(), sqlQueryContext.getQueryId(), elapsed, sql);
        }
        return responseResult;
    }

    private DruidQueryParam enrichContextAndBuildQueryParameter(String sql) {
        sqlQueryContext.setTimeout(httpConfig.getDruidSocketTimeOut());
        if (StringUtil.isBlank(sqlQueryContext.getQueryId())) {
            sqlQueryContext.setQueryId(DigestUtil.md5Hex(StringUtil.createUUID()));
        }
        DruidQueryParam druidQueryParam = new DruidQueryParam();
        druidQueryParam.setQuery(sql);
        druidQueryParam.getContext().put("skipEmptyBuckets", druidIoHttpSource.getSkipEmptyBuckets());
        druidQueryParam.getContext().put("sqlQueryId", sqlQueryContext.getQueryId());
        druidQueryParam.getContext().put("maxSubqueryRows", druidIoHttpSource.getMaxSubqueryRows());
        druidQueryParam.setResultFormat("object");
        druidQueryParam.setHeader(true);
        druidQueryParam.setTypesHeader(true);
        druidQueryParam.setSqlTypesHeader(true);
        return druidQueryParam;
    }

    @Override
    public BaseResult generateBaseResult(String sql, Optional<String> message) {
        watch.start();

        HttpResponseResult responseResult = executeHttpPost(sql);

        if (responseResult.getStatusCode() == HttpStatusCodeEnum.SUCCESS.getCode()) {
            baseResult = BaseResultGenerator.generate(responseResult.getStatusCode(), ResultCodeEnum.SUCCESS.getCode(), true, message.orElse("OK"),
                    (List<Object>) JSON.parseObject(responseResult.getResponseBody(), Object.class), null, null, OutputMode.JSON.getValue());
        } else {
            baseResult = BaseResultGenerator.generate(responseResult.getStatusCode(),
                    CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), false, String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), message.orElse(responseResult.getErrorMessage())),
                    responseResult.getErrorMessage(), null, null, sqlQueryContext.getFormat());
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
            Object metaObj = allResults.remove(0);
            baseResult.setMeta(ColumnCategoryHelper.expandMetaCategory(unifyMeta(JSON.parseObject(JSON.toJSONString(metaObj), Map.class)), sqlQueryContext.getDbSelectStatement(), sqlQueryContext.getDbEngine()));
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

    @SneakyThrows
    @Override
    List<Map<String, String>> buildExampleMeta() {
        if (baseResult.getMeta() == null) {
            return Lists.newArrayList();
        }
        List<Map<String, String>> metaList = (List<Map<String, String>>) baseResult.getMeta();
        Map<String, SelectItemHelper.AliasObject> selectItem = SelectItemHelper.getSelectItem(sqlQueryContext.getDbSelectStatement().getSqlBody());
        for (Map<String, String> meta : metaList) {
            toPerfectMeta(selectItem, meta);
        }
        return metaList;
    }

    @Override
    public List<Object> buildExampleData() {
        if (baseResult.getMeta() == null) {
            return Lists.newArrayList();
        }
        List<Map<String, String>> metaList = (List<Map<String, String>>) baseResult.getMeta();
        Map<String, List<Object>> schemaDataDict = databaseService.getSchemaDataDict(sqlQueryContext.getDbSelectStatement().getTableNames().get(0));
        Long rowCount = SQLHelper.getRowCount(sqlQueryContext.getDbSelectStatement().getSqlBody());
        String queryType = QueryTypeHelper.determineQueryType(sqlQueryContext.getDbSelectStatement());
        Object param = QueryTypeHelper.determineParam(sqlQueryContext.getDbSelectStatement(), queryType);
        return ExampleDataHelper.buildExampleData(queryType, metaList, rowCount, schemaDataDict, param);
    }

    @Override
    BaseResult encapsulateSchemaData(BaseResult baseResult, String table) {
        SchemaBase schema = new SchemaBase();
        schema.setName(table);
        List<Map<String, Object>> fields = new ArrayList<>();
        List<Map<String, Object>> list = (List<Map<String, Object>>) baseResult.getData();
        for (Map<String, Object> resultMap : list) {
            Map<String, Object> schemaMap = Maps.newHashMap();
            schemaMap.put("name", resultMap.get("name"));
            schemaMap.put("type", resultMap.get("type"));
            fields.add(schemaMap);
        }
        schema.setFields(fields);
        baseResult.setData(schema);
        return baseResult;
    }

    /**
     * 将目标SQL转换为Dry Run SQ。使其不扫描数据，仅做执行
     *
     * @param sql
     * @return
     * @throws JSQLParserException
     */
    private String convertSampleSql(String sql) throws JSQLParserException {
        return new SampleSQLHelper(sql, " 1 != 1 ", false, null).build();
    }

    private List<Map<String, String>> unifyMeta(Map<String, Map<String, String>> metaMap) {
        List<Map<String, String>> result = new ArrayList<>();
        if (metaMap == null || metaMap.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, Map<String, String>> entry : metaMap.entrySet()) {
            String metaName = entry.getKey();
            Map<String, String> typeMap = entry.getValue();
            Map<String, String> meta = Maps.newHashMap();
            meta.put(MetaConst.META_NAME, metaName);
            String type = DruidDialect.metaMap.get(typeMap.get("sqlType"));
            meta.put(MetaConst.META_TYPE, type == null ? DataTypeMapping.STRING : type);
            result.add(meta);
        }
        return result;
    }

    private void toPerfectMeta(Map<String, SelectItemHelper.AliasObject> aliasObjects, Map<String, String> meta) {
        if (aliasObjects == null) {
            return;
        }
        String name = meta.get(MetaConst.META_NAME);
        SelectItemHelper.AliasObject aliasObject = aliasObjects.get(name);
        if (aliasObject == null) {
            return;
        }
        if (aliasObject instanceof SelectItemHelper.AliasColumn) {
            SelectItemHelper.AliasColumn aliasColumn = (SelectItemHelper.AliasColumn) aliasObject;
            String fieldName = aliasColumn.getFieldName();
            meta.put(MetaConst.META_FIELD_NAME, fieldName);
            Map schemaInfo = databaseService.getSchemaInfo(MetadataType.FIELDS.getValue(), sqlQueryContext.getDbSelectStatement().getTableNames().get(0), false);
            Object dateType = JSONPath.extract(JSON.toJSONString(schemaInfo), "$.fields[?(@.name == \"" + fieldName + "\")].doc.constraints.type");
            if (dateType instanceof List && ((List<?>) dateType).size() > 0) {
                dateType = ((List<?>) dateType).get(0);
                meta.put(MetaConst.META_DATA_TYPE, dateType.toString());
            }
        } else if (aliasObject instanceof SelectItemHelper.AliasFunExpr) {
            SelectItemHelper.AliasFunExpr aliasDatetime = (SelectItemHelper.AliasFunExpr) aliasObject;
            String dateType = aliasDatetime.getDateType();
            if (dateType != null) {
                meta.put(MetaConst.META_DATA_TYPE, dateType);
            }
        }
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
            SelectStatement dbQuerySource = sqlQueryContext.getDbSelectStatement();
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

        @SneakyThrows
        public Converter mergeFunctions() {
            sql = FunctionsMergeHelper.build(sql);
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
            if (StringUtil.isEmpty(sqlQueryContext.getFederationSelectStatement())) {
                setDefaultResultRows(sqlQueryContext.getDbSelectStatement(), engineConfigSource.getDefaultResultNum());
                setMaxCacheResultRows(sqlQueryContext.getDbSelectStatement(), engineConfigSource.getMaxCacheNum());
            } else {
                setDefaultResultRows(sqlQueryContext.getFederationSelectStatement(), engineConfigSource.getDefaultResultNum());
                setMaxCacheResultRows(sqlQueryContext.getDbSelectStatement(), engineConfigSource.getMaxCacheNum());
                setMaxCacheResultRows(sqlQueryContext.getFederationSelectStatement(), engineConfigSource.getMaxCacheNum());
            }
            return this;
        }


        private void setDefaultResultRows(SelectStatement sqlQuerySource, int defaultResultRows) {
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
        private void setMaxCacheResultRows(SelectStatement sqlQuerySource, int maxCacheResultRows) {
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
            SelectStatement sqlQuerySource = sqlQueryContext.getDbSelectStatement();
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
        private Map<String, Object> context = Maps.newHashMap();
        private String resultFormat;
        private boolean header;
        private boolean typesHeader;
        private boolean sqlTypesHeader;

    }

}

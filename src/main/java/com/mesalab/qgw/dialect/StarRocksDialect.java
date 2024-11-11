package com.mesalab.qgw.dialect;

import cn.hutool.core.util.URLUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPath;
import com.geedgenetworks.utils.StringUtil;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.entity.SchemaBase;
import com.mesalab.common.enums.*;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.common.utils.sqlparser.*;
import com.mesalab.qgw.constant.MetaConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.qgw.service.RewriteTable;
import com.mesalab.qgw.service.impl.HttpClientServiceV2;
import lombok.Data;
import lombok.SneakyThrows;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class StarRocksDialect extends AbstractDataSourceDialect {
    private static final Log log = LogFactory.get();
    private StarRocksHttpSource starRocksHttpSource = (StarRocksHttpSource) SpringContextUtil.getBean("starRocksHttpSource");
    private HttpClientServiceV2 httpClientService = (HttpClientServiceV2) SpringContextUtil.getBean("httpClientServiceV2");
    private EngineConfigSource engineConfigSource = (EngineConfigSource) SpringContextUtil.getBean("engineConfigSource");
    private DatabaseService databaseService = (DatabaseService) SpringContextUtil.getBean("databaseService");
    private Stopwatch watch = Stopwatch.createUnstarted();

    public BaseResult baseResult;

    private static final Pattern pTrimTypeDefinitionPattern = Pattern.compile("\\(\\d+\\)", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> META_MAP = Maps.newHashMap();

    static {
        // bigint、datetime、varchar(65533)、array<varchar(65533)>
        META_MAP.put("bigint", DataTypeMapping.LONG);
        META_MAP.put("double", DataTypeMapping.DOUBLE);
        META_MAP.put("datetime", DataTypeMapping.TIMESTAMP);
        META_MAP.put("varchar", DataTypeMapping.STRING);
        META_MAP.put("array<varchar>", DataTypeMapping.ARRAY);
    }

    public StarRocksDialect(SQLQueryContext queryContext) {
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
        return new Converter(sql).generateDateFunction().build();

    }

    @Override
    public String generateRuleBaseOptimizedQuery() {
        return new Optimizer().generateQueryLimit().generateSQL().generateTableAlias().build();

    }

    /**
     * 获取语法检测sql
     *
     * @return
     */
    @SneakyThrows
    @Override
    public String getSampleQuery() {
        String sampleSQL = generateRuleBaseOptimizedQuery();
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
        return "DESC " + table;
    }

    @Override
    String getExplainQuery() {
        return "EXPLAIN " + convertQuery(generateRuleBaseOptimizedQuery());
    }

    @Override
    public BaseResult executeKillQuery(String queryId) {
        return BaseResultGenerator.success();
    }

    @Override
    public BaseResult getProcesses(String queryId) {
        return BaseResultGenerator.success4Message("ok");
    }

    @Override
    public BaseResult generateBaseResult(String sql, Optional<String> message) {
        watch.start();
        HttpResponseResult responseResult = executeHttpPost(sql);

        if (responseResult.getStatusCode() == HttpStatusCodeEnum.SUCCESS.getCode()) {
            String responseBody = responseResult.getResponseBody();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(responseBody.getBytes()), Charset.defaultCharset()))) {
                Map<String, Object> result = parseNDJSON(reader);
                baseResult = BaseResultGenerator.generate(responseResult.getStatusCode(), ResultCodeEnum.SUCCESS.getCode(), true, message.orElse("OK"),
                        result, null, null, OutputMode.JSON.getValue());
            } catch (IOException | RuntimeException e) {
                log.error("SQL Rewrite add tableName and Alias Error: ", e);
                throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                        CommonErrorCode.SQL_REWRITE_AND_TRANSFORMATION_EXCEPTION.getCode(), String.format(CommonErrorCode.SQL_REWRITE_AND_TRANSFORMATION_EXCEPTION.getMessage(), e.getMessage()));
            }
        } else {
            baseResult = BaseResultGenerator.generate(responseResult.getStatusCode(),
                    CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), false, String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), message.orElse(responseResult.getErrorMessage())),
                    responseResult.getErrorMessage(), null, null, sqlQueryContext.getFormat());
        }
        return build();
    }

    /**
     * Druid Http Query Interface
     *
     * @param sql SQL语句
     * @return
     */
    private HttpResponseResult executeHttpPost(String sql) {
        String queryURL = URLUtil.normalize(starRocksHttpSource.getUrl() + "/api/v1/catalogs/default_catalog/databases/" + starRocksHttpSource.getDbname() + "/sql");
        Map<String, Object> body = Maps.newHashMap();
        body.put("query", sql);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(starRocksHttpSource.getUsername(), starRocksHttpSource.getPin());
        log.info("Query ID: {}, SQL: {}", sqlQueryContext.getQueryId(), sql);
        if (log.isDebugEnabled()) {
            log.debug("DB engine is: {}, : {}", DBEngineType.STAR_ROCKS.getValue(), JSON.toJSONString(body)
                    + ". body sql is:" + sql);
        }
        long start = System.currentTimeMillis();
        HttpResponseResult responseResult = httpClientService.post(queryURL, JSON.toJSONString(body), headers.toSingleValueMap());
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > engineConfigSource.getHighLatencyThreshold()) {
            log.warn("DB engine is: {}, queryId: {}, elapsed: {} ms, sql: {}", DBEngineType.STAR_ROCKS.getValue(), sqlQueryContext.getQueryId(), elapsed, sql);
        }
        return responseResult;
    }


    private Map<String, Object> parseNDJSON(BufferedReader reader) throws IOException {
        Map<String, Object> result = Maps.newHashMap();
        List<Map<String, Object>> dataList = Lists.newArrayList();
        String line;
        while ((line = reader.readLine()) != null) {
            try {
                JSONObject jsonObject = JSON.parseObject(line);
                if (jsonObject.containsKey("data") && jsonObject.containsKey("meta") && jsonObject.containsKey("statistics")) {
                    for (String key : jsonObject.keySet()) {
                        if ("data".equals(key)) {
                            dataList.addAll((Collection<? extends Map<String, Object>>) jsonObject.get(key));
                        } else if ("meta".equals(key)) {
                            result.put("meta", jsonObject.get("meta"));
                        } else if ("statistics".equals(key)) {
                            result.put("statistics", jsonObject.get("statistics"));
                        }
                    }
                    break;
                } else {
                    boolean isData = jsonObject.containsKey("data");
                    if (isData) {
                        List<Map<String, Object>> meta = (List<Map<String, Object>>) result.get("meta");
                        List<Object> data = (List<Object>) jsonObject.get("data");

                        Map<String, Object> dataItem = Maps.newHashMap();
                        for (int i = 0; i < data.size(); i++) {
                            dataItem.put(String.valueOf(meta.get(i).get("name")), data.get(i));
                        }
                        dataList.add(dataItem);
                        continue;
                    }
                    // 解析每一行的 JSON 数据
                    boolean isMeta = jsonObject.containsKey("meta");
                    if (isMeta) {
                        result.put("meta", jsonObject.get("meta"));
                        continue;
                    }

                    boolean isStatistics = jsonObject.containsKey("statistics");
                    if (isStatistics) {
                        result.put("statistics", jsonObject.get("statistics"));
                        continue;
                    }
                }
            } catch (Exception e) {
                log.error("parse NDJSON error. message: {}", e.getMessage(), e);
                throw new RuntimeException("parse NDJSON error. message: " + e.getMessage());
            }
        }
        result.put("data", dataList);
        return result;
    }

    private BaseResult build() {
        Object dataObject = baseResult.getData();
        if (baseResult.isSuccess()) {
            if (StringUtil.isBlank(baseResult.getMessage())) {
                baseResult.setMessage("ok");
            }
            Map<String, Object> resultMap = (Map<String, Object>) dataObject;
            Map<String, Object> statisticMap = (Map<String, Object>) resultMap.get("statistics");
            Map<String, Object> statistics = Maps.newLinkedHashMap();
            statistics.put("rows_read", statisticMap.get("scanRows"));
            statistics.put("bytes_read", statisticMap.get("scanBytes"));
            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            baseResult.setData(resultMap.get("data"));
            baseResult.setMeta(ColumnCategoryHelper.expandMetaCategory(unifyMeta(resultMap.get("meta")), sqlQueryContext.getDbSelectStatement(), sqlQueryContext.getDbEngine()));
            baseResult.setStatistics(statistics);
        } else {
            if (baseResult.getStatus() >= HttpStatusCodeEnum.BAD_REQUEST.getCode()
                    && baseResult.getStatus() < HttpStatusCodeEnum.SERVER_ERROR.getCode()) {
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
        return String.valueOf(dataObject);
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
            schemaMap.put("name", resultMap.get("Field"));
            schemaMap.put("type", resultMap.get("Type"));
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

    private List<Map<String, String>> unifyMeta(Object meta) {
        List<Map<String, String>> metaList = Lists.newArrayList();
        if (StringUtil.isEmpty(meta)) {
            return metaList;
        }
        metaList = JsonPath.read(meta, "$");
        metaList.forEach(x ->
        {
            String dbType = x.get("type");
            dbType = pTrimTypeDefinitionPattern.matcher(dbType).replaceAll("");
            x.put("type", META_MAP.getOrDefault(dbType, DataTypeMapping.STRING));
        });
        return metaList;
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

        public Converter generateDateFunction() {
            sql = SQLFunctionUtil.generateDateFunction(sql, DBEngineType.STAR_ROCKS.getValue());
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
                if (Integer.parseInt(limits[limits.length - 1]) > maxCacheResultRows) {
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

        public Optimizer generateTableAlias() {
            try {
                Select select = (Select) CCJSqlParserUtil.parse(sql);
                DerivedTableHelper.build(select);
                sql = select.toString();
            } catch (JSQLParserException | RuntimeException e) {
                log.error("SQL Rewrite add tableName and Alias Error: ", e);
                throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                        CommonErrorCode.SQL_REWRITE_AND_TRANSFORMATION_EXCEPTION.getCode(), String.format(CommonErrorCode.SQL_REWRITE_AND_TRANSFORMATION_EXCEPTION.getMessage(), e.getMessage()));
            }
            return this;
        }

        public String build() {
            return sql;
        }


        /**
         * 对sql统一处理添加库名、别名
         *
         * @return
         */
    }

}

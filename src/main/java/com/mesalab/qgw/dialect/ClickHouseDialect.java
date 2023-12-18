package com.mesalab.qgw.dialect;


import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.enums.*;
import com.mesalab.common.utils.*;
import com.mesalab.common.utils.sqlparser.*;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.service.RewriteTable;
import com.mesalab.qgw.model.basic.HttpConfig;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.service.DatabaseService;
import com.geedgenetworks.utils.Encodes;
import com.geedgenetworks.utils.StringUtil;
import com.mesalab.qgw.service.impl.HttpClientServiceV2;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import org.apache.avro.Schema;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.springframework.util.CollectionUtils;

import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClickHouseDialect extends AbstractDataSourceDialect {
    private static final Log log = LogFactory.get();
    private static final Pattern pNullable = Pattern.compile("\\bNullable\\((.*?)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern pIn = Pattern.compile("(?<!\\bnot)\\s+in\\s*\\(\\s*SELECT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern pNotIn = Pattern.compile("\\bnot\\s+in\\s*\\(\\s*SELECT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern pUniq = Pattern.compile("count\\s*\\(\\s*distinct\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern pUniqCombined = Pattern.compile("count\\s*\\(\\s*distinct\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern pWhere = Pattern.compile("where\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern pGroupBy = Pattern.compile("\\bgroup\\s+by\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern pDistinct = Pattern.compile("\\b(DISTINCT)(\\(|\\s+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern pAggregateFunSpecificOfCK = Pattern.compile("\\b(anyHeavy|anyLast|argMin|argMax|avgWeighted|topK|topKWeighted|groupArray|groupUniqArray|groupArrayInsertAt|groupArrayMovingAvg|groupArrayMovingSum|groupBitAnd|groupBitOr|groupBitXor|groupBitmap|groupBitmapAnd|groupBitmapOr|groupBitmapXor|sumWithOverflow|sumMap|minMap|maxMap|skewSamp|skewPop|kurtSamp|kurtPop|uniq|uniqExact|uniqCombined|uniqCombined64|uniqHLL12|quantile|quantiles|quantileExact|quantileExactLow|quantileExactHigh|quantileExactWeighted|quantileTiming|quantileTimingWeighted|quantileDeterministic|quantileTDigest|quantileTDigestWeighted|quantileBFloat16|quantileBFloat16Weighted|simpleLinearRegression|stochasticLinearRegression|stochasticLogisticRegression|categoricalInformationValue)\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern pCount1 = Pattern.compile("count\\(\\s*1\\s*\\)", Pattern.CASE_INSENSITIVE);
    private final ClickHouseHttpSource clickHouseHttpSource = (ClickHouseHttpSource) SpringContextUtil.getBean("clickHouseHttpSource");
    private final HttpClientServiceV2 httpClientService = (HttpClientServiceV2) SpringContextUtil.getBean("httpClientServiceV2");
    private final DatabaseService databaseService = (DatabaseService) SpringContextUtil.getBean("databaseService");
    private final EngineConfigSource engineConfigSource = (EngineConfigSource) SpringContextUtil.getBean("engineConfigSource");
    private final HttpConfig httpConfig = (HttpConfig) SpringContextUtil.getBean("httpConfig");
    public QueryContext queryContext;
    public BaseResult baseResult;

    private final static double PERCENT_UP_LINE = 0.98;
    private final static double PERCENT_DOWN_LINE = 0.01;
    private final static String DB_EXCEPTION_START = "e.displayText() = DB::Exception: ";

    private final static Map<String, String> metaMap = Maps.newHashMap();

    static {
        //实际Int128、Int256、UInt64、UInt256的最大值已经超过long最大值
        metaMap.put("Int8", DataTypeMapping.INT);
        metaMap.put("Int16", DataTypeMapping.INT);
        metaMap.put("Int32", DataTypeMapping.INT);
        metaMap.put("Int64", DataTypeMapping.LONG);
        metaMap.put("Int128", DataTypeMapping.LONG);
        metaMap.put("Int256", DataTypeMapping.LONG);
        metaMap.put("UInt8", DataTypeMapping.INT);
        metaMap.put("UInt16", DataTypeMapping.INT);
        metaMap.put("UInt32", DataTypeMapping.LONG);
        metaMap.put("UInt64", DataTypeMapping.LONG);
        metaMap.put("UInt256", DataTypeMapping.LONG);
        metaMap.put("Float32", DataTypeMapping.FLOAT);
        metaMap.put("Float64", DataTypeMapping.DOUBLE);
        metaMap.put("Date", DataTypeMapping.DATE);
        metaMap.put("DateTime", DataTypeMapping.TIMESTAMP);
    }

    public ClickHouseDialect(QueryContext queryContext) {
        this.queryContext = queryContext;
    }


    @Override
    public void init() {
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
        return new Converter(sql).
                generateStandard().generateDateFunction().generateSqlEncode().build();
    }

    @Override
    public String getBestQuery() {
        return new Optimizer().generateQueryLimit().
                generateSQL().generateSubQuery().generateTableAlias().generateApproximation().build();
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
            log.error("Sample sql error {}, execute original sql: {}, error is: {} ",
                    sampleSQL, sampleSQL = queryContext.getDbQuerySource().getSqlBody(), e.getMessage() == null ? e.getCause() : e.getMessage());
        }
        return sampleSQL;
    }

    @Override
    String getExplainQuery() {
        return "explain " + convertQuery(getBestQuery());
    }

    @Override
    public BaseResult executeKillQuery(String queryId) {
        String killSql = StrUtil.format("kill query where query_id ='{}'", queryId);
        String queryURL = URLUtil.normalize(clickHouseHttpSource.getUrl() + "/?");
        StringBuilder queryParamBuilder = new StringBuilder(getLongTermAccount()).append("&")
                .append("database=").append(clickHouseHttpSource.getDbName()).append("&")
                .append("query=").append(killSql)
                .append(" FORMAT ").append(OutputMode.JSON.getValue().toUpperCase()).append(" ;");
        List<NameValuePair> values = URLEncodedUtils.parse(queryParamBuilder.toString(), Charset.forName("UTF-8"));

       HttpResponseResult responseResult = httpClientService.get(queryURL + URLEncodedUtils.format(values, "utf-8"));
        if (responseResult.getStatusCode() == HttpStatusCodeEnum.SUCCESS.getCode()) {
            return BaseResultGenerator.generate(HttpStatusCodeEnum.ACCEPTED.getCode(), null, "ok", null, null, null, null);
        } else {
            return BaseResultGenerator.failure(responseResult.getStatusCode(), responseResult.getErrorMessage());
        }
    }

    @Override
    public BaseResult getProcesses(String queryId) {
        List<String> tableNames = Lists.newArrayList();
        SQLQuerySource sqlQuerySource = new SQLQuerySource();
        String tableName = "processes";
        tableNames.add(tableName);
        String sql = StrUtil.format("select elapsed,total_rows_approx,read_rows from system.{} where query_id='{}'", tableName, queryId);
        queryContext.setQuery(sql);
        sqlQuerySource.setExecSQL(queryContext.getQuery());
        sqlQuerySource.setTableNames(tableNames);
        queryContext.setDbQuerySource(sqlQuerySource);
        BaseResult baseResult = generateBaseResult(sql, Optional.of("ok"));
        Map<String, Object> result = new HashMap<>(16);
        if (!baseResult.isSuccess()) {
            return baseResult;
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        if (CollectionUtils.isEmpty(data)) {
            return BaseResultGenerator.success(data);
        }
        Map<String, Object> map = data.get(0);
        long totalRowsApprox = Long.parseLong(map.get("total_rows_approx").toString());
        long readRows = Long.parseLong(map.get("read_rows").toString());
        long elapsed = Math.round(Double.parseDouble(map.get("elapsed").toString()) * 1000);
        double percent;
        if (readRows == 0
                || totalRowsApprox == 0
                || (percent = Double.parseDouble(String.format("%.2f", (readRows * 1.00 / totalRowsApprox)))) < PERCENT_DOWN_LINE) {
            percent = PERCENT_DOWN_LINE;
        }
        if (percent > PERCENT_UP_LINE) {
            percent = PERCENT_UP_LINE;
        }
        result.put("elapsed", elapsed);
        result.put("rows_read", readRows);
        result.put("percent", percent);
        List<Object> list = Lists.newArrayList();
        list.add(result);
        return BaseResultGenerator.success(list);
    }

    private List<Map<String, String>> unifyMeta(Object meta) {
        List<Map<String, String>> metaList = Lists.newArrayList();
        if (StringUtil.isEmpty(meta)) {
            return metaList;
        }
        metaList = JsonPath.read(meta, "$");
        metaList.forEach(x ->
        {
            String type = x.get("type");
            Matcher matcher = pNullable.matcher(type);
            if (matcher.find()) {
                type = matcher.group(1);
            }
            x.put("type", metaMap.get(type) == null ? DataTypeMapping.STRING : metaMap.get(type));
        });
        return metaList;
    }


    class Converter {
        String sql = null;

        public Converter(String sql) {
            this.sql = sql;
        }

        /**
         * 兼容标准SQL语言（支持CK19.14.6.12 新特性）：
         * 1.不支持 count(1) 需转为count(*)
         * 2.针对非聚合SQL，非分组sql in/not in 替换为 Global in/not
         * @return Converter
         */
        public Converter generateStandard() {
            sql = generateCount(sql);
            sql = generateGlobalIn(sql);
            return this;
        }

        /**
         * Desc:
         * IN 转换为 集群 Global IN 模式(针对聚合、分组)
         *
         * @param sql
         * @return {@link String}
         * @created by wWei
         * @date 2021/3/1 11:47 上午
         */
        public String generateGlobalIn(String sql) {
            if (containAggregateFunction(sql) || containGroupBy(sql) || containDistinct(sql)) {
                sql = generateIn(sql);
                sql = generateNotIn(sql);
            }
            return sql;
        }

        private String generateIn(String sql) {
            Matcher m = pIn.matcher(sql);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, " global in ( select ");
            }
            m.appendTail(sb);
            return sb.toString();
        }

        private String generateNotIn(String sql) {
            Matcher m = pNotIn.matcher(sql);
            StringBuffer sb = new StringBuffer();

            while (m.find()) {
                m.appendReplacement(sb, " global not in ( select ");
            }
            m.appendTail(sb);
            return sb.toString();
        }

        public Converter generateDateFunction() {
            sql = SQLFunctionUtil.generateDateFunction(sql, DBType.CLICKHOUSE.getValue());
            return this;
        }

        /**
         * http GET  sql 里特殊符号需要做encode编码
         *
         * @return
         */
        public Converter generateSqlEncode() {
            return this;
        }

        public String build() {
            return sql;
        }

        /**
         * 集群不支持count(1) 需转成count(*)
         *
         * @param sql
         * @return
         */
        private String generateCount(String sql) {
            Matcher m = pCount1.matcher(sql);
            StringBuffer sb = new StringBuffer();

            while (m.find()) {
                m.appendReplacement(sb, "count(*)");
            }
            m.appendTail(sb);
            return sb.toString();
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

        /**
         * 兼容标准SQL语言（支持CK19.14.6.12 新特性）：
         * 1.集群模式子查询必须加库名、别名 (解决方法: 对sql统一处理添加库名、别名)
         *
         * @return
         */
        public Optimizer generateTableAlias() {
            try {
                Select select = (Select) CCJSqlParserUtil.parse(sql);
                ExpressionDeParser expressionDeParser = new ExpressionDeParser();
                StringBuilder buffer = new StringBuilder();
                RewriteTable deParser = new RewriteTable(expressionDeParser, buffer);
                expressionDeParser.setSelectVisitor(deParser);
                expressionDeParser.setBuffer(buffer);
                select.getSelectBody().accept(deParser);
                sql = buffer.toString();
            } catch (JSQLParserException | RuntimeException e) {
                log.error("SQL Rewrite add tableName and Alias Error: ", e);
                throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                        CommonErrorCode.SQL_REWRITE_AND_TRANSFORMATION_EXCEPTION.getCode(), String.format(CommonErrorCode.SQL_REWRITE_AND_TRANSFORMATION_EXCEPTION.getMessage(), e.getMessage()));
            }
            return this;
        }

        /**
         * count(distinct())转为uniq函数
         *
         * @return
         */
        public Optimizer generateUniq() {
            Matcher m = pUniq.matcher(sql);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                if (m.groupCount() == 1) {
                    String replaceValue = m.group(1);
                    m.appendReplacement(sb, "uniq(" + replaceValue);

                }
            }
            m.appendTail(sb);
            sql = sb.toString();
            return this;
        }

        /**
         * count(distinct())转为uniqCombined函数
         *
         * @return
         */
        private Optimizer generateUniqCombined() {

            Matcher m = pUniqCombined.matcher(sql);

            StringBuffer sb = new StringBuffer();

            while (m.find()) {

                if (m.groupCount() == 1) {
                    String replaceValue = m.group(1);
                    m.appendReplacement(sb, "uniqCombined(" + replaceValue);

                }
            }
            m.appendTail(sb);

            sql = sb.toString();
            return this;
        }

        /**
         * Desc: 近似优化
         * 1. uniq优化
         * 2. TopN优化
         *
         * @param
         * @return {@link Optimizer}
         * @created by wWei
         * @date 2021/8/9 3:19 下午
         */
        public Optimizer generateApproximation() {
            if (!clickHouseHttpSource.isEnableApproximateOptimizer()) {
                return this;
            }
            generateDistributedGroupBy().generateUniq();
            return this;
        }

        /**
         * 事件索引子查询，目的提高日志查询检索性能:
         * 方式1.基于索引表
         * 方式2.基于分区键
         * 限制：
         * 1. 仅适用一级普通查询，带where （无group by 统计）的查询
         * 2. 不对count 统计进行生成
         *
         * @return
         */
        public Optimizer generateSubQuery() {

            if (StringUtil.isNotEmpty(queryContext.getDbQuerySource().getJoins()) || queryContext.getDbQuerySource().getTableNames().size() > 1) {
                return this;
            }
            if (StringUtil.isEmpty(queryContext.getDbQuerySource().getWhereExpression())) {
                return this;
            }
            if (containAggregateFunction(sql) || containGroupBy(sql) || containDistinct(sql)) {
                return this;
            }
            try {
                String indexTable = getAvailableIndexTable();
                if (StringUtil.isNotBlank(indexTable)) {
                    sql = getSubQueryByIndexTable(indexTable);
                } else {
                    sql = getSubQueryByPartitionKey(sql);
                }
            } catch (JSQLParserException | RuntimeException e) {
                log.error("generate SubQuery optimize error: {}", e.getMessage());
            }
            return this;
        }

        /**
         * Desc: 针对单条查询Top做优化
         * settings distributed_group_by_no_merge=1
         * If you set it to 1, then intermediate results from remote servers will not be merged.
         *
         * @param
         * @return {@link Optimizer}
         * @created by wWei
         * @date 2021/7/28 9:37 上午
         */
        public Optimizer generateDistributedGroupBy() {
            TopSQLVisitor topSQLVisitor = new TopSQLVisitor();
            if (topSQLVisitor.isValid(sql)) {
                sql = topSQLVisitor.rewrite();
            }
            return this;
        }

        /**
         * 设置索引表子查询参数: 索引表有效性、索引表表名
         *
         * @return
         */
        private String getAvailableIndexTable() throws JSQLParserException {
            String str = databaseService.getValueByKeyInSchemaDoc(queryContext.getDbQuerySource().getTableNames().get(0), "index_table");
            String[] indexTables = StringUtil.split(str, ",");
            if (StringUtil.isEmpty(indexTables) || StringUtil.isEmpty(databaseService.getIndexKey(queryContext.getDbQuerySource().getTableNames().get(0)))) {
                return StringUtil.EMPTY;
            }
            Expression expression = CondExpressionHelper.rewireCondExprBaseOnCK(queryContext.getDbQuerySource().getWhereExpression().toString(), queryContext.getDbQuerySource().getAliasFields(), false, null);
            List<String> actualFieldOfWhere = CondExpressionHelper.getDistinctFields(expression.toString());
            table:
            for (String tempIndexTable : indexTables) {
                List<String> indexKey = databaseService.getIndexKey(tempIndexTable);
                //判断一: where 是否包含索引键
                if (StringUtil.isEmpty(indexKey) || !actualFieldOfWhere.contains(indexKey.get(0))) {
                    continue;
                }
                Schema indexTableSchema = databaseService.getSchemaByName(tempIndexTable);
                //判断二: where 条件列是否都在索引表中
                for (String field : actualFieldOfWhere) {
                    if (!isFieldOfSchema(indexTableSchema, field)) {
                        continue table;
                    }
                }
                //判断三: order by 元素是否都在索引表中
                if (!isValidOfOrderElementsInSchema(indexTableSchema)) {
                    continue;
                }
                return indexTableSchema.getName();
            }
            return StringUtil.EMPTY;
        }

        /**
         * 基于索引表方式添加子查询
         *
         * @param indexTable
         * @return
         */
        private String getSubQueryByIndexTable(String indexTable) throws JSQLParserException {

            String primaryKey = databaseService.getValueByKeyInSchemaDoc(queryContext.getDbQuerySource().getTableNames().get(0), "primary_key");
            List<String> indexKey = databaseService.getIndexKey(queryContext.getDbQuerySource().getTableNames().get(0));
            indexKey = indexKey.subList(0, indexKey.contains(primaryKey) ? indexKey.indexOf(primaryKey) + 1 : indexKey.size());

            List<String> indexKeyMaster = Lists.newArrayList();
            String nameMaster = databaseService.getSchemaByName(queryContext.getDbQuerySource().getTableNames().get(0)).getName();
            indexKey.forEach(o -> indexKeyMaster.add(nameMaster.concat(".").concat(o)));

            String subQuery = getSubquerySql(String.join(",", indexKey), indexTable);
            StringBuffer sb = new StringBuffer();
            sb.append("select ").append(queryContext.getDbQuerySource().getSelect()).append(" from ").append(queryContext.getDbQuerySource().getTableNames().get(0)).append(" where ( ").append(String.join(",", indexKeyMaster)).append(" ) in ( ").append(subQuery).append(")");
            if (StringUtil.isNotEmpty(queryContext.getDbQuerySource().getWhereExpression())) {
                sb.append(" and (").append(queryContext.getDbQuerySource().getWhereExpression().toString()).append(")");
            }
            if (!queryContext.getDbQuerySource().getOrderByElements().isEmpty()) {
                sb.append(" order by ").append(queryContext.getDbQuerySource().getOrderBy());
            }
            if (StringUtil.isNotBlank(queryContext.getDbQuerySource().getLimit())) {
                sb.append(" limit ").append(queryContext.getDbQuerySource().getLimit());
            }
            return sb.toString();
        }

        /**
         * 基于分区键方式
         *
         * @param sql
         * @return
         */
        private String getSubQueryByPartitionKey(String sql) throws JSQLParserException {
            String partitionKey = databaseService.getPartitionKey(queryContext.getDbQuerySource().getTableNames().get(0));
            if (StringUtil.isBlank(partitionKey)) {
                log.warn("Partition Key is Empty. Focus in tableName {} schema", queryContext.getDbQuerySource().getTableNames().get(0));
                return sql;
            }
            String subQuery = getSubquerySql(partitionKey, queryContext.getDbQuerySource().getTableNames().get(0));
            subQuery = subQuery.replaceAll("\\\\", "$0$0");
            subQuery = subQuery.replaceAll("\\$", "\\\\\\$");
            Matcher m = pWhere.matcher(sql);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, "where  " + databaseService.getSchemaByName(queryContext.getDbQuerySource().getTableNames().get(0)).getName().concat(".").concat(partitionKey) + " in ( " + subQuery + ") and  ");
            }
            m.appendTail(sb);
            return sb.toString();
        }

        /**
         * 获取子sql
         *
         * @param columnName
         * @param tableName
         * @return
         */
        private String getSubquerySql(String columnName, String tableName) throws JSQLParserException {
            StringBuilder subQuery = new StringBuilder();

            subQuery.append("select " + columnName + " from ").append(tableName)
                    .append(" where ").append(CondExpressionHelper.rewireCondExprBaseOnCK(queryContext.getDbQuerySource().getWhereExpression().toString(), queryContext.getDbQuerySource().getAliasFields(), true, tableName));

            //order by中元素存在别名时做替换处理
            List<OrderByElement> listOrderElement = queryContext.getDbQuerySource().getOrderByElements();
            if (StringUtil.isNotEmpty(listOrderElement)) {
                String orderBy = queryContext.getDbQuerySource().getOrderBy();
                for (OrderByElement order : listOrderElement) {
                    String orderField = order.getExpression().toString();
                    if ((orderField.startsWith("\"") && orderField.endsWith("\""))
                            || (orderField.startsWith("`") && orderField.endsWith("`"))) {
                        orderField = orderField.substring(1, orderField.length() - 1);
                    }
                    String originalField = queryContext.getDbQuerySource().getAliasFields().get(orderField);
                    if (StringUtil.isNotBlank(originalField)) {
                        orderBy = orderBy.replaceAll("(\"" + orderField + "\")|(\\b" + orderField + "\\b)", originalField);
                    }

                }
                subQuery.append(" order by ").append(orderBy);
            }


            if (StringUtil.isNotBlank(queryContext.getDbQuerySource().getLimit())) {
                String[] limits = queryContext.getDbQuerySource().getLimit().split(",");
                if (limits.length == 2) {
                    int rows = Integer.valueOf(limits[0].trim()) + Integer.valueOf(limits[1].trim());
                    subQuery.append(" limit ").append(rows);
                } else {
                    subQuery.append(" limit ").append(queryContext.getDbQuerySource().getLimit());
                }

            } else {
                subQuery.append(" limit 0, " + engineConfigSource.getDefaultResultNum());
            }
            return subQuery.toString();
        }

        /**
         * 判断order by 元素是否属于schema字段中
         *
         * @param schema
         * @return
         */
        private boolean isValidOfOrderElementsInSchema(Schema schema) throws JSQLParserException {
            List<OrderByElement> listOrderElement = queryContext.getDbQuerySource().getOrderByElements();
            if (StringUtil.isEmpty(listOrderElement)) {
                return true;
            }
            for (OrderByElement order : listOrderElement) {
                OrderByElement orderByElement = new OrderByElement();
                Expression expression = CondExpressionHelper.rewireCondExprBaseOnCK(String.valueOf(order.getExpression()), queryContext.getDbQuerySource().getAliasFields(), false, null);
                orderByElement.setExpression(expression);
                List<String> fields = CondExpressionHelper.getDistinctFieldsOfOrderBy(orderByElement);
                for (String field : fields) {
                    if (!isFieldOfSchema(schema, field)) {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * 判断schema中是否存在field列
         *
         * @param schema
         * @param fieldName
         * @return
         */
        private boolean isFieldOfSchema(Schema schema, String fieldName) {
            Schema.Field field = schema.getField(fieldName);
            if (StringUtil.isEmpty(field)) {
                return false;
            }
            return true;
        }

        public String build() {
            return sql;
        }


    }


    /**
     * 执行SQL获取查询结果，此数据为数据库返回的原始结果。
     * 主要目标统一封装BaseResult对象，便于后续对结果进行业务模型构建。
     *
     * @param sql
     * @param message 携带消息
     * @return
     */
    @Override
    public BaseResult generateBaseResult(String sql, Optional<String> message) {

        HttpResponseResult responseResult = executeHttpPost(sql);
        if (responseResult.getStatusCode() == HttpStatusCodeEnum.SUCCESS.getCode()) {

            baseResult = BaseResultGenerator.generate(
                    responseResult.getStatusCode(),
                    ResultCodeEnum.SUCCESS.getCode(),
                    message.get(),
                    JSON.parseObject(responseResult.getResponseBody(), Map.class),
                    null,
                    null,
                    OutputMode.JSON.getValue());
        } else {
            baseResult = BaseResultGenerator.generate(responseResult.getStatusCode(),
                    CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), message.get()),
                    responseResult.getErrorMessage(),
               null,
            null,
                    queryContext.getFormat());
        }
        return build();
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
            statistics.put("elapsed", Math.round(Double.parseDouble(String.valueOf(statisticMap.get("elapsed"))) * 1000));
            statistics.put("rows_read", statisticMap.get("rows_read"));
            statistics.put("bytes_read", statisticMap.get("bytes_read"));
            baseResult.setData(resultMap.get("data"));
            baseResult.setMeta(unifyMeta(resultMap.get("meta")));
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
        String message = StringUtil.isNotEmpty(dataObject) ? String.valueOf(dataObject)
                : "System Runtime Exception, Please contact Galaxy Data Platform Project manager";
        int i = message.indexOf(DB_EXCEPTION_START);
        i = i > 0 ? i + DB_EXCEPTION_START.length() : 0;
        message = message.startsWith("Code: 47") ?
                message.substring(i, !message.contains(" while ") ? message.length() : message.indexOf(" while "))
                : message;
        message = message.startsWith("Code: ") ?
                message.substring(i, !message.contains("(version ") ? message.length() : message.indexOf("(version "))
                : message;
        return StrUtil.removeAllLineBreaks(message).trim();
    }

    private boolean containAggregateFunction(String sql) {
        return (SQLFunctionUtil.pAggregateFunStandard.matcher(sql).find()
                || pAggregateFunSpecificOfCK.matcher(sql).find());
    }

    private boolean containGroupBy(String sql) {
        return pGroupBy.matcher(sql).find();
    }

    private boolean containDistinct(String sql) {
        return pDistinct.matcher(sql).find();
    }


    /**
     * 通过递归方式将原SQL转换为样例SQL
     * 做数据集限制:
     * 1. 将最内层sql的表名, 替换成(select * from tableName limit XXX) as alias
     * 2. 可对where、having添加：OR 1=1
     *
     * @param sql 原始sql
     * @return 经转换后的样例SQL
     * @throws JSQLParserException
     */
    private String convertSampleSql(String sql) throws JSQLParserException {
        String dataSet = StrUtil.format("SELECT * FROM {} WHERE rand() % 100 = 0 LIMIT 1000");
        return new SampleSQLHelper(sql, dataSet, true).build();
    }

    /**
     * ClickHouse Http Query Interface
     *
     * @param sql SQL语句
     * @return
     */
    private HttpResponseResult executeHttpPost(String sql) {
        String queryURL = URLUtil.normalize(clickHouseHttpSource.getUrl() + "/?");
        String queryParameter = enrichContextAndBuildQueryParameter();
        log.info("Query ID: {}, SQL: {}", queryContext.getQueryId(), sql);
        if (log.isDebugEnabled()) {
            log.debug("DB engine is: {}, : {}", DBType.CLICKHOUSE.getValue(), queryURL +
                    Encodes.urlDecode(queryParameter).replaceAll("password=(.*?)&", "password=XXX&") + ". body sql is:" + sql);
        }
        List<NameValuePair> values = URLEncodedUtils.parse(queryParameter, Charset.forName("UTF-8"));
        return httpClientService.post(queryURL + URLEncodedUtils.format(values, "utf-8"),
                                    queryContext.getTimeout(),
                                    sql.concat(" FORMAT ").concat(OutputMode.JSON.getValue().toUpperCase()));
    }

    private String enrichContextAndBuildQueryParameter() {
        StringBuilder queryParameterBuilder = new StringBuilder(1024);

        String dbName = StringUtil.isNotEmpty(queryContext.getDbQuerySource().getTableNames())
                ? databaseService.getDBNameByTableName(queryContext.getDbQuerySource().getTableNames().get(0)) : clickHouseHttpSource.getDbName();

        if (dbName.equalsIgnoreCase(clickHouseHttpSource.getSystemDBName())) {
            queryContext.setOption(QueryOption.LONG_TERM.getValue());
        }

        int timeout = (QueryOption.LONG_TERM.getValue().equalsIgnoreCase(queryContext.getOption())) ?
                    httpConfig.getCkLongTermAccountSocketTimeOut() : httpConfig.getCkRealTimeAccountSocketTimeOut();
        queryContext.setTimeout(timeout);

        if (StringUtil.isBlank(queryContext.getQueryId())) {
            queryContext.setQueryId(DigestUtil.md5Hex(StringUtil.createUUID()));
        }

        //QueryContext set queryParameter
        if (QueryOption.LONG_TERM.getValue().equalsIgnoreCase(queryContext.getOption())) {
            queryParameterBuilder.append(getLongTermAccount()).append("&")
                    .append("database=").append(dbName);
        } else {
            queryParameterBuilder.append(getRealTimeAccount()).append("&")
                    .append("database=").append(dbName);
        }
        queryParameterBuilder.append("&").append("query_id=").append(queryContext.getQueryId());

        return queryParameterBuilder.toString();
    }

    private String getRealTimeAccount() {
        return new StringBuilder("user=").append(clickHouseHttpSource.getRealTimeAccountUserName()).append("&")
                .append("password=").append(clickHouseHttpSource.getRealTimeAccountPin()).toString();
    }

    private String getLongTermAccount() {
        return new StringBuilder("user=").append(clickHouseHttpSource.getLongTermAccountUserName()).append("&")
                .append("password=").append(clickHouseHttpSource.getLongTermAccountPin()).toString();
    }

}

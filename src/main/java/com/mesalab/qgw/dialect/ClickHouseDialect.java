package com.mesalab.qgw.dialect;


import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.base.DataTypeMapping;
import com.mesalab.common.enums.*;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.*;
import com.mesalab.common.utils.sqlparser.*;
import com.mesalab.knowledge.common.utils.HttpConfig;
import com.mesalab.qgw.model.api.*;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.mesalab.common.utils.TableFinder;
import com.zdjizhi.utils.Encodes;
import com.zdjizhi.utils.StringUtil;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import org.apache.avro.Schema;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.springframework.util.CollectionUtils;

import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClickHouseDialect extends AbstractDataSourceDialect {
    private static final Log log = LogFactory.get();
    private static final Pattern pSUM = Pattern.compile("\\bsum\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern pNullable = Pattern.compile("\\bNullable\\((.*?)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern pAvg = Pattern.compile("\\bavg\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern pMax = Pattern.compile("\\bmax\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern pMin = Pattern.compile("\\bmin\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern pIn = Pattern.compile("(?<!\\bnot)\\s+in\\s*\\(\\s*SELECT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern pNotIn = Pattern.compile("\\bnot\\s+in\\s*\\(\\s*SELECT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern pUniq = Pattern.compile("count\\s*\\(\\s*distinct\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern pUniqCombined = Pattern.compile("count\\s*\\(\\s*distinct\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern pWhere = Pattern.compile("where\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern pGroupBy = Pattern.compile("\\bgroup\\s+by\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern pCount = Pattern.compile("\\bcount\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern pCount1 = Pattern.compile("count\\(\\s*1\\s*\\)", Pattern.CASE_INSENSITIVE);
    private ClickHouseHttpSource clickHouseHttpSource = (ClickHouseHttpSource) SpringContextUtil.getBean("clickHouseHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");
    private MetadataService metadataService = (MetadataService) SpringContextUtil.getBean("metadataService");
    private EngineConfigSource engineConfigSource = (EngineConfigSource) SpringContextUtil.getBean("engineConfigSource");
    private HttpConfig httpConfig = (HttpConfig) SpringContextUtil.getBean("httpConfig");
    public ApiParam param;
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

    public ClickHouseDialect(ApiParam param) {
        this.param = param;
    }


    @Override
    public void init() {
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
        return new Converter(sql).
                generateStandard().generateDateFunction().generateSqlEncode().build();
    }

    @Override
    public String getBestQuery() {
        return new Optimizer().generateQueryLimit().
                generateSQL().generateSubQuery().generateTableAlias().generateApproximation().build();
    }

    @Override
    public String getOriginQuery() {
        return param.getQuery();
    }

    /**
     * 获取语法检测sql
     *
     * @return
     */
    @Override
    public String getSyntaxCheckQuery() {
        String sql = param.getDbQuerySource().getSqlBody();
        try {
            sql = String.valueOf(parserAndUpdateSql(sql));
        } catch (JSQLParserException e) {
            log.error("syntax-check sql error {}, execute original sql: {}, error is: {} ",
                    sql, sql = param.getDbQuerySource().getSqlBody(), e.getMessage() == null ? e.getCause() : e.getMessage());
        }
        return sql;
    }

    @Override
    public BaseResult executeKillQuery() {
        String killSql = String.format("kill query where query_id ='%s'", param.getQueryId());
        String queryURL = URLUtil.normalize(clickHouseHttpSource.getUrl() + "/?");
        StringBuilder queryParamBuilder = new StringBuilder(getLongTermAccount()).append("&")
                .append("database=").append(clickHouseHttpSource.getDbName()).append("&")
                .append("query=").append(killSql)
                .append(" FORMAT ").append(QueryFormatEnum.JSON.getValue().toUpperCase()).append(" ;");
        List<NameValuePair> values = URLEncodedUtils.parse(queryParamBuilder.toString(), Charset.forName("UTF-8"));

        Map<String, String> map = httpClientService.httpGet(queryURL + URLEncodedUtils.format(values, "utf-8"), 0);
        if (Integer.parseInt(map.get("status")) == HttpStatus.SC_OK) {
            return BaseResultGenerator.generate(HttpStatus.SC_ACCEPTED, null, "ok", null, null, null, null);
        } else {
            return BaseResultGenerator.failure(Integer.parseInt(map.get("status")), map.get("result"));
        }
    }

    @Override
    public BaseResult getProcesses() {
        String tableName = "processes";
        String sql = String.format("select elapsed,total_rows_approx,read_rows from `system`.%s where query_id='%s'", tableName, param.getQueryId());
        param.setQuery(sql);
        param.setTableName(tableName);
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
         * @return
         */
        public Converter generateStandard() {
            sql = generateCount(sql);
            sql = generateGlobalIn(sql);
            return this;
        }

        /**
         * Desc:
         *  IN 转换为 集群 Global IN 模式(针对聚合、分组)
         *
         * @param sql
         * @return {@link String}
         * @created by wWei
         * @date 2021/3/1 11:47 上午
         */
        public String generateGlobalIn(String sql) {
            if (containAggregateFunction(sql) || containGroupBy(sql)) {
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
            sql = SQLFunctionUtil.generateDateFunction(sql, DBTypeEnum.CLICKHOUSE.getValue());
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
        private Boolean subIndexValidFlag = true;
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

            String originalInnerSql = innerSqlQuerySource.getSqlBody();
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
                sqlQuerySource.setSqlBody(sqlQuerySource.getSqlBody().replace(originalInnerSql, limitInnerSql));
            }

        }

        public Optimizer generateSQL() {
            SQLQuerySource sqlQuerySource = param.getDbQuerySource();
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
                TableFinder tableFinder = new TableFinder();
                Statement parse = CCJSqlParserUtil.parse(sql);
                tableFinder.setDefaultAlias(true);
                tableFinder.addSchemaName(parse);
                sql = String.valueOf(parse);
            } catch (JSQLParserException e) {
                log.error("SQL Rewrite add tableName and Alias Error: ", e.toString());
                throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "SQL Rewrite add tableName and Alias Error", e);
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
            StringBuffer sb = new StringBuffer();
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

            if (StringUtil.isBlank(param.getDbQuerySource().getExpr())) {
                return this;
            }
            if (containAggregateFunction(sql) || containGroupBy(sql)) {
                return this;
            }

            String indexTable = getAvailableIndexTable();
            if (StringUtil.isNotBlank(indexTable)) {
                sql = getSubQueryByIndexTable(indexTable);
            } else {
                sql = getSubQueryByPartitionKey(sql);
            }
            return this;
        }

        /**
         * Desc: 针对单条查询Top做优化
         * settings distributed_group_by_no_merge=1
         * If you set it to 1, then intermediate results from remote servers will not be merged.
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
        private String getAvailableIndexTable() {
            String indexTable = "";
            String str = metadataService.getValueByKeyInSchemaDoc(param.getTableName(), "index_table");
            String[] indexTables = StringUtil.split(str, ",");
            if (StringUtil.isEmpty(indexTables)) {
                return StringUtil.EMPTY;
            }
            for (String tempIndexTable : indexTables) {

                String indexKey = metadataService.getValueByKeyInSchemaDoc(tempIndexTable, "index_key");
                //判断一: where 是否包含索引键
                if (!param.getDbQuerySource().getExpr().contains(indexKey)) {
                    continue;
                }

                Map indexTableData = metadataService.getSchemaInfo("fields", tempIndexTable);
                Schema indexTableSchema = new Schema.Parser().parse(JsonMapper.toJsonString(indexTableData));
                //判断二: order by 元素是否都在索引表中
                if (!isValidOfOrderElementsInSchema(indexTableSchema)) {
                    continue;
                }

                Map masterTableData = metadataService.getSchemaInfo("fields", param.getTableName());
                Schema masterTableSchema = new Schema.Parser().parse(JsonMapper.toJsonString(masterTableData));
                //判断三: where 条件列是否都在索引表中
                setValidityByWhereExpressionsInSchema(param.getDbQuerySource().getWhereExpression(), masterTableSchema, indexTableSchema);
                if (getSubIndexValidFlag() && isExist(indexTableSchema.getFullName())) {
                    indexTable = indexTableSchema.getName();
                    break;
                }
            }
            return indexTable;
        }

        /**
         * 基于索引表方式添加子查询
         *
         * @param indexTable
         * @return
         */
        private String getSubQueryByIndexTable(String indexTable) {

            String primaryKey = metadataService.getValueByKeyInSchemaDoc(param.getTableName(), "primary_key");

            String subQuery = getSubquerySql(primaryKey, indexTable);
            StringBuffer sb = new StringBuffer();
            sb.append("select ").append(param.getDbQuerySource().getSelectItems()).append(" from ").append(param.getTableName()).append(" where ").append(primaryKey).append(" in ( ").append(subQuery).append(")");
            if (StringUtil.isNotBlank(param.getDbQuerySource().getExpr())) {
                sb.append(" and (").append(param.getDbQuerySource().getExpr()).append(")");
            }
            if (StringUtil.isNotBlank(param.getDbQuerySource().getOrderBy())) {
                sb.append(" order by ").append(param.getDbQuerySource().getOrderBy());
            }
            if (StringUtil.isNotBlank(param.getDbQuerySource().getLimit())) {
                sb.append(" limit ").append(param.getDbQuerySource().getLimit());
            }
            return sb.toString();
        }

        /**
         * 基于分区键方式
         *
         * @param sql
         * @return
         */
        private String getSubQueryByPartitionKey(String sql) {
            String partitionKey = getPartitionKey(param.getTableName());
            if (StringUtil.isBlank(partitionKey)) {
                log.warn("Partition Key is Empty. Focus in tableName {} schema", param.getTableName());
                return sql;
            } else {
                String subQuery = getSubquerySql(partitionKey, param.getTableName());
                subQuery = subQuery.replaceAll("\\\\","$0$0");
                subQuery = subQuery.replaceAll("\\$","\\\\\\$");
                Matcher m = pWhere.matcher(sql);
                StringBuffer sb = new StringBuffer();
                while (m.find()) {
                    m.appendReplacement(sb, "where  " + partitionKey + " in ( " + subQuery + ") and  ");
                }
                m.appendTail(sb);
                return sb.toString();
            }

        }

        /**
         * 获取子sql
         *
         * @param columnName
         * @param tableName
         * @return
         */
        private String getSubquerySql(String columnName, String tableName) {
            StringBuilder subQuery = new StringBuilder();

            subQuery.append("select " + columnName + " from ").append(tableName)
                    .append(" where ").append(param.getDbQuerySource().getExpr());

            //order by中元素存在别名时做替换处理
            List<OrderByElement> listOrderElement = param.getDbQuerySource().getListOrderElement();
            if (StringUtil.isNotEmpty(listOrderElement)) {
                String orderBy = param.getDbQuerySource().getOrderBy();
                for (OrderByElement order : listOrderElement) {
                    String orderField = order.getExpression().toString();
                    if (orderField.startsWith("\"") && orderField.endsWith("\"")) {
                        orderField = orderField.substring(1, orderField.length() - 1);
                    }
                    String originalField = param.getDbQuerySource().getAliasFields().get(orderField);
                    if (StringUtil.isNotBlank(originalField)) {
                        orderBy = orderBy.replaceAll("(\"" + orderField + "\")|(\\b" + orderField + "\\b)", originalField);
                    }

                }
                subQuery.append(" order by ").append(orderBy);
            }


            if (StringUtil.isNotBlank(param.getDbQuerySource().getLimit())) {
                String[] limits = param.getDbQuerySource().getLimit().split(",");
                if (limits.length == 2) {
                    int rows = Integer.valueOf(limits[0].trim()) + Integer.valueOf(limits[1].trim());
                    subQuery.append(" limit ").append(rows);
                } else {
                    subQuery.append(" limit ").append(param.getDbQuerySource().getLimit());
                }

            } else {
                subQuery.append(" limit 0, " + engineConfigSource.getDefaultResultNum());
            }
            return subQuery.toString();
        }

        /**
         * 获取当前CK查询中的分区键，用于子查询优化操作;
         * 若分区键被其它列当别名使用，返回空
         *
         * @return
         */
        private String getPartitionKey(String tableName) {
            String partitionKey = metadataService.getPartitionKey(tableName);

            if (StringUtil.isNotBlank(partitionKey)) {
                String tempValue = param.getDbQuerySource().getAliasFields().get(partitionKey);
                if (StringUtil.isNotBlank(tempValue)) {
                    if (tempValue.contains(partitionKey)) {
                        partitionKey = tempValue;
                    } else {
                        partitionKey = "";
                    }
                }

            }

            return partitionKey;

        }

        /**
         * 判断table是否在DB中
         *
         * @param tableName
         * @return
         */
        private boolean isExist(String tableName) {
            Map<String, String> result = executeHttpGet(String.format("select 1 from %s limit 1", tableName));
            if (result.get("status") == null || Integer.parseInt(result.get("status")) != HttpStatus.SC_OK) {
                log.warn("The table does not exist: {}", tableName);
                return false;
            }
            return true;
        }

        /**
         * 判断order by 元素是否属于schema字段中
         *
         * @param schema
         * @return
         */
        private boolean isValidOfOrderElementsInSchema(Schema schema) {
            List<OrderByElement> listOrderElement = param.getDbQuerySource().getListOrderElement();
            if (StringUtil.isEmpty(listOrderElement)) {
                return true;
            } else {
                Map<String, String> aliasFields = param.getDbQuerySource().getAliasFields();
                for (OrderByElement order : listOrderElement) {
                    String orderField = String.valueOf(order.getExpression());
                    if (orderField.startsWith("\"") && orderField.endsWith("\"")) {
                        orderField = orderField.substring(1, orderField.length() - 1);
                    }
                    String originalField = StringUtil.isBlank(aliasFields.get(orderField)) ? orderField : aliasFields.get(orderField);
                    Expression expression = parserException(originalField);
                    if (expression instanceof Function) {
                        if (!getValidityByFunctionParamsInSchema(schema, (Function) expression)) {
                            return false;
                        }
                    } else {
                        if (!isFieldOfSchema(schema, originalField)) {
                            return false;
                        }
                    }
                }
                return true;
            }
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

        /**
         * 判断where条件是否可以进行在索引表中的查询
         *
         * @param where
         * @param masterTableSchema 主表
         * @param indexTableSchema  索引表
         */
        private void setValidityByWhereExpressionsInSchema(Expression where, Schema masterTableSchema, Schema indexTableSchema) {

            if (StringUtil.isEmpty(masterTableSchema) || StringUtil.isEmpty(indexTableSchema)) {
                setSubIndexValidFlag(false);
                return;
            }

            if (where instanceof InExpression) {
                InExpression whereIn = (InExpression) where;
                ItemsList rightItemsList = whereIn.getRightItemsList();
                if (rightItemsList instanceof SubSelect) {
                    setSubIndexValidFlag(false);
                    return;
                }
                String left;
                if (!StringUtil.isEmpty(whereIn.getLeftExpression())) {
                    left = String.valueOf(whereIn.getLeftExpression());
                } else {
                    left = String.valueOf(whereIn.getLeftItemsList());
                }
                if (!isFieldOfSchema(masterTableSchema, left) || !isFieldOfSchema(indexTableSchema, left)) {
                    setSubIndexValidFlag(false);
                    return;
                }
            } else if (where instanceof AndExpression) {
                AndExpression whereAnd = (AndExpression) where;
                Expression leftExpression = whereAnd.getLeftExpression();
                Expression rightExpression = whereAnd.getRightExpression();
                setValidityByWhereExpressionsInSchema(leftExpression, masterTableSchema, indexTableSchema);
                setValidityByWhereExpressionsInSchema(rightExpression, masterTableSchema, indexTableSchema);
            } else if (where instanceof OrExpression) {
                OrExpression whereOr = (OrExpression) where;
                Expression leftExpression = whereOr.getLeftExpression();
                Expression rightExpression = whereOr.getRightExpression();
                setValidityByWhereExpressionsInSchema(leftExpression, masterTableSchema, indexTableSchema);
                setValidityByWhereExpressionsInSchema(rightExpression, masterTableSchema, indexTableSchema);
            } else if (where instanceof ComparisonOperator) {
                ComparisonOperator whereComparisonOperator = (ComparisonOperator) where;
                setValidityByLRExpressionInSchema(masterTableSchema, indexTableSchema, whereComparisonOperator.getLeftExpression(), whereComparisonOperator.getRightExpression());
            } else if (where instanceof LikeExpression) {
                LikeExpression whereLike = (LikeExpression) where;
                setValidityByLRExpressionInSchema(masterTableSchema, indexTableSchema, whereLike.getLeftExpression(), whereLike.getRightExpression());
            } else if (where instanceof Function) {
                Function function = (Function) where;
                boolean bool = getValidityByFunctionParamsInSchema(indexTableSchema, function);
                if (!bool) {
                    setSubIndexValidFlag(false);
                }
            } else if (where instanceof Parenthesis) {
                Parenthesis whereExpressionParenthesis = (Parenthesis) where;
                Expression expression = whereExpressionParenthesis.getExpression();
                setValidityByWhereExpressionsInSchema(expression, masterTableSchema, indexTableSchema);
            } else {
                setSubIndexValidFlag(false);
                return;
            }
        }

        private Expression parserException(String originalField) {
            try {
                return CCJSqlParserUtil.parseExpression(originalField);
            } catch (JSQLParserException e) {
                throw new BusinessException("parseExpression error: " + originalField);
            }
        }

        /**
         * 根据函数参数判断索引表查询是否有效: 只支持0或1(判断是否在schema中)个参数, 其它均设为无效
         *
         * @param schema
         * @param function
         */
        private boolean getValidityByFunctionParamsInSchema(Schema schema, Function function) {
            ExpressionList parameters = function.getParameters();
            if (StringUtil.isEmpty(parameters)) {
                return true;
            } else {
                List<Expression> expressions = parameters.getExpressions();
                if (expressions.size() != 1) {
                    return false;
                }
                if (!isFieldOfSchema(schema, String.valueOf(expressions.get(0)))) {
                    return false;
                }
                return true;
            }
        }

        /**
         * 根据左右表达式判断索引表查询是否有效
         *
         * @param masterTableSchema
         * @param indexTableSchema
         * @param leftExpression
         * @param rightExpression
         */
        private void setValidityByLRExpressionInSchema(Schema masterTableSchema, Schema indexTableSchema, Expression leftExpression, Expression rightExpression) {
            String leftStr = String.valueOf(leftExpression);
            String rightStr = String.valueOf(rightExpression);
            if (leftExpression instanceof Function && rightExpression instanceof Function) {
                setValidityByFunAndFunInSchema(masterTableSchema, indexTableSchema, (Function) leftExpression, (Function) rightExpression);
            } else if (!(leftExpression instanceof Function) && rightExpression instanceof Function) {
                setValidityByFunctionAndStrInSchema(masterTableSchema, indexTableSchema, (Function) rightExpression, leftStr);
            } else if (leftExpression instanceof Function && !(rightExpression instanceof Function)) {
                setValidityByFunctionAndStrInSchema(masterTableSchema, indexTableSchema, (Function) leftExpression, rightStr);
            } else {
                setValidityBuStrAndStrInSchema(masterTableSchema, indexTableSchema, leftStr, rightStr);
            }
        }

        /**
         * 操作符左右两边都是非函数类型(转String)
         *
         * @param masterTableSchema
         * @param indexTableSchema
         * @param leftStr
         * @param rightStr
         */
        private void setValidityBuStrAndStrInSchema(Schema masterTableSchema, Schema indexTableSchema, String leftStr, String rightStr) {
            if (isFieldOfSchema(masterTableSchema, leftStr) && !isFieldOfSchema(indexTableSchema, leftStr)) {
                setSubIndexValidFlag(false);
                return;
            }
            if (isFieldOfSchema(masterTableSchema, rightStr) && !isFieldOfSchema(indexTableSchema, rightStr)) {
                setSubIndexValidFlag(false);
                return;
            }
            if (!isFieldOfSchema(masterTableSchema, leftStr) && !isFieldOfSchema(masterTableSchema, rightStr)) {
                setSubIndexValidFlag(false);
                return;
            }
        }

        /**
         * 操作符左右两边都是函数
         *
         * @param masterTableSchema
         * @param indexTableSchema
         * @param leftExpression
         * @param rightExpression
         */
        private void setValidityByFunAndFunInSchema(Schema masterTableSchema, Schema indexTableSchema, Function leftExpression, Function rightExpression) {
            boolean validLeftInMaster = getValidityByFunctionParamsInSchema(masterTableSchema, leftExpression);
            boolean validLeftInIndex = getValidityByFunctionParamsInSchema(indexTableSchema, leftExpression);
            if (validLeftInMaster && !validLeftInIndex) {
                setSubIndexValidFlag(false);
                return;
            }
            boolean validRightInMaster = getValidityByFunctionParamsInSchema(masterTableSchema, rightExpression);
            boolean validRightInIndex = getValidityByFunctionParamsInSchema(indexTableSchema, rightExpression);
            if (validRightInMaster && !validRightInIndex) {
                setSubIndexValidFlag(false);
                return;
            }
            if (!validLeftInMaster && !validRightInMaster) {
                setSubIndexValidFlag(false);
                return;
            }
        }

        /**
         * 操作符左右一边是函数类型一边是非函数(转String)
         *
         * @param masterTableSchema
         * @param indexTableSchema
         * @param function
         * @param str
         */
        private void setValidityByFunctionAndStrInSchema(Schema masterTableSchema, Schema indexTableSchema, Function function, String str) {
            boolean validFunInMaster = getValidityByFunctionParamsInSchema(masterTableSchema, function);
            boolean validFunInIndex = getValidityByFunctionParamsInSchema(indexTableSchema, function);
            if (validFunInMaster && !validFunInIndex) {
                setSubIndexValidFlag(false);
                return;
            }
            if (isFieldOfSchema(masterTableSchema, str) && !isFieldOfSchema(indexTableSchema, str)) {
                setSubIndexValidFlag(false);
                return;
            }
            if (!isFieldOfSchema(masterTableSchema, str) && !validFunInMaster) {
                setSubIndexValidFlag(false);
                return;
            }
        }

        private void setSubIndexValidFlag(Boolean subIndexValidFlag) {
            this.subIndexValidFlag = subIndexValidFlag;
        }

        private Boolean getSubIndexValidFlag() {
            return subIndexValidFlag;
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

        Map<String, String> results = executeHttpGet(sql);

        if (Integer.parseInt(results.get("status")) == HttpStatus.SC_OK) {

            baseResult = BaseResultGenerator.generate(
                    Integer.parseInt(results.get("status")),
                    ResultCodeEnum.EXECUTE_SUCCESS.getCode(),
                    message.get(),
                    JsonMapper.fromJsonString(results.get("result"), Map.class),
                    null,
                    null,
                    QueryFormatEnum.JSON.getValue());
        } else {
            baseResult = BaseResultGenerator.generate(Integer.parseInt(results.get("status")), ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(), message.get(),
                    results.get("result"), null, null, param.getFormat());
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
            baseResult = BaseResultGenerator.failure((baseResult.getStatus() < 500) || (baseResult.getStatus() > 400) ? HttpStatus.SC_INTERNAL_SERVER_ERROR : baseResult.getStatus(),
                    ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(), simplyErrorMessage(dataObject));
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
        return StrUtil.removeAllLineBreaks("SQL Execution Error: ".concat(message)).trim();
    }

    private boolean containAggregateFunction(String sql) {
        return (pCount.matcher(sql).find()
                || pMax.matcher(sql).find()
                || pSUM.matcher(sql).find()
                || pMin.matcher(sql).find()
                || pAvg.matcher(sql).find());
    }

    private boolean containGroupBy(String sql) {
        return pGroupBy.matcher(sql).find();
    }



    /**
     * 做数据集限制:
     *  1. 将最内层sql的表名, 替换成(select * from tableName limit XXX) as alias
     *  2. 对where添加：OR 1=1
     * 涉及from 和 where中存在子查询
     *
     * @param sql
     * @return
     * @throws JSQLParserException
     */
    private SelectBody parserAndUpdateSql(String sql) throws JSQLParserException {
        Statement parse = CCJSqlParserUtil.parse(sql);
        if (parse instanceof Select) {
            Select select = (Select) parse;
            SelectBody selectBody = select.getSelectBody();
            if (selectBody instanceof PlainSelect) {
                PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
                FromItem fromItem = plainSelect.getFromItem();
                if (fromItem instanceof SubSelect) {
                    SubSelect subSelect = (SubSelect) plainSelect.getFromItem();
                    subSelect.setSelectBody(parserAndUpdateSql(subSelect.getSelectBody().toString()));
                } else if (fromItem instanceof Table) {
                    Table table = (Table) fromItem;
                    plainSelect.setFromItem(generateDataSetSql(table.getName(), table.getAlias() != null ? table.getAlias().getName() : table.getName()));
                }
                if (StringUtil.isNotEmpty(plainSelect.getWhere())) {
                    plainSelect.setWhere(generateDateSetWhere(plainSelect.getWhere().toString()));
                }
                parseWhereAndUpdate(plainSelect.getWhere());
                return plainSelect;
            } else if (selectBody instanceof SetOperationList) {
                SetOperationList setOperationList = (SetOperationList) selectBody;
                List<SelectBody> selects = setOperationList.getSelects();
                for (int i = 0; i < selects.size(); i++) {
                    selects.set(i, parserAndUpdateSql(selects.get(i).toString()));
                }
                return setOperationList;
            } else {
                throw new BusinessException("Only support selectBody operation: PlainSelect or SetOperationList");
            }
        }
        throw new BusinessException("Only support statement operation as select");
    }

    /**
     * 将where中子查询语句表名更新为数据集形式,类同from中表的替换
     *
     * @param whereExpression
     * @throws JSQLParserException
     */
    private void parseWhereAndUpdate(Expression whereExpression) throws JSQLParserException {
        if (whereExpression instanceof ComparisonOperator) {
            ComparisonOperator whereOperator = (ComparisonOperator) whereExpression;
            Expression left = whereOperator.getLeftExpression();
            setSelectBody(left);
            Expression right = whereOperator.getRightExpression();
            setSelectBody(right);
        } else if (whereExpression instanceof InExpression) {
            InExpression inExpression = (InExpression) whereExpression;
            Expression leftExpression = inExpression.getLeftExpression();
            setSelectBody(leftExpression);
            ItemsList rightItemsList = inExpression.getRightItemsList();
            if (rightItemsList instanceof SubSelect) {
                SubSelect subSelect = (SubSelect) rightItemsList;
                setSelectBody(subSelect);
            }
        } else if (whereExpression instanceof Between) {
            Between whereBetween = (Between) whereExpression;
            Expression betweenExpressionStart = whereBetween.getBetweenExpressionStart();
            setSelectBody(betweenExpressionStart);
            Expression betweenExpressionEnd = whereBetween.getBetweenExpressionEnd();
            setSelectBody(betweenExpressionEnd);
        } else if (whereExpression instanceof AndExpression) {
            AndExpression whereAnd = (AndExpression) whereExpression;
            parseWhereAndUpdate(whereAnd.getLeftExpression());
            parseWhereAndUpdate(whereAnd.getRightExpression());
        } else if (whereExpression instanceof OrExpression) {
            OrExpression whereOr = (OrExpression) whereExpression;
            parseWhereAndUpdate(whereOr.getLeftExpression());
            parseWhereAndUpdate(whereOr.getRightExpression());
        } else if (whereExpression instanceof Parenthesis) {
            Parenthesis whereExpressionParenthesis = (Parenthesis) whereExpression;
            Expression expression = whereExpressionParenthesis.getExpression();
            parseWhereAndUpdate(expression);
        }
    }

    private void setSelectBody(Expression expression) throws JSQLParserException {
        if (expression instanceof SubSelect) {
            SubSelect subSelect = (SubSelect) expression;
            SelectBody selectBody = subSelect.getSelectBody();
            subSelect.setSelectBody(parserAndUpdateSql(selectBody.toString()));
        }
    }

    /**
     * 生成数据集: 以FromItem对象形式的数据集表 sql,用来替换table
     *
     * @param tableName
     * @param alias
     * @return
     * @throws JSQLParserException
     */
    private FromItem generateDataSetSql(String tableName, String alias) throws JSQLParserException {
        String sql = String.format("SELECT 1 FROM (SELECT * FROM %s  LIMIT %s ) as %s ",
                tableName, 100, alias);
        Statement parse = CCJSqlParserUtil.parse(sql);
        Select select = (Select) parse;
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        return plainSelect.getFromItem();
    }

    private Expression generateDateSetWhere(String where) throws JSQLParserException {
        String sql = String.format("SELECT * FROM tableName WHERE  %s %s ",
                where, "OR 1=1");
        Statement parse = CCJSqlParserUtil.parse(sql);
        Select select = (Select) parse;
        PlainSelect selectBody = (PlainSelect) select.getSelectBody();
        return selectBody.getWhere();
    }

    /**
     * ClickHouse Http Query Interface
     *
     * @param sql SQL语句
     * @return
     */
    private Map<String, String> executeHttpGet(String sql) {
        String queryURL = URLUtil.normalize(clickHouseHttpSource.getUrl() + "/?");
        ClickHouseHttpQuery clickHouseHttpQuery = buildHttpQueryParameter();
        log.info("DB engine is :{},execute http query is :{}", DBTypeEnum.CLICKHOUSE.getValue(), queryURL +
                Encodes.urlDecode(clickHouseHttpQuery.getQueryParameter()).replaceAll("password=(.*?)&", "password=XXX&") + ". body sql is:" + sql);
        List<NameValuePair> values = URLEncodedUtils.parse(clickHouseHttpQuery.getQueryParameter(), Charset.forName("UTF-8"));
        return httpClientService.httpPost(queryURL + URLEncodedUtils.format(values, "utf-8"), sql.concat(" FORMAT ").concat(QueryFormatEnum.JSON.getValue().toUpperCase())
                , clickHouseHttpQuery.getSocketTimeOut());
    }

    private ClickHouseHttpQuery buildHttpQueryParameter() {
        ClickHouseHttpQuery clickHouseHttpQuery = new ClickHouseHttpQuery();
        StringBuilder queryParameterBuilder = new StringBuilder(1024);
        String dbName = metadataService.getDBNameByTableName(param.getTableName());
        if (dbName.equalsIgnoreCase(clickHouseHttpSource.getSystemDBName())) {
            clickHouseHttpQuery.setSocketTimeOut(httpConfig.getCkLongTermAccountSocketTimeOut());
            queryParameterBuilder.append(getLongTermAccount()).append("&")
                    .append("database=").append(dbName);
        } else if (QueryOptionEnum.LONG_TERM.getValue().equalsIgnoreCase(param.getOption())) {
            clickHouseHttpQuery.setSocketTimeOut(httpConfig.getCkLongTermAccountSocketTimeOut());
            queryParameterBuilder.append(getLongTermAccount()).append("&")
                    .append("database=").append(dbName).append("&")
                    .append("query_id=").append(SQLHelper.getQueryId(param.getResultId(), param.getQuery()));
        } else {
            clickHouseHttpQuery.setSocketTimeOut(httpConfig.getCkRealTimeAccountSocketTimeOut());
            queryParameterBuilder.append(getRealTimeAccount()).append("&")
                    .append("database=").append(dbName);
        }

        clickHouseHttpQuery.setQueryParameter(queryParameterBuilder.toString());
        return clickHouseHttpQuery;
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

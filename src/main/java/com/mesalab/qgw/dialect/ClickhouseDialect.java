package com.mesalab.qgw.dialect;


import com.google.common.collect.Maps;
import com.mesalab.calcite.CalciteMemoryUtils;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.*;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.model.api.udf.UDF;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.Encodes;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
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

import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ClickhouseDialect implements Dialect {
    Pattern pGlobalIn = Pattern.compile("\\s+in\\s*\\(", Pattern.CASE_INSENSITIVE);
    Pattern pUniq = Pattern.compile("count\\s*\\(\\s*distinct\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE);
    Pattern pUniqCombined = Pattern.compile("count\\s*\\(\\s*distinct\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE);
    Pattern pWhere = Pattern.compile("where\\s*", Pattern.CASE_INSENSITIVE);
    Pattern pCount = Pattern.compile("\\bcount\\s*\\(", Pattern.CASE_INSENSITIVE);
    Pattern pCount1 = Pattern.compile("count\\(\\s*1\\s*\\)", Pattern.CASE_INSENSITIVE);
    private ClickHouseHttpSource clickHouseHttpSource = (ClickHouseHttpSource) SpringContextUtil.getBean("clickHouseHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");
    private MetadataService metadataService = (MetadataService) SpringContextUtil.getBean("metadataService");
    private ApiParam param;
    private Map<String, String> results;
    private Boolean subIndexValidFlag = true;




    public ClickhouseDialect(ApiParam param) {
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
        if (offset > 0) {
            stringBuilder.append(offset).append(",").append(limit);
        } else {
            stringBuilder.append(limit);
        }
        return stringBuilder.toString();
    }


    /**
     * 默认分页处理
     * @param sql
     * @param sqlQuerySource
     * @return
     */
    private String generatePageSql(String sql, SQLQuerySource sqlQuerySource) {
        if (StringUtil.isBlank(sqlQuerySource.getLimit())) {
            return getLimitString(sql, 0, 200);
        } else {
            return sql;
        }
    }

    /**
     * http GET  sql 里特殊符号需要做encode编码
     * @param sql
     * @return
     */
    private String generateSqlEncode(String sql) {

        return Encodes.urlEncode(sql);

    }


    private void  setSubIndexValidFlag(Boolean subIndexValidFlag) {
        this.subIndexValidFlag = subIndexValidFlag;
    }

    private Boolean getSubIndexValidFlag() {
        return subIndexValidFlag;
    }

    /**
     * IN 转换为 集群 Global IN 模式
     * @param sql
     * @return
     */
    private String generateGlobalIn(String sql) {
        Matcher m = pGlobalIn.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, " global in (");
        }

        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * count(distinct())转为uniqCombined函数
     * @param sql
     * @return
     */
    private String generateUniqCombined(String sql) {

        Matcher m = pUniqCombined.matcher(sql);

        StringBuffer sb = new StringBuffer();

        while (m.find()) {

            if (m.groupCount() == 1) {
                String replaceValue = m.group(1);
                m.appendReplacement(sb, "uniqCombined(" + replaceValue);

            }
        }

        m.appendTail(sb);

        return sb.toString();


    }

    /**
     * count(distinct())转为uniq函数
     * @param sql
     * @return
     */
    private String generateUniq(String sql) {

        Matcher m = pUniq.matcher(sql);

        StringBuffer sb = new StringBuffer();

        while (m.find()) {

            if (m.groupCount() == 1) {
                String replaceValue = m.group(1);
                m.appendReplacement(sb, "uniq(" + replaceValue);

            }
        }

        m.appendTail(sb);

        return sb.toString();
    }

    /**
     * 事件索引子查询，目的提高日志查询检索性能:
     * 方式1.基于索引表
     * 方式2.基于分区键
     * 限制：
     * 1. 仅适用一级普通查询，带where （无group by 统计）的查询
     * 2. 不对count 统计进行生成
     * @param sql
     * @return
     */
    private String generateSubquery(String sql) {


        if (StringUtil.isBlank(param.getSqlQuerySource().getExpr())) {
            return sql;
        }

        if (StringUtil.isNotEmpty(param.getSqlQuerySource().getGroupByElement())) {
            return sql;
        }

        if (pCount.matcher(param.getSqlQuerySource().getSelectItems()).find()) {
            return sql;
        }

        String indexTable  = getAvaliableIndexTable();
        if (StringUtil.isNotBlank(indexTable)) {
            return getSubqueryByIndexTable(indexTable);
        } else {
            return getSubqueryByPartitionKey(sql);
        }
    }

    /**
     * 基于分区键方式
     * @param sql
     * @return
     */
    private String getSubqueryByPartitionKey(String sql) {
        String partitionKey = getPartitionKey(param.getTableName());
        if (StringUtil.isBlank(partitionKey)) {
            log.warn("Get Partition Key is Null.Focus in tableName {} schema", param.getTableName());
            return sql;
        } else {
            StringBuffer subQuery = getSubquerySql(partitionKey, param.getTableName());
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
     * 基于索引表方式添加子查询
     * @param indexTable
     * @return
     */
    private String getSubqueryByIndexTable(String indexTable) {

        String primaryKey = metadataService.getValueByKeyInSchemaDoc(param.getTableName(), "primary_key");

        StringBuffer subQuery = getSubquerySql(primaryKey, indexTable);
        StringBuffer sb = new StringBuffer();
        sb.append("select ").append(param.getSqlQuerySource().getSelectItems()).append(" from ").append(param.getTableName()).append(" where ").append(primaryKey).append(" in ( ").append(subQuery).append(")");
        if (StringUtil.isNotBlank(param.getSqlQuerySource().getOrderBy())) {
            sb.append(" order by ").append(param.getSqlQuerySource().getOrderBy());
        }
        if (StringUtil.isNotBlank(param.getSqlQuerySource().getLimit())) {
            sb.append(" limit ").append(param.getSqlQuerySource().getLimit());
        }
        return sb.toString();
    }

    /**
     * 获取子sql
     * @param columnName
     * @param tableName
     * @return
     */
    private StringBuffer getSubquerySql(String columnName, String tableName) {
        StringBuffer subQuery = new StringBuffer();

        subQuery.append("select " + columnName + " from ").append(tableName)
                .append(" where ").append(param.getSqlQuerySource().getExpr());

        //order by中元素存在别名时做替换处理
        List<OrderByElement> listOrderElement = param.getSqlQuerySource().getListOrderElement();
        if (StringUtil.isNotEmpty(listOrderElement)) {
            String orderBy = param.getSqlQuerySource().getOrderBy();
            for (OrderByElement order : listOrderElement) {
                String orderField = order.getExpression().toString();
                String originalField = param.getSqlQuerySource().getAliasFields().get(orderField);
                if(StringUtil.isNotBlank(originalField)){
                    orderBy = orderBy.replaceAll("\\b"+orderField+"\\b", originalField);
                }

            }
            subQuery.append(" order by ").append(orderBy);
        }


        if (StringUtil.isNotBlank(param.getSqlQuerySource().getLimit())) {
            String[] limits = param.getSqlQuerySource().getLimit().split(",");
            if (limits.length == 2) {
                int rows = Integer.valueOf(limits[0].trim()) + Integer.valueOf(limits[1].trim());
                subQuery.append(" limit ").append(rows);
            } else {
                subQuery.append(" limit ").append(param.getSqlQuerySource().getLimit());
            }

        } else {
            subQuery.append(" limit 0, 200");
        }
        return subQuery;
    }


    /**
     * 获取当前CK查询中的分区键，用于子查询优化操作;
     * 若分区键被其它列当别名使用，返回空
     * @return
     */
    private String getPartitionKey(String tableName) {
        String partitionKey = metadataService.getPartitionKey(tableName);

        if (StringUtil.isNotBlank(partitionKey)) {
            String tempValue = param.getSqlQuerySource().getAliasFields().get(partitionKey);
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


    @Override
    public String queryTransformation(String sql) {
        sql = generateStandard(sql);
        sql = SQLFunctionUtil.generateDateFunction(sql, DBTypeEnum.CLICKHOUSE.getValue());
        sql = generateSqlEncode(sql);
        return sql;
    }

    @Override
    public String queryOptimization(String sql) {
        sql = generateUniq(sql);
        sql = generateSubquery(sql);

        return sql;
    }

    @Override
    public Dialect queryDefaultPreProcess() {
        param.setQuery(generatePageSql(param.getQuery(), param.getSqlQuerySource()));
        return this;
    }


    @Override
    public Dialect executeGeneralQuery() {
        String sql = queryOptimization(param.getQuery());
        sql = queryTransformation(sql);
        results = executeHttpGet(sql, param.getFormat());
        return this;
    }

    @Override
    public Dialect executeSubQuery() {
        String sql = queryTransformation(param.getSqlQuerySource().getSubSelect().getSelectBody().toString());
        results = executeHttpGet(sql, param.getFormat());
        return this;
    }

    @Override
    public Dialect executeMemoryQuery() {
        if (!results.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            throw new BusinessException(BaseResultEnum.NOT_FOUND.getCode(), "query error, result: " + results.get("result"), null);
        }
        try {
            Map<String, Object> maps = (Map<String, Object>) JsonMapper.fromJsonString(String.valueOf(results.get("result")), Map.class);
            List<Map<String, Object>> data = (List<Map<String, Object>>) maps.get("data");
            List<Map<String, String>> meta = (List<Map<String, String>>) maps.get("meta");
            List list = CalciteMemoryUtils.calciteQueryMemoryData(meta, data, param);
            maps.put("data", list);
            results.put("result", JsonMapper.toJsonString(maps));
        } catch (Exception ex) {
            throw new BusinessException(BaseResultEnum.SERVER_ERROR.getCode(), "calcite execute query memory is error: ", ex);
        }
        return this;
    }


    @Override
    public Dialect executeSyntaxCheck() {
        String query;
        if (param.getSqlQuerySource().getNeedMemoryQuery()) {
            query = param.getSqlQuerySource().getSubSelect().getSelectBody().toString();
        } else {
            query = param.getQuery();
        }
        query = queryTransformation(query);
        query = Encodes.urlDecode(query);
        results = executeHttpGet("select 1", param.getFormat());
        results.put("message", query);
        log.info("option=syntax-check:  original sql: {}, after transformation(return message) info: {}", param.getQuery(), query);
        return this;
    }

    @Override
    public Dialect executeUDF(SQLQuerySource sqlQuerySource) {
        Set<UDF> udfSet = sqlQuerySource.getUdfSet();
        for (UDF udf : udfSet) {
            if (StringUtil.isNotEmpty(results)) {
                if (results.get("status").equals(String.valueOf(HttpStatus.SC_OK)) &&
                        param.getFormat().equalsIgnoreCase(QueryFormatEnum.JSON.getValue())) {

                    Map<String, Object> maps = (Map<String, Object>) JsonMapper.fromJsonString(results.get("result"), Map.class);

                    List<Object> lists = (List<Object>) maps.get("data");

                    List<Map<String, String>> targetResult = (List<Map<String, String>>) udf.execute(sqlQuerySource, lists);
                    maps.put("data", targetResult);
                    results.put("result", JsonMapper.toJsonString(maps));

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
                String message = "ok";
                if (StringUtil.isNotBlank(results.get("message"))) {
                    message = results.get("message");
                }

                if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.CSV.getValue())) {
                    baseResult = BaseResultGenerator.success(message, results.get("result"), null);
                } else if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.JSON.getValue())) {
                    Map<String, Object> maps =(Map<String, Object>)JsonMapper.fromJsonString(results.get("result"),  Map.class);
                    Map<String, Object> statisticMap = (Map<String, Object>)maps.get("statistics");
                    Map<String, Object> statistics = Maps.newHashMap();
                    statistics.put("elapsed", String.format("%.2f", (double)statisticMap.get("elapsed")));
                    statistics.put("rows_read", statisticMap.get("rows_read"));
                    int resultSize = StringUtil.isEmpty(maps.get("data")) ? 0 : JsonMapper.toJsonString(maps.get("data")).getBytes().length;
                    statistics.put("result_size", resultSize);
                    baseResult = BaseResultGenerator.success(message, maps.get("data"), statistics);
                }

            } else if (results.get("status").equals(String.valueOf(HttpStatus.SC_NOT_FOUND))) {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND, results.get("result"));
            } else if (results.get("status").equals(String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR))) {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, results.get("result"));
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, results.get("result"));
            }
        }
        return baseResult;
    }


    /**
     * 查询clickhouse: http get
     * @param sql
     * @param format
     * @return
     */
    private Map<String, String> executeHttpGet(String sql, String format) {
        StringBuilder urlBuilder = new StringBuilder("http://")
                .append(clickHouseHttpSource.getUrl()).append("/?");

        StringBuilder queryparamBuilder = new StringBuilder("user=")
                .append(clickHouseHttpSource.getUsername()).append("&")
                .append("password=").append(clickHouseHttpSource.getPassword()).append("&")
                .append("database=")
                .append(clickHouseHttpSource.getDbname()).append("&")
                .append("query=").append(sql)
                .append(" FORMAT ").append(format.toUpperCase()).append(" ;");

        log.info("Query Session and Events Query : {}", urlBuilder.toString() + Encodes.urlDecode(queryparamBuilder.toString()));

        List<NameValuePair> values = URLEncodedUtils.parse(queryparamBuilder.toString(), Charset.forName("UTF-8"));

        return httpClientService.httpGet(urlBuilder.toString() + URLEncodedUtils.format(values, "utf-8"));
    }


    /**
     * 兼容标准SQL语言（支持CK19.14.6.12 新特性）：
     * 1.子查询必须加表别名
     * 2.不支持 count(1) 需转为count(*)
     * @param sql
     * @return
     */
    private String generateStandard(String sql){
        sql = generateCount(sql);
        SelectBody body = generateSubqueryAlias(sql);
        if (StringUtil.isNotEmpty(body)) {
            sql = body.toString();
        }
        return sql;
    }

    /**
     * 集群不支持count(1) 需转成count(*)
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

    /**
     * clickhouse 集群模式下针对GLOBAL IN subquery 默认需要增加别名
     * @param sql
     * @return
     */
    private SelectBody generateSubqueryAlias(String sql) {

        SelectBody resultSelectBody = null;
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);

            if (statement instanceof Select) {

                Select selectStatement = (Select) statement;

                SelectBody body = selectStatement.getSelectBody();

                if (body instanceof PlainSelect) {

                    PlainSelect plainSelect = (PlainSelect) body;

                    //子查询处理
                    Expression where = plainSelect.getWhere();
                    if (where != null) {
                        setInSubqueryAlias(where);
                    }

                    resultSelectBody = plainSelect;

                } else if (body instanceof SetOperationList) {//连接查询
                    SetOperationList setOperationList = (SetOperationList) body;
                    List<SelectBody> selectsList = setOperationList.getSelects();
                    for (int i = 0; i < selectsList.size(); i++) {
                        if (selectsList.get(i) instanceof PlainSelect) {
                            SelectBody selectBody = generateSubqueryAlias(selectsList.get(i).toString());
                            setOperationList.getSelects().set(i,selectBody);
                        }

                    }

                    resultSelectBody =  setOperationList;

                }
            }

        } catch (JSQLParserException e) {
            log.error("sqlParser error: ", e);
            throw new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：SQL 解析错误", e);

        }

        return resultSelectBody;

    }

    /**
     * 子查询别名处理
     * @param where
     */
    private void setInSubqueryAlias(Expression where){
        if(where instanceof InExpression){//where in 查询处理
            InExpression whereIn = (InExpression) where;
            ItemsList rightItemsList = whereIn.getRightItemsList();
            if(rightItemsList instanceof SubSelect){
                SubSelect sub = (SubSelect) rightItemsList;
                SelectBody selectBody = sub.getSelectBody();
                PlainSelect plainSelect = (PlainSelect) selectBody;
                Table table = (Table) plainSelect.getFromItem();
                if (StringUtil.isEmpty(table.getAlias())) {
                    Alias alias = new Alias(table.getName());
                    plainSelect.getFromItem().setAlias(alias);
                }
                if (StringUtil.isNotEmpty(plainSelect.getWhere())) {
                    setInSubqueryAlias(plainSelect.getWhere());
                }

            }

        }else if(where instanceof AndExpression){
            AndExpression whereAnd = (AndExpression) where;
            Expression leftExpression = whereAnd.getLeftExpression();
            setInSubqueryAlias(leftExpression);
            Expression rightExpression = whereAnd.getRightExpression();
            setInSubqueryAlias(rightExpression);
        }else if(where instanceof OrExpression){
            OrExpression whereOr = (OrExpression) where;
            Expression leftExpression = whereOr.getLeftExpression();
            setInSubqueryAlias(leftExpression);
            Expression rightExpression = whereOr.getRightExpression();
            setInSubqueryAlias(rightExpression);
        }
    }
    /**
     * 设置索引表子查询参数: 索引表有效性、索引表表名
     * @return
     */
    private String getAvaliableIndexTable() {
        String indexTable = "";
        String str = metadataService.getValueByKeyInSchemaDoc(param.getTableName(), "index_table");
        String[] indexTables = StringUtil.split(str, ",");
        if (StringUtil.isEmpty(indexTables)) {
            return StringUtil.EMPTY;
        }
        for (String tempIndexTable : indexTables) {

            String indexKey = metadataService.getValueByKeyInSchemaDoc(tempIndexTable, "index_key");
            //判断一: where 是否包含索引键
            if (!param.getSqlQuerySource().getExpr().contains(indexKey)) {
                continue;
            }

            BaseResult indexTableData = metadataService.getSchemaInfo("fields", tempIndexTable);
            Schema indexTableSchema = new Schema.Parser().parse(JsonMapper.toJsonString(indexTableData.getData()));
            //判断二: order by 元素是否都在索引表中
            if (!isValidOfOrderElementsInSchema(indexTableSchema)) {
                continue;
            }

            BaseResult masterTableData = metadataService.getSchemaInfo("fields", param.getTableName());
            Schema masterTableSchema = new Schema.Parser().parse(JsonMapper.toJsonString(masterTableData.getData()));
            //判断三: where 条件列是否都在索引表中
            setValidityByWhereExpressionsInSchema(param.getSqlQuerySource().getWhereExpression(), masterTableSchema, indexTableSchema);
            if (getSubIndexValidFlag() && tableIsAvaliableInDB(indexTableSchema.getFullName())) {
                indexTable = indexTableSchema.getFullName();
                break;
            }
        }
        return indexTable;
    }

    /**
     * 判断table是否在DB中
     * @param tableName
     * @return
     */
    private boolean tableIsAvaliableInDB(String tableName) {
        Map<String, String> result = executeHttpGet(String.format("select 1 from %s limit 1", tableName), QueryFormatEnum.JSON.getValue());
        if (result.get("status") == null || Integer.parseInt(result.get("status")) != HttpStatus.SC_OK) {
            log.info("DB中不存在该表: {}", tableName);
            return false;
        }
        return true;
    }

    /**
     * 判断order by 元素是否属于schema字段中
     * @param schema
     * @return
     */
    private boolean isValidOfOrderElementsInSchema(Schema schema) {
        List<OrderByElement> listOrderElement = param.getSqlQuerySource().getListOrderElement();
        if (StringUtil.isEmpty(listOrderElement)) {
            return true;
        } else {
            Map<String, String> aliasFields = param.getSqlQuerySource().getAliasFields();
            for (OrderByElement order : listOrderElement) {
                String orderField = String.valueOf(order.getExpression());
                String originalField = aliasFields.get(orderField);
                if (StringUtil.isNotBlank(originalField) && !isFieldOfSchema(schema, originalField)) {
                    return false;
                }
                if (StringUtil.isBlank(originalField) && !isFieldOfSchema(schema, orderField)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 判断schema中是否存在field列
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
     * @param where
     * @param masterTableSchema 主表
     * @param indexTableSchema 索引表
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
            if(!bool){
                setSubIndexValidFlag(false);
            }
        } else {
            setSubIndexValidFlag(false);
            return;
        }
    }

    /**
     * 根据函数参数判断索引表查询是否有效: 只支持0或1(判断是否在schema中)个参数, 其它均设为无效
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
}

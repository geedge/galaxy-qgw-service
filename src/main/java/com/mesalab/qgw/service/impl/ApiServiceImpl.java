package com.mesalab.qgw.service.impl;

import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.druid.sql.ast.statement.*;
import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.base.SchemaBase;
import com.mesalab.common.constant.MessageConst;
import com.mesalab.common.enums.*;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.ReportCacheUtils;
import com.mesalab.common.utils.sqlparser.SQLFunctionUtil;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.common.utils.sqlparser.SQLSyntaxParserUtil;
import com.mesalab.qgw.dialect.Dialect;
import com.mesalab.qgw.dialect.FederationDialect;
import com.mesalab.qgw.model.api.*;
import com.mesalab.qgw.model.api.udf.UDF;
import com.mesalab.qgw.model.api.udf.UDFElements;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.qgw.service.SystemService;
import com.zdjizhi.utils.StringUtil;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.DescribeStatement;
import net.sf.jsqlparser.statement.ExplainStatement;
import net.sf.jsqlparser.statement.ShowStatement;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.apache.hbase.thirdparty.com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service("apiService")
public class ApiServiceImpl implements ApiService {
    private static final Log log = LogFactory.get();
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;
    @Autowired
    private DruidIoHttpSource druidIoHttpSource;
    @Autowired
    private HBaseAPISource hBaseAPISource;
    @Autowired
    private MetadataService metadataServiceTemp;
    @Autowired
    private SystemService systemServiceTemp;

    private static MetadataService metadataService;
    private static SystemService systemService;

    @PostConstruct
    private void initMetadataService() {
        metadataService = this.metadataServiceTemp;
        systemService = this.systemServiceTemp;
    }

    public static final int MAX_PARSER_LEVEL = 5;
    public static final String PARSER_EXCEPTION_CLASS_NAME = "net.sf.jsqlparser.parser.ParseException:";
    public static final String PARSER_EXCEPTION_IDENTIFICATION = "Was expecting one of:";
    public static final Pattern PATTERN_ANGLE_BRACKET = Pattern.compile("<(.*?)>", Pattern.CASE_INSENSITIVE);


    @Override
    public BaseResult executeQuery(ApiParam param) {
        validateAndSetDefaultValue(param);
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(param.getQuery());
        } catch (JSQLParserException e) {
            log.error("SQL Syntax Error: {}",e);
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(),
                    "SQL Syntax Error: " + simplyErrorMessage(e.getMessage()));
        }
        BaseResult baseResult;
        log.debug("Request option ：{}, SQL ：{}", param.getOption(), param.getQuery());
        if (statement instanceof Select) {
            baseResult = executeSelectStatement(param);
        } else if (statement instanceof DescribeStatement) {
            DescribeStatement describeStatement = (DescribeStatement) statement;
            baseResult = executeDescStatement(param, describeStatement);
        } else if (statement instanceof ShowStatement) {
            baseResult = executeShowStatement(param);
        } else if (statement instanceof ExplainStatement) {
            baseResult = executeExplainStatement(param);
        } else {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(),
                    "SQL Syntax Error: Only support statement operations for select, show and describe.", null);
        }
        return baseResult;
    }

    private void validateAndSetDefaultValue(ApiParam param) {
        if (StringUtil.isBlank(param.getQuery())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Param Syntax Error: The SQL must be exists.", null);
        }
        if (StringUtil.isBlank(param.getOption())) {
            param.setOption(QueryOptionEnum.REAL_TIME.getValue());
        }
        if (Arrays.stream(QueryOptionEnum.values()).noneMatch(o -> o.getValue().equalsIgnoreCase(param.getOption()))) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Param Syntax Error: The option must be real-time or long-term or syntax-validation. ", null);
        }
        if (QueryOptionEnum.LONG_TERM.getValue().equalsIgnoreCase(param.getOption())
                && StringUtil.isBlank(param.getReportId())
                && StringUtil.isBlank(param.getResultId())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Param Syntax Error: The option=long-term, must be reportId or resultId.", null);
        }
        if (StringUtil.isBlank(param.getFormat())) {
            param.setFormat(QueryFormatEnum.JSON.getValue());
        }
        if (Arrays.stream(QueryFormatEnum.values()).noneMatch(o -> o.getValue().equalsIgnoreCase(param.getFormat()))) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(),
                    "Param Syntax Error: The format must be json or csv.", null);
        }
    }

    /**
     * Desc: 简化JSQLParser语法异常信息
     *
     * @param message
     * @return {@link String}
     * @created by wWei
     * @date 2021/4/26 9:49 上午
     */
    private String simplyErrorMessage(String message) {
        if (StringUtil.isBlank(message)) {
            return StringUtil.EMPTY;
        }
        message = message.replace(PARSER_EXCEPTION_CLASS_NAME, StringUtil.EMPTY);
        int end = message.indexOf(PARSER_EXCEPTION_IDENTIFICATION);
        message = message.substring(0, end < 0 ? message.length() : end);
        message = StrUtil.removeAllLineBreaks(message);
        Matcher matcher = PATTERN_ANGLE_BRACKET.matcher(message);
        if (matcher.find()) {
            message = matcher.replaceAll(StringUtil.EMPTY);
        }
        String[] split = message.split(" ");
        StringBuilder stringBuilder = new StringBuilder(message.length());
        for (String c : split) {
            if (StringUtil.isBlank(c)) {
                continue;
            }
            stringBuilder.append(" ").append(c);
        }
        return stringBuilder.toString().trim();
    }

    /**
     * 1.设置默认queryID
     * 2.为Select操作选择不同的执行动作，包含SQL解析(Syntax-parse)、SQL验证（Syntax-validation）、实时查询(Real-time)、长查询(Long-term)
     * @param param 请求参数
     * @return BaseResult 执行结果
     */
    private BaseResult executeSelectStatement(ApiParam param) {
        if (QueryOptionEnum.SYNTAX_PARSE.getValue().equalsIgnoreCase(param.getOption())) {
            return executeSyntaxParse(param);
        } else if (QueryOptionEnum.SYNTAX_VALIDATION.getValue().equalsIgnoreCase(param.getOption())) {
            return executeSyntaxValidation(param);
        } else if (QueryOptionEnum.LONG_TERM.getValue().equalsIgnoreCase(param.getOption())) {
            param.setQueryId(systemService.getCustomQueryId(param.getResultId(), param.getQuery()));
            return executeLongTermQuery(param);
        } else {
            return executeRealTimeQuery(param);
        }

    }

    private BaseResult executeSyntaxParse(ApiParam param) {
       return BaseResultGenerator.success(SQLSyntaxParserUtil.syntaxParse(param.getQuery()));
    }

    private BaseResult executeSyntaxValidation(ApiParam param) {
        return new FederationDialect(param, getDBDialect(param).executeSyntaxCheck()).executeSyntaxCheck();
    }

    private BaseResult executeLongTermQuery(ApiParam param) {
        BaseResult baseResult = ReportCacheUtils.get(param.getResultId(), param.getQuery());
        if (baseResult.isSuccess()) {
            return baseResult;
        } else {
            log.info("Long-term Results not exist, execute real-time query: resultID-{}, sql-{}",
                    param.getResultId(), StringUtil.strip(param.getQuery()));
            return new FederationDialect(param, getDBDialect(param).executeQuery()).executeQuery();
        }
    }


    private BaseResult executeRealTimeQuery(ApiParam param) {
        if (BooleanUtil.isTrue(param.getSampled())) {
            return new FederationDialect(param, getDBDialect(param).executeSampleQuery()).executeSampleQuery();
        } else {
            return new FederationDialect(param, getDBDialect(param).executeQuery()).executeQuery();
        }
    }


    private Dialect getDBDialect(ApiParam param) {
        param.setTableName(SQLHelper.getTableName(param.getQuery()));
        param.setDbType(metadataService.getDBTypeByTableName(param.getTableName()));
        param.setDialectDBType(SQLHelper.getDialectDBType(param.getDbType()));
        convertQueryRecursive(param, parserSQLByAst(param.getQuery()));
        return getReflectiveDialectObject(param);
    }


    private Dialect getReflectiveDialectObject(ApiParam param) {
        Dialect dialect;
        try {
            Class dialectClazz = Class.forName("com.mesalab.qgw.dialect." +
                    CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, param.getDbType()) + "Dialect");
            Constructor constructor = dialectClazz.getConstructor(ApiParam.class);
            dialect = (Dialect) constructor.newInstance(param);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.error("Dialect conversion instance exception:{}",e);
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "Dialect conversion instance exception！", e);
        }
        return dialect;
    }





    /**
     * 递归解析SQLQuerySource ，拆分为数据库执行SQL与引擎执行的SQL source
     *
     * @param sqlQuerySource SQL解析对象
     */
    public static void convertQueryRecursive(ApiParam param, SQLQuerySource sqlQuerySource) {
        SQLQuerySource engineQuerySource = null;
        SQLQuerySource dbQuerySource = null;

        SQLQuerySource indexUpSqlQuerySource = null;
        SQLQuerySource indexSqlQuerySource = sqlQuerySource;
        for (int i = 0; i < MAX_PARSER_LEVEL; i++) {
            if (indexSqlQuerySource.getUdfSet().size() > 0) {
                dbQuerySource = indexSqlQuerySource;
                break;
            }
            if (StringUtil.isEmpty(indexSqlQuerySource.getSubSelect())) {
                dbQuerySource = sqlQuerySource;
                indexUpSqlQuerySource = null;
                break;
            } else {
                indexUpSqlQuerySource = indexSqlQuerySource;
                indexSqlQuerySource = indexSqlQuerySource.getSubSqlQuerySources().get(0);
                if (indexSqlQuerySource.getUdfSet().size() > 0) {
                    dbQuerySource = indexSqlQuerySource;
                    break;
                }
            }
        }

        if (StringUtil.isNotEmpty(indexUpSqlQuerySource)) {
            engineQuerySource = new SQLQuerySource();
            String tableName = StringUtil.isEmpty(indexUpSqlQuerySource.getSubSelect().getAlias()) ? indexUpSqlQuerySource.getTableName() : indexUpSqlQuerySource.getSubSelect().getAlias().getName();
            String replace = sqlQuerySource.getSqlBody().replace(indexUpSqlQuerySource.getSubSelect().toString(), tableName);
            engineQuerySource.setSqlBody(replace);
            engineQuerySource.setTableName(tableName);
            engineQuerySource.setLimit(sqlQuerySource.getLimit());
        }

        param.setDbQuerySource(dbQuerySource);
        param.setEngineQuerySource(engineQuerySource);
    }


    /**
     * 解析SQL
     *
     * @param sql
     * @return
     */
    public static SQLQuerySource parserSQLByAst(String sql) {


        SQLQuerySource sqlQuerySource = new SQLQuerySource();
        try {

            Statement statement = CCJSqlParserUtil.parse(sql);
            Select selectStatement = null;
            if (statement instanceof Select) {
                selectStatement = (Select) statement;
            } else if (statement instanceof ExplainStatement) {
                selectStatement = ((ExplainStatement) statement).getStatement();
            } else {
                log.error("Not support DML Parser");
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), MessageConst.SQL_PARSE_ONLY_SUPPORT_SELECT);
            }

            sqlQuerySource.setSqlBody(String.valueOf(selectStatement.getSelectBody()));
            TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
            List<String> tableList = tablesNamesFinder.getTableList(selectStatement);

            if (StringUtil.isNotEmpty(tableList)) {
                String tableName = tableList.get(0);
                int i = tableName.lastIndexOf(".");
                tableName = i < 0 ? tableName : tableName.substring(i + 1);
                sqlQuerySource.setTableName(tableName);
                sqlQuerySource.setPartitionKey(metadataService.getPartitionKey(tableName));
            } else {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), MessageConst.SQL_PARSE_ERROR_NOT_FOUNT_TABLE);
            }

            SelectBody body = selectStatement.getSelectBody();
            if (body instanceof PlainSelect) { //单条查询

                PlainSelect select = (PlainSelect) body;

                FromItem fromItem = select.getFromItem();
                if (fromItem != null) {
                    sqlQuerySource.setFromItem(fromItem.toString());
                }

                Expression where = select.getWhere();

                if (where != null) {
                    sqlQuerySource.setExpr(where.toString());
                    sqlQuerySource.setWhereExpression(where);
                }

                List<SelectItem> selectItemsList = select.getSelectItems();

                if (StringUtil.isNotEmpty(selectItemsList)) {
                    String selectItemsString = Joiner.on(",").join(selectItemsList);
                    sqlQuerySource.setSelectItems(selectItemsString);

                }

                for (SelectItem item : selectItemsList) {
                    if (item instanceof SelectExpressionItem) {
                        SelectExpressionItem expressionItem = ((SelectExpressionItem) item);
                        if (StringUtil.isNotEmpty(expressionItem.getAlias())) {
                            String aliasName = expressionItem.getAlias().getName();
                            if (aliasName.startsWith("\"") && aliasName.endsWith("\"")) {
                                aliasName = aliasName.substring(1, aliasName.length() - 1);
                            }
                            sqlQuerySource.getAliasFields().put(aliasName, expressionItem.getExpression().toString());
                        } else {
                            sqlQuerySource.getAliasFields().put(expressionItem.getExpression().toString(), expressionItem.getExpression().toString());
                        }
                        addUDFSet(sqlQuerySource.getUdfSet(), expressionItem.getExpression());//伪代码：后期需要在SQLQuerySource where对象中提取

                    }
                }

                GroupByElement groupBy;
                if (StringUtil.isNotEmpty(groupBy = select.getGroupBy())) {
                    sqlQuerySource.setGroupByElement(groupBy);
                }

                List<OrderByElement> orderByList = select.getOrderByElements();
                if (StringUtil.isNotEmpty(orderByList)) {
                    String orderByString = Joiner.on(",").join(orderByList);
                    sqlQuerySource.setOrderBy(orderByString);
                    sqlQuerySource.setListOrderElement(orderByList);
                }


                FromItem subItem = select.getFromItem();

                if (subItem instanceof SubSelect) {
                    SubSelect subSelect = (SubSelect) subItem;
                    log.debug("存在子查询为: {}", subSelect);
                    sqlQuerySource.setSubSelect(subSelect);
                    sqlQuerySource.getSubSqlQuerySources().add(0, parserSQLByAst(String.valueOf(subSelect.getSelectBody())));
                }


                Limit limit = select.getLimit();


                if (limit != null) {

                    if (StringUtil.isNotEmpty(limit.getOffset())) {
                        sqlQuerySource.setLimit(StringUtil.setDefaultIfEmpty(limit.getOffset(), 0) + "," + limit.getRowCount());
                    } else {
                        sqlQuerySource.setLimit(String.valueOf(limit.getRowCount()));
                    }
                }


            } else if (body instanceof SetOperationList) { // 连接查询
                SetOperationList setOperationList = (SetOperationList) body;
                List<SelectBody> selects = setOperationList.getSelects();
                //暂时只解析第一个结构，不接受不相同的where
                if (StringUtil.isNotEmpty(selects)) {
                    SQLQuerySource parseSql = parserSQLByAst(selects.get(0).toString());
                    sqlQuerySource.setUdfSet(parseSql.getUdfSet());
                    sqlQuerySource.setWhereExpression(parseSql.getWhereExpression());
                    sqlQuerySource.setGroupByElement(parseSql.getGroupByElement());
                    sqlQuerySource.setAliasFields(parseSql.getAliasFields());
                }
                sqlQuerySource.setLimit(SQLHelper.INVALID_LIMIT_DESC);
            } else { //其它暂不支持
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), MessageConst.SQL_PARSE_ONLY_SUPPORT_SELECT);
            }


        } catch (JSQLParserException | RuntimeException e) {
            log.error("sqlParser error: {}", e);
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "SQL Syntax Error:", e);

        }

        return sqlQuerySource;
    }


    /**
     * 自定义函数封装
     *
     * @param udfSet
     * @param expr
     */
    private static void addUDFSet(Set<UDF> udfSet, Expression expr) {
        parserExpressionForFun(udfSet, expr);
    }

    private static void parserExpressionForFun(Set<UDF> udfSet, Expression expr) {
        if (expr instanceof Function) {
            Function fun = (Function) expr;
            if (SQLFunctionUtil.functions.keySet().contains(fun.getName().toUpperCase())) {
                List<Expression> expressions = fun.getParameters().getExpressions();
                UDF udf = getUDF(fun, expressions);
                udfSet.add(udf);
            }
            if (fun.getParameters() == null) {
                return;
            }
            for (Expression expression : fun.getParameters().getExpressions()) {
                parserExpressionForFun(udfSet, expression);
            }
        } else if (expr instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expr;
            Expression leftExpression = binary.getLeftExpression();
            parserExpressionForFun(udfSet, leftExpression);
            Expression rightExpression = binary.getRightExpression();
            parserExpressionForFun(udfSet, rightExpression);
        }
    }

    /**
     * 获取自定义函数类
     *
     * @param fun
     * @param expressions
     * @return
     */
    private static UDF getUDF(Function fun, List<Expression> expressions) {
        UDF udf;
        String funName = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_UNDERSCORE, fun.getName());
        try {
            Class dialectClazz = Class.forName("com.mesalab.qgw.model.api.udf." + CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_UNDERSCORE, fun.getName().toUpperCase()));
            Constructor constructor = dialectClazz.getConstructor(UDFElements.class);
            UDFElements udfElements = new UDFElements(funName, expressions);
            udf = (UDF) constructor.newInstance(udfElements);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.error("Custom function conversion instance exception:{}",e);
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "Custom function conversion instance exception！", e);
        }
        return udf;
    }

    private String getMultiSqlTableName(SQLSelectQuery sqlSubSelectQuery) {
        SQLTableSource subTable = ((SQLSelectQueryBlock) sqlSubSelectQuery).getFrom();
        if (subTable instanceof SQLExprTableSource) {
            return ((SQLExprTableSource) subTable).getName().getSimpleName();
        } else {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "SQL Syntax Error：Multi-level nested subqueries are not supported yet.");
        }
    }


    /**
     * 执行describe语句
     *
     * @param param
     * @param statement
     * @return
     */
    private BaseResult executeDescStatement(ApiParam param, DescribeStatement statement) {
        String tableName = statement == null ? param.getTableName() : statement.getTable().getName();
        param.setTableName(tableName);
        String dbType = metadataService.getDBTypeByTableName(param.getTableName());

        if (DBTypeEnum.CLICKHOUSE.getValue().equalsIgnoreCase(dbType)) {
            param.setDbType(DBTypeEnum.CLICKHOUSE.getValue());
            param.setDialectDBType(DBTypeEnum.CLICKHOUSE.getValue());
        } else if (DBTypeEnum.DRUID.getValue().equalsIgnoreCase(dbType)) {
            param.setDbType(DBTypeEnum.DRUID.getValue());
            param.setDialectDBType(DBTypeEnum.DRUID.getValue());
            param.setQuery("SELECT COLUMN_NAME as name, DATA_TYPE as type FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + tableName + "'");
        } else if (DBTypeEnum.ELASTICSEARCH.getValue().equalsIgnoreCase(dbType)) {
            param.setDbType(DBTypeEnum.ELASTICSEARCH.getValue());
            param.setDialectDBType(DBTypeEnum.ELASTICSEARCH.getValue());
        } else if (DBTypeEnum.HBASE.getValue().equalsIgnoreCase(dbType)) {
            param.setDbType(DBTypeEnum.HBASE.getValue());
            param.setDialectDBType(DBTypeEnum.HBASE.getValue());
            param.setQuery(StrFormatter.format("SELECT COLUMN_NAME as \"name\", DATA_TYPE as \"type\" FROM SYSTEM.CATALOG WHERE TABLE_NAME= '{}' AND COLUMN_NAME  IS NOT NULL", tableName));
        } else {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(),
                    param.getTableName() + " The form is not registered on the platform, please contact the person in charge of the data platform", null);
        }

        param.setFormat(QueryFormatEnum.JSON.getValue());
        SQLQuerySource dbQuerySource = new SQLQuerySource();
        dbQuerySource.setSqlBody(param.getQuery());
        param.setDbQuerySource(dbQuerySource);
        Dialect dialect = getReflectiveDialectObject(param);
        BaseResult baseResult = dataEncapsulationOfSchema(dialect.executeAdministrativeQuery(), param);

        if (!baseResult.isSuccess()) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), "describe table error, please check tableName", null);
        }
        return baseResult;
    }

    private BaseResult executeExplainStatement(ApiParam param) {
        return getDBDialect(param).executeExplainPlan();
    }

    /**
     * 执行show语句
     *
     * @param param
     * @return
     */
    private BaseResult executeShowStatement(ApiParam param) {
        return getAllDatabaseTables(param);
    }


    /**
     * 获取所有表信息
     *
     * @return
     */
    private BaseResult getAllDatabaseTables(ApiParam apiParam) {
        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("type", "enum");
        Arrays.stream(DBTypeEnum.values()).forEach(dbTypeEnum -> {
            ApiParam dbParam = new ApiParam();
            if (dbTypeEnum.getValue().equalsIgnoreCase(DBTypeEnum.CLICKHOUSE.getValue())) {
                dbParam.setQuery("show tables");
                dbParam.setDbType(DBTypeEnum.CLICKHOUSE.getValue());
                dbParam.setDialectDBType(SQLHelper.getDialectDBType(DBTypeEnum.CLICKHOUSE.getValue()));
            } else if (dbTypeEnum.getValue().equalsIgnoreCase(DBTypeEnum.DRUID.getValue())) {
                dbParam.setQuery("SELECT TABLE_NAME AS name FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'TABLE'");
                dbParam.setDbType(DBTypeEnum.DRUID.getValue());
                dbParam.setDialectDBType(SQLHelper.getDialectDBType(DBTypeEnum.DRUID.getValue()));
            } else {
                return;
            }
            dbParam.setFormat(QueryFormatEnum.JSON.getValue());
            Dialect dialect = getReflectiveDialectObject(dbParam);
            BaseResult queryResult = dialect.executeAdministrativeQuery();
            if (queryResult.getStatus() == null || queryResult.getStatus() != ResultStatusEnum.SUCCESS.getCode()) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), queryResult.getMessage(), null);
            }
            List tables = (ArrayList) queryResult.getData();
            List<String> list = new ArrayList<>();
            Iterator iterator = tables.iterator();
            while (iterator.hasNext()) {
                Map<String, String> next = (Map) iterator.next();
                list.add(next.get("name"));
            }
            BaseResult result = encapsulationShowTables(list, dbParam.getDbType());

            if (result.getStatus() != null && result.getStatus() == ResultStatusEnum.SUCCESS.getCode()) {
                Map dbResultMap = (Map) result.getData();

                if (StringUtil.isEmpty(dataMap.get("name"))) {
                    dataMap.put("name", dbResultMap.get("name"));
                } else {
                    dataMap.put("name", Joiner.on(",").join(dataMap.get("name"),dbResultMap.get("name")));
                }

                if (StringUtil.isEmpty(dataMap.get("symbols"))) {
                    dataMap.put("symbols", dbResultMap.get("symbols"));
                } else {
                    dataMap.put("symbols",  Lists.newArrayList(dataMap.get("symbols"), dbResultMap.get("symbols")));
                }
            }


        });

        return BaseResultGenerator.success("ok", dataMap);
    }


    /**
     * 表描述信息结果封装
     *
     * @param result
     * @param param
     * @return
     */
    public BaseResult dataEncapsulationOfSchema(BaseResult result, ApiParam param) {
        SchemaBase schema = new SchemaBase();
        schema.setName(param.getTableName());
        List<Map> fields = new ArrayList<>();
        List<Map> list = (List<Map>) result.getData();
        for (Map resultMap : list) {
            Map schemaMap = Maps.newHashMap();
            schemaMap.put("name", DBTypeEnum.ELASTICSEARCH.getValue().equals(param.getDbType()) ? resultMap.get("column") : resultMap.get("name"));
            schemaMap.put("type", resultMap.get("type"));
            fields.add(schemaMap);
        }
        schema.setFields(fields);
        result.setData(schema);
        return result;
    }

    /**
     * 库中包含表表名查询结果处理
     *
     * @param tables
     * @return
     */
    private BaseResult encapsulationShowTables(List tables, String database) {
        List<String> list = new ArrayList<>();
        Map<String, Object> date = new LinkedHashMap<>();
        date.put("type", "enum");
        date.put("name", database);
        Iterator iterator = tables.iterator();
        while (iterator.hasNext()) {
            list.add(String.valueOf(iterator.next()));
        }
        date.put("symbols", list);
        BaseResult<Map> baseResult = BaseResultGenerator.success("ok", null);
        baseResult.setData(date);
        return baseResult;
    }

}

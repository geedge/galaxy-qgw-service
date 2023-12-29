package com.mesalab.qgw.service.impl;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.entity.SchemaBase;
import com.mesalab.common.exception.QGWMessageConst;
import com.mesalab.common.enums.*;
import com.mesalab.common.utils.sqlparser.SQLFunctionUtil;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.common.utils.sqlparser.SQLSyntaxParserUtil;
import com.mesalab.qgw.dialect.Dialect;
import com.mesalab.qgw.dialect.FederationDialect;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.model.basic.udf.UDF;
import com.mesalab.qgw.model.basic.udf.UDFElements;
import com.mesalab.qgw.service.SQLSyncQueryService;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.qgw.service.SystemService;
import com.geedgenetworks.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
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
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service("sqlSyncQueryService")
@Slf4j
public class SQLSyncQueryServiceImpl implements SQLSyncQueryService {
    private DatabaseService databaseService;
    private SystemService systemService;

    @Autowired
    public void SQLSyncQueryServiceImpl(DatabaseService databaseService, SystemService systemService) {
        this.databaseService = databaseService;
        this.systemService = systemService;
    }

    private final int MAX_PARSER_LEVEL = 5;
    private final String PARSER_EXCEPTION_CLASS_NAME = "net.sf.jsqlparser.parser.ParseException:";
    private final String PARSER_EXCEPTION_IDENTIFICATION = "Was expecting one of:";
    private final Pattern PATTERN_ANGLE_BRACKET = Pattern.compile("<(.*?)>", Pattern.CASE_INSENSITIVE);


    @Override
    public BaseResult executeQuery(SQLQueryContext queryContext) {
        checkQueryContextParam(queryContext);
        parseAndSetConnectorType(queryContext);
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(queryContext.getRawSQL());
        } catch (JSQLParserException e) {
            log.error("Error Parsing SQL: {}", ExceptionUtil.getRootCauseMessage(e));
            String message = getJSQLParserSimpleErrorMessage(e);
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getMessage(), message));
        }
        BaseResult baseResult;
        log.debug("Request SQL Statement: {}", queryContext.getRawSQL());
        if (statement instanceof Select) {
            baseResult = executeSelectStatement(queryContext);
        } else if (statement instanceof DescribeStatement) {
            DescribeStatement describeStatement = (DescribeStatement) statement;
            baseResult = executeDescStatement(queryContext, describeStatement);
        } else if (statement instanceof ShowStatement) {
            baseResult = executeShowStatement(queryContext);
        } else if (statement instanceof ExplainStatement) {
            baseResult = executeExplainStatement(queryContext);
        } else {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getMessage(), QGWMessageConst.ONLY_SUPPORT_STATEMENT_OPERATIONS_FOR_SELECT));
        }
        return baseResult;
    }

    private void parseAndSetConnectorType(SQLQueryContext queryContext) {
        if (SQLHelper.getTableName(queryContext.getRawSQL()).isEmpty()) {
            return;
        }
        String tableName = SQLHelper.getTableName(queryContext.getRawSQL()).get(0);
        queryContext.setDbEngine(databaseService.getDBEngineByTableName(tableName));
        queryContext.setSqlDialect(SQLHelper.getDialectType(queryContext.getDbEngine()));
    }

    private void checkQueryContextParam(SQLQueryContext queryContext) {
        if (StringUtil.isBlank(queryContext.getOption())) {
            queryContext.setOption(QueryOption.REAL_TIME.getValue());
        }
        if (StringUtil.isBlank(queryContext.getFormat())) {
            queryContext.setFormat(OutputMode.JSON.getValue());
        }
        if (StringUtil.isBlank(queryContext.getRawSQL())) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.SQL_MUST_BE_EXISTS));
        }
        if (Arrays.stream(QueryOption.values()).noneMatch(o -> o.getValue().equalsIgnoreCase(queryContext.getOption()))) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.OPTION_TYPE_ERROR));
        }
        if (Arrays.stream(OutputMode.values()).noneMatch(o -> o.getValue().equalsIgnoreCase(queryContext.getFormat()))) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.FORMAT_TYPE_ERROR));
        }
    }


    /**
     * Desc: 简化JSQLParser语法异常信息
     *
     * @param e
     * @return {@link String}
     * @created by wWei
     * @date 2021/4/26 9:49 上午
     */
    private String getJSQLParserSimpleErrorMessage(Throwable e) {
        String message = Joiner.on(" ").skipNulls().join(ExceptionUtil.getSimpleMessage(e), ExceptionUtil.getRootCauseMessage(e));
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
     *
     * @param queryContext 请求参数
     * @return BaseResult 执行结果
     */
    private BaseResult executeSelectStatement(SQLQueryContext queryContext) {
        if (QueryOption.SYNTAX_PARSE.getValue().equalsIgnoreCase(queryContext.getOption())) {
            return executeSyntaxParse(queryContext);
        } else if (QueryOption.SYNTAX_VALIDATION.getValue().equalsIgnoreCase(queryContext.getOption())) {
            return executeSyntaxValidation(queryContext);
        } else if (QueryOption.LONG_TERM.getValue().equalsIgnoreCase(queryContext.getOption())) {
            queryContext.setQueryId(systemService.getCustomQueryId(queryContext.getResultId(), queryContext.getRawSQL()));
            return executeLongTermQuery(queryContext);
        } else {
            return executeRealTimeQuery(queryContext);
        }

    }

    private BaseResult executeSyntaxParse(SQLQueryContext param) {
        return BaseResultGenerator.success(SQLSyntaxParserUtil.syntaxParse(param.getRawSQL()));
    }

    private BaseResult executeSyntaxValidation(SQLQueryContext queryContext) {
        return new FederationDialect(queryContext, getDialect(queryContext).executeSyntaxCheck()).executeSyntaxCheck();
    }

    private BaseResult executeLongTermQuery(SQLQueryContext param) {
        return new FederationDialect(param, getDialect(param).executeQuery()).executeQuery();
    }


    private BaseResult executeRealTimeQuery(SQLQueryContext queryContext) {
        if (BooleanUtil.isTrue(queryContext.getSampled())) {
            return new FederationDialect(queryContext, getDialect(queryContext).executeSampleQuery()).executeSampleQuery();
        } else {
            return new FederationDialect(queryContext, getDialect(queryContext).executeQuery()).executeQuery();
        }
    }


    private Dialect getDialect(SQLQueryContext queryContext) {
        convertQueryRecursive(queryContext, parserSQLByAst(queryContext.getRawSQL()));
        return getReflectiveDialectObject(queryContext);
    }


    private Dialect getReflectiveDialectObject(SQLQueryContext queryContext) {
        Dialect dialect;
        try {
            Class dialectClazz = Class.forName("com.mesalab.qgw.dialect." +
                    CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, queryContext.getDbEngine()) + "Dialect");
            Constructor constructor = dialectClazz.getConstructor(SQLQueryContext.class);
            dialect = (Dialect) constructor.newInstance(queryContext);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.error("Dialect conversion instance exception:{}", e);
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getMessage(), e.getMessage()));
        }
        return dialect;
    }


    /**
     * 递归解析SQLQuerySource ，拆分为数据库执行SQL与引擎执行的SQL source
     *
     * @param sqlQuerySource SQL解析对象
     */
    public void convertQueryRecursive(SQLQueryContext param, SQLQuerySource sqlQuerySource) {
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
            String tableName = StringUtil.isEmpty(indexUpSqlQuerySource.getSubSelect().getAlias()) ? indexUpSqlQuerySource.getTableNames().get(0) : indexUpSqlQuerySource.getSubSelect().getAlias().getName();
            String replace = sqlQuerySource.getSqlBody().replace(indexUpSqlQuerySource.getSubSelect().toString(), tableName);
            engineQuerySource.setSqlBody(replace);
            engineQuerySource.getTableNames().add(0, tableName);
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
    private SQLQuerySource parserSQLByAst(String sql) {
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
                throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getMessage(), QGWMessageConst.SQL_PARSE_ONLY_SUPPORT_SELECT));
            }

            sqlQuerySource.setSqlBody(String.valueOf(selectStatement.getSelectBody()));
            TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
            List<String> tableList = tablesNamesFinder.getTableList(selectStatement);
            tableList.forEach(o -> {
                int i = o.lastIndexOf(".");
                sqlQuerySource.getTableNames().add(i < 0 ? o : o.substring(i + 1));
            });
            sqlQuerySource.setPartitionKey(databaseService.getPartitionKey(sqlQuerySource.getTableNames().get(0)));
            SelectBody body = selectStatement.getSelectBody();
            if (body instanceof PlainSelect) { //单条查询

                PlainSelect select = (PlainSelect) body;

                FromItem fromItem = select.getFromItem();
                sqlQuerySource.setFromItem(fromItem);
                sqlQuerySource.setJoins(select.getJoins());
                Expression where = select.getWhere();

                if (where != null) {
                    sqlQuerySource.setWhereExpression(where);
                }

                List<SelectItem> selectItemsList = select.getSelectItems();
                sqlQuerySource.setSelectItems(selectItemsList);

                for (SelectItem item : selectItemsList) {
                    if (item instanceof SelectExpressionItem) {
                        SelectExpressionItem expressionItem = ((SelectExpressionItem) item);
                        if (StringUtil.isNotEmpty(expressionItem.getAlias())) {
                            String aliasName = expressionItem.getAlias().getName();
                            if ((aliasName.startsWith("\"") && aliasName.endsWith("\""))
                                    || ((aliasName.startsWith("`") && aliasName.endsWith("`")))) {
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
                sqlQuerySource.setOrderByElements(select.getOrderByElements());
                FromItem subItem = select.getFromItem();

                if (subItem instanceof SubSelect) {
                    SubSelect subSelect = (SubSelect) subItem;
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
                throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                        String.format(CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getMessage(), QGWMessageConst.SQL_PARSE_ONLY_SUPPORT_SELECT));
            }


        } catch (JSQLParserException | RuntimeException e) {
            log.error("sqlParser error: {}", e);
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getMessage(), e.getMessage()));

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
            Class dialectClazz = Class.forName("com.mesalab.qgw.model.basic.udf." + CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_UNDERSCORE, fun.getName().toUpperCase()));
            Constructor constructor = dialectClazz.getConstructor(UDFElements.class);
            UDFElements udfElements = new UDFElements(funName, expressions);
            udf = (UDF) constructor.newInstance(udfElements);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.error("Custom function conversion instance exception:{}", e);
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getMessage(), QGWMessageConst.CUSTOM_FUNCTION_CONVERSION_INSTANCE_EXCEPTION));
        }
        return udf;
    }


    /**
     * 执行describe语句
     *
     * @param queryContext
     * @param statement
     * @return
     */
    private BaseResult executeDescStatement(SQLQueryContext queryContext, DescribeStatement statement) {
        String tableName = statement.getTable().getName();
        String dbEngineType = queryContext.getDbEngine();
        if (DBEngineType.CLICKHOUSE.getValue().equalsIgnoreCase(dbEngineType)) {
            queryContext.setDbEngine(DBEngineType.CLICKHOUSE.getValue());
            queryContext.setSqlDialect(DBEngineType.CLICKHOUSE.getValue());
        } else if (DBEngineType.DRUID.getValue().equalsIgnoreCase(dbEngineType)) {
            queryContext.setDbEngine(DBEngineType.DRUID.getValue());
            queryContext.setSqlDialect(DBEngineType.DRUID.getValue());
            queryContext.setRawSQL("SELECT COLUMN_NAME as name, DATA_TYPE as type FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + tableName + "'");
        } else if (DBEngineType.HBASE.getValue().equalsIgnoreCase(dbEngineType)) {
            queryContext.setDbEngine(DBEngineType.HBASE.getValue());
            queryContext.setSqlDialect(DBEngineType.HBASE.getValue());
            queryContext.setRawSQL(StrFormatter.format("SELECT COLUMN_NAME as \"name\", DATA_TYPE as \"type\" FROM SYSTEM.CATALOG WHERE TABLE_NAME= '{}' AND COLUMN_NAME  IS NOT NULL", tableName));
        } else {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getMessage(),
                            tableName + ":" + QGWMessageConst.TABLE_NOT_EXIST));
        }

        queryContext.setFormat(OutputMode.JSON.getValue());
        SQLQuerySource dbQuerySource = new SQLQuerySource();
        dbQuerySource.setSqlBody(queryContext.getRawSQL());
        queryContext.setDbQuerySource(dbQuerySource);
        Dialect dialect = getReflectiveDialectObject(queryContext);
        BaseResult baseResult = dataEncapsulationOfSchema(dialect.executeAdministrativeQuery(), queryContext);

        if (!baseResult.isSuccess()) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.CHECK_TABLE));
        }
        return baseResult;
    }

    private BaseResult executeExplainStatement(SQLQueryContext param) {
        return getDialect(param).executeExplainPlan();
    }

    /**
     * 执行show语句
     *
     * @param param
     * @return
     */
    private BaseResult executeShowStatement(SQLQueryContext param) {
        return getAllDatabaseTables(param);
    }


    /**
     * 获取所有表信息
     *
     * @return
     */
    private BaseResult getAllDatabaseTables(SQLQueryContext queryContext) {
        Map<String, Object> dataMap = new LinkedHashMap<>();
        dataMap.put("type", "enum");
        Arrays.stream(DBEngineType.values()).forEach(dbTypeEnum -> {
            SQLQueryContext dbParam = new SQLQueryContext();
            if (dbTypeEnum.getValue().equalsIgnoreCase(DBEngineType.CLICKHOUSE.getValue())) {
                dbParam.setRawSQL("show tables");
                dbParam.setDbEngine(DBEngineType.CLICKHOUSE.getValue());
                dbParam.setSqlDialect(SQLHelper.getDialectType(DBEngineType.CLICKHOUSE.getValue()));
            } else if (dbTypeEnum.getValue().equalsIgnoreCase(DBEngineType.DRUID.getValue())) {
                dbParam.setRawSQL("SELECT TABLE_NAME AS name FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'TABLE'");
                dbParam.setDbEngine(DBEngineType.DRUID.getValue());
                dbParam.setSqlDialect(SQLHelper.getDialectType(DBEngineType.DRUID.getValue()));
            } else {
                return;
            }
            dbParam.setFormat(OutputMode.JSON.getValue());
            Dialect dialect = getReflectiveDialectObject(dbParam);
            BaseResult queryResult = dialect.executeAdministrativeQuery();
            if (queryResult.getStatus() == null || queryResult.getStatus() != HttpStatusCodeEnum.SUCCESS.getCode()) {
                throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), queryResult.getMessage());
            }
            List tables = (ArrayList) queryResult.getData();
            List<String> list = new ArrayList<>();
            Iterator iterator = tables.iterator();
            while (iterator.hasNext()) {
                Map<String, String> next = (Map) iterator.next();
                list.add(next.get("name"));
            }
            BaseResult result = encapsulationShowTables(list, dbParam.getDbEngine());

            if (result.getStatus() != null && result.getStatus() == HttpStatusCodeEnum.SUCCESS.getCode()) {
                Map dbResultMap = (Map) result.getData();

                if (StringUtil.isEmpty(dataMap.get("name"))) {
                    dataMap.put("name", dbResultMap.get("name"));
                } else {
                    dataMap.put("name", Joiner.on(",").join(dataMap.get("name"), dbResultMap.get("name")));
                }

                if (StringUtil.isEmpty(dataMap.get("symbols"))) {
                    dataMap.put("symbols", dbResultMap.get("symbols"));
                } else {
                    dataMap.put("symbols", Lists.newArrayList(dataMap.get("symbols"), dbResultMap.get("symbols")));
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
    public BaseResult dataEncapsulationOfSchema(BaseResult result, SQLQueryContext param) {
        SchemaBase schema = new SchemaBase();
        schema.setName(SQLHelper.getTableName(param.getRawSQL()).get(0));
        List<Map> fields = new ArrayList<>();
        List<Map> list = (List<Map>) result.getData();
        for (Map resultMap : list) {
            Map schemaMap = Maps.newHashMap();
            schemaMap.put("name", resultMap.get("name"));
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

package com.mesalab.qgw.service.impl;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLLimit;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOperator;
import com.alibaba.druid.sql.ast.expr.SQLInSubQueryExpr;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;
import com.alibaba.druid.util.JdbcConstants;
import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.base.SchemaBase;
import com.mesalab.common.configuration.ClickHouseProperties;
import com.mesalab.common.enums.*;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.ReportCacheUtils;
import com.mesalab.common.utils.SQLFunctionUtil;
import com.mesalab.common.utils.SQLHelper;
import com.mesalab.common.utils.SQLSyntaxParserUtil;
import com.mesalab.qgw.dialect.Dialect;
import com.mesalab.qgw.dialect.FederationDialect;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.DruidIoHttpSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.model.api.udf.UDF;
import com.mesalab.qgw.model.api.udf.UDFElements;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.qgw.service.MetadataService;
import com.zdjizhi.utils.StringUtil;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.DescribeStatement;
import net.sf.jsqlparser.statement.ShowStatement;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.lang.reflect.Constructor;
import java.util.*;

@Service("apiService")
public class ApiServiceImpl implements ApiService {
    private static final Log log = LogFactory.get();
    @Autowired
    private ClickHouseProperties clickHouseProperties;
    @Autowired
    private DruidIoHttpSource druidIoHttpSource;
    @Resource(name = "nacosMetadataService")
    private MetadataService metadataServiceTemp;

    private static MetadataService metadataService;

    @PostConstruct
    private void initMetadataService() {
        metadataService = this.metadataServiceTemp;
    }

    public static final int MAX_PARSER_LEVEL = 5;

    public static final String UNVALID_LIMIT_DESC = "NO LIMIT";


    @Override
    public BaseResult executeQuery(ApiParam param) {
        if (StringUtil.isBlank(param.getQuery())) {
            return BaseResultGenerator.success4Message("ok");
        }
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(param.getQuery());
        } catch (JSQLParserException e) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "SQL Syntax Error:", e.getCause());
        }


        BaseResult baseResult;
        if (statement instanceof Select) {
            baseResult = executeSelectStatement(param);
        } else if (statement instanceof DescribeStatement) {
            DescribeStatement describeStatement = (DescribeStatement) statement;
            baseResult = executeDescStatement(param, describeStatement);
        } else if (statement instanceof ShowStatement) {
            ShowStatement showStatement = (ShowStatement) statement;
            baseResult = executeShowStatement(param, showStatement);
        } else {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(),
                    "SQL Syntax Error: Only support statement operations for select, show and describe.", null);
        }
        return baseResult;
    }


    private BaseResult executeSelectStatement(ApiParam param) {

        BaseResult baseResult = null;

        if (QueryOptionEnum.SYNTAX_PARSE.getValue().equalsIgnoreCase(param.getOption())) {
            return BaseResultGenerator.success(SQLSyntaxParserUtil.syntaxParse(param.getQuery()));
        }

        if (QueryOptionEnum.LONG_TERM.getValue().equalsIgnoreCase(param.getOption())) {
            baseResult = ReportCacheUtils.get(param.getResultId(), param.getQuery());
            if (baseResult.isSuccess()) {
                return baseResult;
            } else {
                log.warn("Long-Term Results not exist, execute real-time query: resultID-{}, sql-{}",
                        param.getResultId(), StringUtil.strip(param.getQuery()));

            }

        }

        Dialect dbDialect = parserSQL(param);

        if (param.getOption().equalsIgnoreCase(QueryOptionEnum.SYNTAX_VALIDATION.getValue())) {
            baseResult = dbDialect.executeSyntaxCheck();
        } else {
            baseResult = dbDialect.executeQuery();
        }
        baseResult = new FederationDialect(param, baseResult).executeQuery();
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), baseResult.getCode(), baseResult.getMessage(), null);
        }

        return baseResult;
    }

    private Dialect parserSQL(ApiParam param) {
        param.setTableName(validateAndGetTableName(param));
        return parserAndGetDialect(param);
    }


    private Dialect parserAndGetDialect(ApiParam param) {
        Dialect dialect = null;
        String dbType = metadataService.getDBTypeByTableName(param.getTableName());
        if (DBTypeEnum.CLICKHOUSE.getValue().equalsIgnoreCase(dbType)) {
            param.setDbType(DBTypeEnum.CLICKHOUSE.getValue());
        } else if (DBTypeEnum.DRUID.getValue().equalsIgnoreCase(dbType)) {
            param.setDbType(DBTypeEnum.DRUID.getValue());
        } else {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), param.getTableName() + " 表未在平台注册，请联系数据平台负责人", null);
        }
        param.setDialectDBType(SQLHelper.getDialectDBType(param.getDbType()));
        convertQueryRecursive(param, parserSQLByAst(param.getQuery()));
        dialect = getDialect(param);
        return dialect;
    }

    private Dialect getDialect(ApiParam param) {
        Dialect dialect;
        try {
            Class dialectClazz = Class.forName("com.mesalab.qgw.dialect." +
                    CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, param.getDbType()) + "Dialect");
            Constructor constructor = dialectClazz.getConstructor(ApiParam.class);
            dialect = (Dialect) constructor.newInstance(param);
        } catch (Exception e) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "方言转换实例异常！", e);
        }
        return dialect;
    }


    /**
     * 对请求参数进行验证
     *
     * @param param query:查询语句，统一小写
     *              format：输出格式，默认为JSON
     *              option：查询选项，默认为real-time
     */
    private String validateAndGetTableName(ApiParam param) {

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

        return SQLHelper.getTableName(param.getQuery());
    }


    /**
     * Visitor 模式解析SQL
     *
     * @param sql
     */
    @Deprecated
    private String getTableNameFromDruid(String sql) {
        String tableName = "";
        try {

            List<SQLStatement> statementList = SQLUtils.parseStatements(sql, JdbcConstants.MYSQL);
            SchemaStatVisitor statVisitor = SQLUtils.createSchemaStatVisitor(JdbcConstants.MYSQL);
            SQLStatement statement = statementList.get(0);
            statement.accept(statVisitor);
            Map<TableStat.Name, TableStat> tables = statVisitor.getTables();

            if (tables.keySet().size() > 0) {
                TableStat.Name name = Iterables.getFirst(tables.keySet(), null);
                tableName = name.getName();
            }

        } catch (Exception e) {
            log.error("sqlParser error: ", e);
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "SQL Syntax Error：", e);
        }
        return tableName;
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
            if (statement instanceof Select) {
                Select selectStatement = (Select) statement;
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
                    throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "SQL Syntax Error: Can't find table name.");
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
                    sqlQuerySource.setLimit(UNVALID_LIMIT_DESC);
                } else { //其它暂不支持
                    throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "SQL Syntax Error：Only support statement operation as select.");
                }


            } else {
                log.warn("Not support DML Parser");
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "SQL Syntax Error: Only support statement operation as select.");
            }


        } catch (Exception e) {
            log.error("sqlParser error: ", e);
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
        } catch (Exception e) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "自定义函数转换实例异常！", e);
        }
        return udf;
    }


    /**
     * 对SQL语句进行预先解析,为语法树结构
     *
     * @param sql
     * @return
     */
    @Deprecated
    private SQLQuerySource parserSQLByAst(String sql, String dialectDbType) {

        SQLQuerySource sqlQuerySource = new SQLQuerySource();
        try {
            List<SQLStatement> statementList = SQLUtils.parseStatements(sql, dialectDbType);
            if (statementList.size() > 0) {
                SQLStatement statement = statementList.get(0);
                if (statement instanceof SQLSelectStatement) {
                    SQLSelectStatement sqlSelectStatement = (SQLSelectStatement) statement;
                    SQLSelectQuery sqlSelectQuery = sqlSelectStatement.getSelect().getQuery();
                    // 非union的查询语句
                    if (sqlSelectQuery instanceof SQLSelectQueryBlock) {
                        SQLSelectQueryBlock sqlSelectQueryBlock = (SQLSelectQueryBlock) sqlSelectQuery;
                        // 获取字段列表
                        List<SQLSelectItem> selectItems = sqlSelectQueryBlock.getSelectList();
                        sqlQuerySource.setSelectItems(Arrays.toString(selectItems.toArray()));

                        // 获取表
                        SQLTableSource table = sqlSelectQueryBlock.getFrom();
                        // 普通单表
                        if (table instanceof SQLExprTableSource) {
                            sqlQuerySource.setTableName(((SQLExprTableSource) table).getName().getSimpleName());
                            // join多表
                        } else if (table instanceof SQLJoinTableSource) {
                            SQLTableSource sqlSubTableSource = ((SQLJoinTableSource) table).getLeft();
                            sqlQuerySource.setTableName(((SQLExprTableSource) sqlSubTableSource).getName().getSimpleName());
                            // 子查询作为表
                        } else if (table instanceof SQLSubqueryTableSource) {
                            SQLSelectQuery sqlSubSelectQuery = ((SQLSubqueryTableSource) table).getSelect().getQuery();
                            sqlQuerySource.setTableName(getMultiSqlTableName(sqlSubSelectQuery));
                        }
                        // 获取where条件
                        SQLExpr where = sqlSelectQueryBlock.getWhere();

                        // 如果是二元表达式
                        if (where instanceof SQLBinaryOpExpr) {
                            SQLBinaryOpExpr sqlBinaryOpExpr = (SQLBinaryOpExpr) where;
                            SQLExpr left = sqlBinaryOpExpr.getLeft();
                            SQLBinaryOperator operator = sqlBinaryOpExpr.getOperator();
                            SQLExpr right = sqlBinaryOpExpr.getRight();
                            sqlQuerySource.setExpr(left.toString() + operator.name() + right.toString());
                            // 如果是子查询
                        } else if (where instanceof SQLInSubQueryExpr) {
                            log.warn("UNSupport Parser sub Where!");

                        }

                        // 获取分页
                        SQLLimit limit = sqlSelectQueryBlock.getLimit();

                        if (limit != null) {
                            if (StringUtil.isNotEmpty(limit.getOffset())) {
                                sqlQuerySource.setLimit(StringUtil.setDefaultIfEmpty(limit.getOffset(), 0) + "," + limit.getRowCount());
                            } else {
                                sqlQuerySource.setLimit(String.valueOf(limit.getRowCount()));
                            }
                        }

                        // union的查询语句
                    } else if (sqlSelectQuery instanceof SQLUnionQuery) {
                        SQLUnionQuery sqlUnionQuery = (SQLUnionQuery) sqlSelectQuery;
                        sqlQuerySource.setTableName(getMultiSqlTableName(sqlUnionQuery.getRight()));
                        sqlQuerySource.setLimit(UNVALID_LIMIT_DESC);
                    }
                } else {
                    throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "SQL Syntax Error：Only support statement operation as select.");
                }


            }
        } catch (Exception e) {
            log.error("sqlParser error: ", e);
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "SQL Syntax Error：", e);

        }


        return sqlQuerySource;
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
        } else {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), param.getTableName() + " 表未在平台注册，请联系数据平台负责人", null);
        }

        param.setFormat(QueryFormatEnum.JSON.getValue());
        SQLQuerySource dbQuerySource = new SQLQuerySource();
        dbQuerySource.setSqlBody(param.getQuery());
        param.setDbQuerySource(dbQuerySource);
        Dialect dialect = getDialect(param);
        BaseResult baseResult = dataEncapsulationOfSchema(dialect.executeAdministrativeQuery(), param);

        if (!baseResult.isSuccess()) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), "describe table error, please check tableName", null);
        }
        return baseResult;
    }

    /**
     * 执行show语句
     *
     * @param param
     * @param showStatement
     * @return
     */
    private BaseResult executeShowStatement(ApiParam param, ShowStatement showStatement) {

        if (showStatement != null && !"tables".equalsIgnoreCase(showStatement.getName())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), "SQL Syntax Error：不支持Show tables 之外的操作", null);
        }
        if (clickHouseProperties.getDbname().equalsIgnoreCase(param.getSchema())) {
            param.setDbType(DBTypeEnum.CLICKHOUSE.getValue());
            param.setDialectDBType(DBTypeEnum.CLICKHOUSE.getValue());
        } else if (druidIoHttpSource.getDbname().equalsIgnoreCase(param.getSchema())) {
            param.setDbType(DBTypeEnum.DRUID.getValue());
            param.setDialectDBType(DBTypeEnum.DRUID.getValue());
            param.setQuery("SELECT TABLE_NAME AS name FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'TABLE'");
        } else if (StringUtil.isBlank(param.getSchema())) {
            BaseResult result = getAllTables();
            return result;
        } else {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), "schema name error", null);
        }
        param.setFormat(QueryFormatEnum.JSON.getValue());

        Dialect dialect = getDialect(param);
        BaseResult result = dialect.executeAdministrativeQuery();
        if (result.getStatus() == null || result.getStatus() != HttpStatus.SC_OK) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), result.getMessage(), null);
        }
        List tables = (ArrayList) result.getData();
        List<String> list = new ArrayList<>();
        Iterator iterator = tables.iterator();
        while (iterator.hasNext()) {
            Map<String, String> next = (Map) iterator.next();
            list.add(next.get("name"));
        }
        return tablesResultEncapsulation(list, param.getSchema());
    }

    /**
     * 获取所有表信息
     *
     * @return
     */
    private BaseResult getAllTables() {
        //查ck
        ApiParam temp = new ApiParam();
        temp.setQuery("show tables");
        temp.setSchema(clickHouseProperties.getDbname());
        BaseResult ck = new BaseResult();
        try {
            ck = executeShowStatement(temp, null);
        } catch (Exception e) {
            log.info("tables: 查询clickhouse异常", e);
        }

        //查druid
        BaseResult druid = new BaseResult();
        temp = new ApiParam();
        temp.setSchema(druidIoHttpSource.getDbname());
        try {
            druid = executeShowStatement(temp, null);
        } catch (Exception e) {
            log.info("tables: 查询druid   异常", e);
        }
        //合并结果
        Map<String, Object> date = new LinkedHashMap<>();
        List symbols = new ArrayList<>();
        String dbName = "";
        date.put("type", "enum");
        if (ck.getStatus() != null && ck.getStatus() == HttpStatus.SC_OK) {
            Map ckDate = (Map) ck.getData();
            dbName += (String) ckDate.get("name");
            symbols.addAll((List) ckDate.get("symbols"));
        }
        if (druid.getStatus() != null && druid.getStatus() == HttpStatus.SC_OK) {
            Map druidDate = (Map) druid.getData();
            if (!"".equals(dbName)) {
                dbName += ",";
            }
            dbName += (String) druidDate.get("name");
            symbols.addAll((List) druidDate.get("symbols"));
        }
        date.put("name", dbName);
        date.put("symbols", symbols);
        if (ck.getStatus() == HttpStatus.SC_OK) {
            ck.setData(date);
            return ck;
        } else if (druid.getStatus() == HttpStatus.SC_OK) {
            druid.setData(date);
            return druid;
        } else {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), "查询所有tables异常", null);
        }
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
    private BaseResult tablesResultEncapsulation(List tables, String database) {
        List<String> list = new ArrayList<>();
        Map<String, Object> date = new LinkedHashMap<>();
        date.put("type", "enum");
        date.put("name", database);
        Iterator iterator = tables.iterator();
        while (iterator.hasNext()) {
            list.add(String.valueOf(iterator.next()));
            date.put("symbols", list);
        }
        BaseResult<Map> baseResult = BaseResultGenerator.success("ok", null);
        baseResult.setData(date);
        return baseResult;
    }

}

package com.mesalab.qgw.service.impl;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLLimit;
import com.alibaba.druid.sql.ast.SQLOrderBy;
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
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.configuration.SchemaProperties;
import com.mesalab.common.enums.*;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.SQLHelper;
import com.mesalab.qgw.dialect.Dialect;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.service.IAPIService;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.parser.Node;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.util.AddAliasesVisitor;
import net.sf.jsqlparser.util.TablesNamesFinder;
import net.sf.jsqlparser.util.deparser.SelectDeParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service("apiService")
public class APIServiceImpl implements IAPIService {
    @Autowired
    private SchemaProperties schemaProperties;

    @Override
    public BaseResult executeQuery(ApiParam param) {

        validateAndGetTableName(param);

        BaseResult baseResult = parserAndGetDialect(param).sqlDialectTransform().executeHttpQuery();

        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus() ,baseResult.getMessage(), null);
        }


        return baseResult;
    }



    private Dialect parserAndGetDialect(ApiParam param) {

        Dialect dialect = null;

        if (param.getOption().equalsIgnoreCase(QueryOptionEnum.LONG_TERM.getValue())) {//离线查询无需解析sql
            param.setDbType(DBTypeEnum.HBASE.getValue());
            param.setDialectDBType(SQLHelper.getDialectDBType(param.getDbType()));
        } else {
            if (Splitter.on(",").trimResults().omitEmptyStrings().splitToList(schemaProperties.getClickhouse())
                    .contains(param.getTableName().toLowerCase())) {
                param.setDbType(DBTypeEnum.CLICKHOUSE.getValue());
            }  else if (Splitter.on(",").trimResults().omitEmptyStrings().splitToList(schemaProperties.getDruid())
                    .contains(param.getTableName().toLowerCase())) {
                param.setDbType(DBTypeEnum.DRUID.getValue());
            } else  {
                throw new BusinessException(BaseResultEnum.FAIL.getCode() , "该表未在平台注册，请联系数据平台负责人", null);
            }

            param.setDialectDBType(SQLHelper.getDialectDBType(param.getDbType()));

            param.setSqlQuerySource(parserSQLByAst(param.getQuery()));

        }
        try {
            Class dialectClazz = Class.forName("com.mesalab.qgw.dialect." + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, param.getDbType())+"Dialect");
            Constructor constructor = dialectClazz.getConstructor(ApiParam.class);
            dialect = (Dialect) constructor.newInstance(param);

        } catch (Exception e) {
            throw new BusinessException(BaseResultEnum.FAIL.getCode() , "方言转换实例异常！", e);
        }

        return dialect;
    }



    /**
     * 对请求参数进行验证
     * @param param
     * query:查询语句，统一小写
     * format：输出格式，默认为JSON
     * option：查询选项，默认为real-time
     */
    private void  validateAndGetTableName(ApiParam param) {


       if (StringUtil.isBlank(param.getQuery())) {
           throw new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：query SQL 错误", null);
       }

       if (StringUtil.isNotBlank(param.getOption())) {

            if (!param.getOption().equalsIgnoreCase(QueryOptionEnum.REAL_TIME.getValue()) &&
                    !param.getOption().equalsIgnoreCase(QueryOptionEnum.LONG_TERM.getValue())) {
                throw new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：option 参数错误", null);
            }

            if (param.getOption().equalsIgnoreCase(QueryOptionEnum.LONG_TERM.getValue())) {
                if (StringUtil.isBlank(param.getReportId())) {
                    throw new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：option=long-term,需指定reportId", null);
                }
            }

       } else {
            param.setOption(QueryOptionEnum.REAL_TIME.getValue());
       }


        if (StringUtil.isNotBlank(param.getFormat())) {

            if (!param.getFormat().equalsIgnoreCase(QueryFormatEnum.JSON.getValue())
                && !param.getFormat().equalsIgnoreCase(QueryFormatEnum.CSV.getValue())) {
                throw new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：format 参数错误", null);
            }

        } else {
            param.setFormat(QueryFormatEnum.JSON.getValue());
        }

        param.setTableName(getTableName(param.getQuery()));

    }


    private String  getTableName(String sql) {

        String tableName = "";
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (statement instanceof Select) {
                Select selectStatement = (Select) statement;
                TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
                List<String> tableList =  tablesNamesFinder.getTableList(selectStatement);
                if (StringUtil.isNotEmpty(tableList)) {
                    tableName = tableList.get(0);
                } else {
                    throw  new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：SQL 解析错误,未能获取表名" );
                }


            } else {
                log.warn("Not support DML Parser");
                throw  new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：SQL 解析错误,不支持SELECT 之外操作" );
            }

        } catch (Exception e) {

            throw  new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：SQL 解析错误", e.getCause());
        }

        return  tableName;
    }



    /**
     * Visitor 模式解析SQL
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
                TableStat.Name name =  Iterables.getFirst(tables.keySet(), null);
                tableName = name.getName();
            }

        } catch (Exception e) {
                log.error("sqlParser error: ", e);
            throw  new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：SQL 解析错误", e);
        }
        return  tableName;
    }


    /**
     *
     * 解析SQL
     * @param sql
     * @return
     */
    private SQLQuerySource parserSQLByAst(String sql) {


        SQLQuerySource sqlQuerySource = new SQLQuerySource();
        try {

            Statement statement = CCJSqlParserUtil.parse(sql);
            if (statement instanceof Select) {
                Select selectStatement = (Select) statement;
                TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
                List<String> tableList =  tablesNamesFinder.getTableList(selectStatement);

                if (StringUtil.isNotEmpty(tableList)) {

                    sqlQuerySource.setTableName(tableList.get(0));
                } else {
                    throw  new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：SQL 解析错误,未能获取表名" );
                }

                SelectBody body = selectStatement.getSelectBody();


                if (body instanceof  PlainSelect)  { //单条查询

                    PlainSelect select = (PlainSelect) body;



                    Expression where = select.getWhere();

                    if (where != null){
                        sqlQuerySource.setExpr(where.toString());
                    }

                    List<SelectItem> selectItemsList = select.getSelectItems();

                    if (StringUtil.isNotEmpty(selectItemsList)) {
                        String selectItemsString =  Joiner.on(",").join(selectItemsList);
                        sqlQuerySource.setSelectItems(selectItemsString);

                    }

                    List<Expression> groupList = select.getGroupByColumnReferences();


                    if (StringUtil.isNotEmpty(groupList)) {
                        String groupByString =  Joiner.on(",").join(groupList);
                        sqlQuerySource.setGroupByClause(groupByString);

                    }


                    List<OrderByElement> orderByList = select.getOrderByElements();
                    if (StringUtil.isNotEmpty(orderByList)) {
                        String orderByString =  Joiner.on(",").join(orderByList);
                        sqlQuerySource.setOrderBy(orderByString);

                    }



                    FromItem subItem = select.getFromItem();

                    if (subItem instanceof  SubSelect) {
                        log.info("SQL 包含子查询,暂不做解析处理  ");
                    }


                    Limit limit = select.getLimit();


                    if (limit != null) {

                        if (StringUtil.isNotEmpty(limit.getOffset())) {
                            sqlQuerySource.setLimit(StringUtil.setDefaultIfEmpty(limit.getOffset() , 0)+ "," + limit.getRowCount());
                        } else {
                            sqlQuerySource.setLimit(String.valueOf(limit.getRowCount()));
                        }
                    }


                } else if (body instanceof SetOperationList) { // 连接查询
                    sqlQuerySource.setLimit("NO LIMIT");
                } else { //其它暂不支持
                    throw  new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：不支持SELECT之外Statement操作");
                }



            } else {
                log.warn("Not support DML Parser");
                throw  new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：SQL 解析错误,不支持SELECT 之外操作" );
            }



        } catch (Exception e) {
            log.error("sqlParser error: ", e);
            throw  new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：SQL 解析错误", e);

        }

        return sqlQuerySource;
    }


    /**
     *  对SQL语句进行预先解析,为语法树结构
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
                if (statement instanceof  SQLSelectStatement) {
                    SQLSelectStatement sqlSelectStatement = (SQLSelectStatement) statement;
                    SQLSelectQuery sqlSelectQuery = sqlSelectStatement.getSelect().getQuery();
                    // 非union的查询语句
                    if (sqlSelectQuery instanceof SQLSelectQueryBlock) {
                        SQLSelectQueryBlock sqlSelectQueryBlock = (SQLSelectQueryBlock) sqlSelectQuery;
                        // 获取字段列表
                        List<SQLSelectItem> selectItems = sqlSelectQueryBlock.getSelectList();
                        sqlQuerySource.setSelectItems( Arrays.toString(selectItems.toArray()));

                        // 获取表
                        SQLTableSource table = sqlSelectQueryBlock.getFrom();
                        // 普通单表
                        if (table instanceof SQLExprTableSource) {
                            sqlQuerySource.setTableName(((SQLExprTableSource) table).getName().getSimpleName());
                            // join多表
                        } else if (table instanceof SQLJoinTableSource) {
                            SQLTableSource sqlSubTableSource = ((SQLJoinTableSource)table).getLeft();
                            sqlQuerySource.setTableName(((SQLExprTableSource) sqlSubTableSource).getName().getSimpleName());
                            // 子查询作为表
                        } else if (table instanceof SQLSubqueryTableSource) {
                            SQLSelectQuery sqlSubSelectQuery = ((SQLSubqueryTableSource)table).getSelect().getQuery();
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
                            SQLInSubQueryExpr sqlInSubQueryExpr = (SQLInSubQueryExpr) where;

                        }


                        // 获取分组
                        SQLSelectGroupByClause groupBy = sqlSelectQueryBlock.getGroupBy();

                        // 获取排序
                        SQLOrderBy orderBy = sqlSelectQueryBlock.getOrderBy();

                        // 获取分页
                        SQLLimit limit = sqlSelectQueryBlock.getLimit();

                        if (limit != null) {
                            if (StringUtil.isNotEmpty(limit.getOffset())) {
                                sqlQuerySource.setLimit(StringUtil.setDefaultIfEmpty(limit.getOffset() , 0)+ "," + limit.getRowCount());
                            } else {
                                sqlQuerySource.setLimit(String.valueOf(limit.getRowCount()));
                            }
                        }


                        // union的查询语句
                    } else if (sqlSelectQuery instanceof SQLUnionQuery) {
                        SQLUnionQuery sqlUnionQuery = (SQLUnionQuery) sqlSelectQuery;
                        sqlQuerySource.setTableName(getMultiSqlTableName(sqlUnionQuery.getRight()));
                        sqlQuerySource.setLimit("NO LIMIT");
                    }
                } else {
                    throw  new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：不支持SELECT之外Statement操作");
                }



            }
        } catch (Exception e) {
            log.error("sqlParser error: ", e);
            throw  new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：SQL 解析错误", e);

        }


        return sqlQuerySource;
    }


    private String getMultiSqlTableName(SQLSelectQuery sqlSubSelectQuery) {
        SQLTableSource subTable =  ((SQLSelectQueryBlock) sqlSubSelectQuery).getFrom();
        if (subTable instanceof SQLExprTableSource) {
            return ((SQLExprTableSource) subTable).getName().getSimpleName();
        } else {
            throw  new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：暂不支持多级嵌套子查询操作");
        }
    }






}

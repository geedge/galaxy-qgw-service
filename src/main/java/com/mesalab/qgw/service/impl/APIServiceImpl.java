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
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.base.SchemaBase;
import com.mesalab.common.configuration.ClickHouseProperties;
import com.mesalab.common.configuration.DruidIoProperties;
import com.mesalab.common.configuration.SchemaProperties;
import com.mesalab.common.enums.*;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.SQLHelper;
import com.mesalab.qgw.dialect.Dialect;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.service.IAPIService;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.DescribeStatement;
import net.sf.jsqlparser.statement.ShowStatement;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.*;

@Slf4j
@Service("apiService")
public class APIServiceImpl implements IAPIService {

    @Autowired
    private SchemaProperties schemaProperties;
    @Autowired
    private ClickHouseProperties clickHouseProperties;
    @Autowired
    private DruidIoProperties druidIoProperties;


    @Override
    public BaseResult executeQuery(ApiParam param) {
        if (StringUtil.isBlank(param.getQuery())) {
            return BaseResultGenerator.success4Message("ok");
        }
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(param.getQuery());
        } catch (Exception e) {
            throw new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：SQL 解析错误", e.getCause());
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
            throw new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：SQL 解析错误,目前只支持SELECT、SHOW、DESCRIBE 操作", null);
        }
        return baseResult;
    }

    private BaseResult executeSelectStatement(ApiParam param) {
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
                throw new BusinessException(BaseResultEnum.FAIL.getCode(), param.getTableName() + " 表未在平台注册，请联系数据平台负责人", null);
            }

            param.setDialectDBType(SQLHelper.getDialectDBType(param.getDbType()));

            param.setSqlQuerySource(parserSQLByAst(param.getQuery()));

        }
        dialect = getDialect(param);

        return dialect;
    }

    private Dialect getDialect(ApiParam param) {
        Dialect dialect;
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
                    int i = tableName.lastIndexOf(".");
                    tableName = i < 0 ? tableName : tableName.substring(i + 1);
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
                    String tableName = tableList.get(0);
                    int i = tableName.lastIndexOf(".");
                    tableName = i < 0 ? tableName : tableName.substring(i + 1);
                    sqlQuerySource.setTableName(tableName);
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

                    GroupByElement groupBy;
                    List<Expression> groupList;
                    if (StringUtil.isNotEmpty(groupBy = select.getGroupBy()) && StringUtil.isNotEmpty(groupList = groupBy.getGroupByExpressions())) {
                        String groupByString = Joiner.on(",").join(groupList);
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



    /**
     * 执行describe语句
     * @param param
     * @param statement
     * @return
     */
    private BaseResult executeDescStatement(ApiParam param, DescribeStatement statement) {
        if(QueryOptionEnum.QUERY_SCHEMA.getValue().equalsIgnoreCase(param.getOption())){
            Map<String, JsonProperties> ckSchemaInfo = getSchemaInfo("/config/avro/clickhouse");
            Map<String, JsonProperties> druidSchemaInfo = getSchemaInfo("/config/avro/druid");
            if (ckSchemaInfo.containsKey(param.getTableName())) {
                return getSchemaResult(param, ckSchemaInfo);
            } else if (druidSchemaInfo.containsKey(param.getTableName())) {
                return getSchemaResult(param, druidSchemaInfo);
            } else {
                throw new BusinessException(BaseResultEnum.FAIL.getCode(), "schema未注册此表: " + param.getTableName(), null);
            }
        } else {
            String name = statement == null ? param.getTableName() : statement.getTable().getName();
            param.setTableName(name);
            //查ck
            ApiParam temp = new ApiParam();
            temp.setSchema(clickHouseProperties.getDbname());
            temp.setTableName(name);
            temp.setQuery("show tables");
            BaseResult ckResult = executeShowStatement(temp, null);
            Map data = (Map) ckResult.getData();
            List symbols = (List) data.get("symbols");
            if (symbols.contains(param.getTableName())) {
                //表在ck中
                param.setDbType(DBTypeEnum.CLICKHOUSE.getValue());
                param.setDialectDBType(DBTypeEnum.CLICKHOUSE.getValue());
            } else {//走druid去查
                param.setDbType(DBTypeEnum.DRUID.getValue());
                param.setDialectDBType(DBTypeEnum.DRUID.getValue());
                param.setQuery("SELECT COLUMN_NAME as name, DATA_TYPE as type FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + name + "'");
            }
            param.setOption(QueryOptionEnum.QUERY_SCHEMA.getValue());
            param.setFormat(QueryFormatEnum.JSON.getValue());
            Dialect dialect = getDialect(param);
            BaseResult result = dialect.sqlDialectTransform().executeHttpQuery();
            if (result.getStatus() == null || result.getStatus() != HttpStatus.SC_OK) {
                throw new BusinessException(BaseResultEnum.FAIL.getCode(), "describe table error, please check tableName", null);
            }
            BaseResult baseResult = dataEncapsulationOfSchema(result, param);
            return baseResult;
        }
    }

    /**
     * 封装schema信息
     * @param param
     * @param schema
     * @return
     */
    private BaseResult getSchemaResult(ApiParam param, Map<String, JsonProperties> schema) {
        Map resultMap = (Map) JsonMapper.fromJsonString(String.valueOf(schema.get(param.getTableName())), Map.class);
        if (resultMap == null) {
            throw new BusinessException(BaseResultEnum.FAIL.getCode(), "schema未注册此表: " + param.getTableName(), null);
        }
        List<Map> fields = (List<Map>) resultMap.get("fields");
        Iterator<Map> iterator = fields.iterator();
        Map mapDoc = null;
        while (iterator.hasNext()) {
            Map next = iterator.next();
            try {
                mapDoc = (Map) JsonMapper.fromJsonString(String.valueOf(next.get("doc")), Map.class);
            } catch (Exception ex) {
                log.debug("该字段的 doc 为非JSON字符串，未转换");
            }
            if (mapDoc != null) {
                next.put("doc", mapDoc);
            }
            if(StringUtil.isEmpty(mapDoc) && !StringUtil.isEmpty(next.get("doc"))){
                log.error("{}表该字段{}的 doc 为非JSON字符串，未转换", param.getTableName(), next.get("name"));
            }
        }
        BaseResult<Map> baseResult = BaseResultGenerator.success("ok", resultMap);
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), baseResult.getMessage(), null);
        }
        return baseResult;
    }

    /**
     * 执行show语句
     * @param param
     * @param showStatement
     * @return
     */
    private BaseResult executeShowStatement(ApiParam param, ShowStatement showStatement) {

        if (QueryOptionEnum.QUERY_SCHEMA.getValue().equalsIgnoreCase(param.getOption())) {
            Map<String, JsonProperties> ckSchemaInfo = getSchemaInfo("/config/avro/clickhouse");
            Map<String, JsonProperties> druidSchemaInfo = getSchemaInfo("/config/avro/druid");
            if (clickHouseProperties.getDbname().equals(param.getSchema())) {
                List<String> list = new ArrayList<>(ckSchemaInfo.keySet());
                return tablesResultEncapsulation(list, param.getSchema());
            } else if (druidIoProperties.getDbname().equals(param.getSchema())) {
                List<String> list = new ArrayList<>(druidSchemaInfo.keySet());
                tablesResultEncapsulation(list, param.getSchema());
                return tablesResultEncapsulation(list, param.getSchema());
            } else {
                throw new BusinessException(BaseResultEnum.FAIL.getCode(), "schema未注册此表: " + param.getTableName(), null);
            }

        }else {
            if(showStatement != null && !"tables".equalsIgnoreCase(showStatement.getName())){
                throw new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：不支持Show tables 之外的操作", null);
            }
            if (clickHouseProperties.getDbname().equalsIgnoreCase(param.getSchema())) {
                param.setDbType(DBTypeEnum.CLICKHOUSE.getValue());
                param.setDialectDBType(DBTypeEnum.CLICKHOUSE.getValue());
            } else if (druidIoProperties.getDbname().equalsIgnoreCase(param.getSchema())) {
                param.setDbType(DBTypeEnum.DRUID.getValue());
                param.setDialectDBType(DBTypeEnum.DRUID.getValue());
                param.setQuery("SELECT TABLE_NAME AS name FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'TABLE'");
            } else if (StringUtil.isBlank(param.getSchema())) {
                BaseResult result = getAllTables();
                return result;
            } else {
                throw new BusinessException(BaseResultEnum.FAIL.getCode(), "schema name error", null);
            }
            param.setOption(QueryOptionEnum.QUERY_SCHEMA.getValue());
            param.setFormat(QueryFormatEnum.JSON.getValue());
            Dialect dialect = getDialect(param);
            BaseResult result = dialect.sqlDialectTransform().executeHttpQuery();
            if(result.getStatus() == null || result.getStatus() != HttpStatus.SC_OK){
                throw new BusinessException(BaseResultEnum.FAIL.getCode(), result.getMessage(), null);
            }
            List tables = (ArrayList) result.getData();
            List<String> list = new ArrayList<>();
            Map<String, Object> date = new LinkedHashMap<>();
            Iterator iterator = tables.iterator();
            while (iterator.hasNext()){
                Map<String, String> next = (Map)iterator.next();
                if(!next.get("name").endsWith("_local")){
                    list.add(next.get("name"));
                }
            }
            return tablesResultEncapsulation(list, param.getSchema());
        }
    }

    /**
     * 获取所有表信息
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
        }catch (Exception e){
            log.info("tables: 查询clickhouse异常", e);
        }

        //查druid
        BaseResult druid = new BaseResult();
        temp = new ApiParam();
        temp.setSchema(druidIoProperties.getDbname());
        try {
            druid = executeShowStatement(temp, null);
        }catch (Exception e){
            log.info("tables: 查询druid   异常", e);
        }
        //合并结果
        Map<String, Object> date = new LinkedHashMap<>();
        List symbols = new ArrayList<>();
        String dbName = "";
        date.put("type", "enum");
        if(ck.getStatus() != null && ck.getStatus() == HttpStatus.SC_OK){
            Map ckDate = (Map)ck.getData();
            dbName += (String) ckDate.get("name");
            symbols.addAll((List)ckDate.get("symbols"));
        }
        if(druid.getStatus() != null && druid.getStatus() == HttpStatus.SC_OK){
            Map druidDate = (Map)druid.getData();
            if(!"".equals(dbName)){
                dbName += ",";
            }
            dbName += (String) druidDate.get("name");
            symbols.addAll((List)druidDate.get("symbols"));
        }
        date.put("name", dbName);
        date.put("symbols", symbols);
        if(ck.getStatus() == HttpStatus.SC_OK){
            ck.setData(date);
            return ck;
        }else if(druid.getStatus() == HttpStatus.SC_OK){
            druid.setData(date);
            return druid;
        }else {
            throw new BusinessException(BaseResultEnum.FAIL.getCode(), "查询所有tables异常", null);
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
        HashMap<String, Object> map = new HashMap<>();
        List<Map> list = (List<Map>) result.getData();
        Iterator iterator = list.iterator();
        while (iterator.hasNext()) {
            Map<String, String> next = (Map) iterator.next();
            map = new HashMap<>();
            map.put("name", next.get("name"));
            map.put("type", next.get("type"));
            fields.add(map);
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
        while (iterator.hasNext()){
            list.add(String.valueOf(iterator.next()));
            date.put("symbols", list);
        }
        BaseResult<Map> baseResult = BaseResultGenerator.success("ok", null);
        baseResult.setData(date);
        return baseResult;
    }

    /**
     * 获取Schema
     * @param path
     * @return
     */
    public static Map<String, JsonProperties> getSchemaInfo(String path){
        Map<String, JsonProperties> cKSchema = new HashMap<>();
        String pathRoot= null;
        try {
            pathRoot = new File("").getCanonicalPath();
            File file = new File(pathRoot + path);
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    if(!f.getName().toLowerCase().endsWith(".avsc")){
                        continue;
                    }
                    try {
                        Schema ckSchema = new Schema.Parser().parse(new File(f.getPath()));
                        cKSchema.put(ckSchema.getName(), ckSchema);
                    }catch (Exception ex){
                        log.error("遍历{}路径下文件失败: {}", path, f.getName(), ex);
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取 项目路径/{}失败", path);
        }
        return cKSchema;
    }
}

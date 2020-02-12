package com.mesalab.qgw.dialect;


import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SQLDateFunctionUtil;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.Encodes;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.ComparisonOperator;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import org.apache.avro.Schema;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private boolean canSubQueryOfSlaveTable;


    public ClickhouseDialect(ApiParam param) {
        this.param = param;
        this.canSubQueryOfSlaveTable = true;
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
            sqlQuerySource.setLimit("0, 200");
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
     * 方式1.生成基于子表(从表)
     * 方式2.生成基于分区键
     * 限制：
     * 1. 仅适用一级普通查询，带where （无group by 统计）的查询
     * 2. 不对count 统计进行生成
     * @param sql
     * @return
     */
    private String generateSubQuery(String sql) {


        if (StringUtil.isBlank(param.getSqlQuerySource().getExpr())) {
            return sql;
        }

        if (StringUtil.isNotBlank(param.getSqlQuerySource().getGroupByClause())) {
            return sql;
        }

        if (pCount.matcher(param.getSqlQuerySource().getSelectItems()).find()) {
            return sql;
        }

        String key;
        String tableName = null;
        try {
            tableName = getSlaveTableName();
        } catch (Exception e) {
            log.error("get subTableQuery of tableName is failed", e);
        }
        if (StringUtil.isNotBlank(tableName)) {//方式一: 基于子表(从表)子查询
            key = metadataService.getPrimaryKey(param.getTableName());
            tableName = clickHouseHttpSource.getDbname() + "." + tableName;
            return addSubQueryOfSlaveTable(sql, key, tableName);
        } else {//方式二: 基于分区键子查询
            key = getPartitionKey();
            tableName = param.getTableName();
            return addSubQuery(sql, key, tableName);
        }
    }

    /**
     * 基于分区键方式
     * @param sql
     * @param key
     * @param tableName
     * @return
     */
    private String addSubQuery(String sql, String key, String tableName) {
        StringBuffer subQuery = getSubSql(key, tableName);
        Matcher m = pWhere.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, "where  " + key + " in ( " + subQuery + ") and  ");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 基于子表(从表)方式添加子查询
     * @param sql
     * @param key
     * @param tableName
     * @return
     */
    private String addSubQueryOfSlaveTable(String sql, String key, String tableName) {
        StringBuffer subQuery = getSubSql(key, tableName);
        StringBuffer sb = new StringBuffer();
        sb.append("select ").append(param.getSqlQuerySource().getSelectItems()).append(" from ").append(param.getTableName()).append(" where ").append(key).append(" in ( ").append(subQuery).append(")");
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
     * @param key
     * @param tableName
     * @return
     */
    private StringBuffer getSubSql(String key, String tableName) {
        StringBuffer subQuery = new StringBuffer();

        subQuery.append("select " + key + " from ").append(tableName)
                .append(" where ").append(param.getSqlQuerySource().getExpr());

        List<OrderByElement> listOrderElement = param.getSqlQuerySource().getListOrderElement();
        if (StringUtil.isNotEmpty(listOrderElement)) {
            String orderBy = param.getSqlQuerySource().getOrderBy();
            for (OrderByElement order : listOrderElement) {
                String orderField = order.getExpression().toString();
                String aliasField = param.getSqlQuerySource().getFields().get(orderField);
                if(StringUtil.isNotBlank(aliasField)){
                    orderBy = orderBy.replaceAll("\\b"+orderField+"\\b", aliasField);
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

        }
        return subQuery;
    }


    /**
     * 获取当前CK查询中的分区键，用于子查询优化操作;
     * 若分区键被其它列当别名使用，返回空
     * @return
     */
    private String getPartitionKey() {
        String partitionKey = metadataService.getPartitionKey(param.getTableName());

        if (StringUtil.isNotBlank(partitionKey)) {
            String tempValue = param.getSqlQuerySource().getFields().get(partitionKey);
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
    public Dialect sqlDialectTransform() {
        if (!param.getOption().equalsIgnoreCase(QueryOptionEnum.QUERY_SCHEMA.getValue())) {
            param.setQuery(generatePageSql(param.getQuery(), param.getSqlQuerySource()));
            param.setQuery(generateUniq(param.getQuery()));
            param.setQuery(generateSubQuery(param.getQuery()));
            param.setQuery(generateStandard(param.getQuery()));
            param.setQuery(SQLDateFunctionUtil.generateDateFunction(param.getQuery(), DBTypeEnum.CLICKHOUSE.getValue()));
        }
        param.setQuery(generateSqlEncode(param.getQuery()));


        return this;
    }

    @Override
    public BaseResult executeHttpQuery() {
        BaseResult baseResult = null;
        StringBuilder urlBuilder = new StringBuilder("http://")
                .append(clickHouseHttpSource.getUrl()).append("/?");

        StringBuilder queryparamBuilder = new StringBuilder("user=")
                .append(clickHouseHttpSource.getUsername()).append("&")
                .append("password=").append(clickHouseHttpSource.getPassword()).append("&")
                .append("database=")
                .append(clickHouseHttpSource.getDbname()).append("&")
                .append("query=").append(param.getQuery())
                .append(" FORMAT ").append(param.getFormat().toUpperCase()).append(" ;");

        log.info("Query Session and Events Query : {}" , urlBuilder.toString() + Encodes.urlDecode(queryparamBuilder.toString()));

        List<NameValuePair> values = URLEncodedUtils.parse(queryparamBuilder.toString(), Charset.forName("UTF-8"));

        Map<String, String> resultMap = httpClientService.httpGet(urlBuilder.toString() + URLEncodedUtils.format(values, "utf-8"));

       if (StringUtil.isEmpty(resultMap)) {
           baseResult =  BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "服务繁忙，请联系数据平台方！");
       } else {
           if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {

               if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.CSV.getValue())) {
                   baseResult = BaseResultGenerator.success("ok", resultMap.get("result"), null);
               } else if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.JSON.getValue())) {
                   Map<String, Object> maps =(Map<String, Object>)JsonMapper.fromJsonString(resultMap.get("result"),  Map.class);
                   Map<String, Object> statisticMap = (Map<String, Object>)maps.get("statistics");
                   Map<String, Object> statistics = Maps.newHashMap();
                   statistics.put("elapsed", String.format("%.2f", (double)statisticMap.get("elapsed")));
                   statistics.put("rows_read", statisticMap.get("rows_read"));
                   int resultSize = StringUtil.isEmpty(maps.get("data")) ? 0 : JsonMapper.toJsonString(maps.get("data")).getBytes().length;
                   statistics.put("result_size", resultSize);
                   baseResult = BaseResultGenerator.success("ok", maps.get("data"), statistics);
               }



           }else if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_NOT_FOUND))) {
               baseResult = BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND, resultMap.get("result"));
           } else if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR))) {
               baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, resultMap.get("result"));
           } else {
               baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, resultMap.get("result"));
           }
       }


       return  baseResult;

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
     * 查询可进行子表(从表)查询的表名, 如不能则返回null
     * @return
     */
    private String getSlaveTableName() {

        BaseResult fields = metadataService.getSchemaInfo("fields", param.getTableName());
        Map schema = (Map) JsonMapper.fromJsonString(JsonMapper.toJsonString(fields.getData()), Map.class);
        if (StringUtil.isEmpty(schema)) {
            return null;
        }
        Map doc = (Map) JsonMapper.fromJsonString(JsonMapper.toJsonString(schema.get("doc")), Map.class);
        if (StringUtil.isEmpty(doc)) {
            return null;
        }
        String[] split = StringUtil.split((String) doc.get("slave_table"), ",");
        if (StringUtil.isEmpty(split)) {
            return null;
        }

        for (String slaveTableName : split) {
            String partitionKey = metadataService.getPartitionKey(slaveTableName);
            if (param.getSqlQuerySource().getExpr().contains(partitionKey)) {
                List<OrderByElement> listOrderElement = param.getSqlQuerySource().getListOrderElement();
                if(StringUtil.isNotEmpty(listOrderElement)){//判断order by 列是否在子表中
                    for (OrderByElement order : listOrderElement) {
                        String orderField = order.getExpression().toString();
                        String aliasField = param.getSqlQuerySource().getFields().get(orderField);
                        if(StringUtil.isNotBlank(aliasField) && !isField(slaveTableName, aliasField)){
                            return null;
                        }
                        if (StringUtil.isBlank(aliasField) && !isField(slaveTableName, orderField)) {
                            return null;
                        }
                    }
                }
                judgeOfSlaveQuery(param.getSqlQuerySource().getExpression(), param.getTableName(), slaveTableName);
                if (canSubQueryOfSlaveTable) {
                    return slaveTableName;
                }
                break;
            }
        }
        return null;
    }

    /**
     * 判断schema中是否存在fieldName列
     * @param tableName
     * @param fieldName
     * @return
     */
    private boolean isField(String tableName, String fieldName) {
        BaseResult result = metadataService.getSchemaInfo("fields", tableName);
        Object data = result.getData();
        Schema schema = new Schema.Parser().parse(JsonMapper.toJsonString(data));
        Schema.Field field = schema.getField(fieldName);
        if (StringUtil.isEmpty(field)) {
            return false;
        }
        return true;
    }

    /**
     * 解析where判断是否可以进行子查询(子表方式)
     * @param where
     * @param masterTableName 主表名
     * @param slaveTableName 从表名
     * @return
     */
    private void judgeOfSlaveQuery(Expression where, String masterTableName, String slaveTableName) {
        if (StringUtil.isEmpty(masterTableName) || StringUtil.isEmpty(slaveTableName)) {
            canSubQueryOfSlaveTable = false;
            return;
        }
        if (where instanceof InExpression) {
            InExpression whereIn = (InExpression) where;
            ItemsList rightItemsList = whereIn.getRightItemsList();
            if (rightItemsList instanceof SubSelect) {
                canSubQueryOfSlaveTable = false;
                return;
            }
            String left;
            if (!StringUtil.isEmpty(whereIn.getLeftExpression())) {
                left = String.valueOf(whereIn.getLeftExpression());
            } else {
                left = String.valueOf(whereIn.getLeftItemsList());
            }
            if (!isField(masterTableName, left) || !isField(slaveTableName, left)) {
                canSubQueryOfSlaveTable = false;
                return;
            }
        } else if (where instanceof AndExpression) {
            AndExpression whereAnd = (AndExpression) where;
            Expression leftExpression = whereAnd.getLeftExpression();
            Expression rightExpression = whereAnd.getRightExpression();
            judgeOfSlaveQuery(leftExpression, masterTableName, slaveTableName);
            judgeOfSlaveQuery(rightExpression, masterTableName, slaveTableName);

        } else if (where instanceof OrExpression) {
            OrExpression whereOr = (OrExpression) where;
            Expression leftExpression = whereOr.getLeftExpression();
            Expression rightExpression = whereOr.getRightExpression();
            judgeOfSlaveQuery(leftExpression, masterTableName, slaveTableName);
            judgeOfSlaveQuery(rightExpression, masterTableName, slaveTableName);
        } else if (where instanceof ComparisonOperator) {
            ComparisonOperator whereComparisonOperator = (ComparisonOperator) where;
            String leftExpression = String.valueOf(whereComparisonOperator.getLeftExpression());
            String rightExpression = String.valueOf(whereComparisonOperator.getRightExpression());
            if (isField(masterTableName, leftExpression) && !isField(slaveTableName, leftExpression)) {
                canSubQueryOfSlaveTable = false;
                return;
            }
            if (isField(masterTableName, rightExpression) && !isField(slaveTableName, rightExpression)) {
                canSubQueryOfSlaveTable = false;
                return;
            }
            if (!isField(masterTableName, leftExpression) && !isField(masterTableName, rightExpression) && !leftExpression.equalsIgnoreCase(rightExpression)) {
                canSubQueryOfSlaveTable = false;
                return;
            }
        } else {
            canSubQueryOfSlaveTable = false;
            return;
        }
    }
}

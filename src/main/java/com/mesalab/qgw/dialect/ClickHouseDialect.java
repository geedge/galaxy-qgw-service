package com.mesalab.qgw.dialect;


import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.*;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.*;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.model.api.EngineConfigSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
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
public class ClickHouseDialect extends  AbstractDataSourceDialect{
    Pattern pGlobalIn = Pattern.compile("\\s+in\\s*\\(", Pattern.CASE_INSENSITIVE);
    Pattern pUniq = Pattern.compile("count\\s*\\(\\s*distinct\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE);
    Pattern pUniqCombined = Pattern.compile("count\\s*\\(\\s*distinct\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE);
    Pattern pWhere = Pattern.compile("where\\s*", Pattern.CASE_INSENSITIVE);
    Pattern pCount = Pattern.compile("\\bcount\\s*\\(", Pattern.CASE_INSENSITIVE);
    Pattern pCount1 = Pattern.compile("count\\(\\s*1\\s*\\)", Pattern.CASE_INSENSITIVE);
    private ClickHouseHttpSource clickHouseHttpSource = (ClickHouseHttpSource) SpringContextUtil.getBean("clickHouseHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");
    private MetadataService metadataService = (MetadataService) SpringContextUtil.getBean("metadataService");
    private EngineConfigSource engineConfigSource = (EngineConfigSource) SpringContextUtil.getBean("engineConfigSource");



    public ClickHouseDialect(ApiParam param) {
        initialize(param);
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
    public String findBestQuery() {
        return new Optimizer().generateQueryLimit().
                generateSQL().generateUniq().generateSubquery().build();
    }



  class Converter {
    String sql = null;

    public Converter(String sql) {
        this.sql = sql;
    }

   /**
    * 兼容标准SQL语言（支持CK19.14.6.12 新特性）：
    * 1.子查询必须加表别名
    * 2.不支持 count(1) 需转为count(*)
    * @return
    */
    public Converter generateStandard(){
        sql = generateCount(sql);
        SelectBody body = generateSubqueryAlias(sql);
        if (StringUtil.isNotEmpty(body)) {
            sql = body.toString();
        }
        return this;
    }

    public Converter generateDateFunction() {
        sql = SQLFunctionUtil.generateDateFunction(sql, DBTypeEnum.CLICKHOUSE.getValue());

        return this;
    }

      /**
       * http GET  sql 里特殊符号需要做encode编码
       * @return
       */
      public Converter generateSqlEncode() {
         sql = Encodes.urlEncode(sql);
         return this;
      }

      public String build() {
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
              throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "参数校验：SQL 解析错误", e);

          }

          return resultSelectBody;

      }

      /**
       * 子查询别名处理
       * @param where
       */
      private void setInSubqueryAlias(Expression where) {
          if (where instanceof InExpression) {
              InExpression whereIn = (InExpression) where;
              ItemsList rightItemsList = whereIn.getRightItemsList();
              if (rightItemsList instanceof SubSelect) {
                  SubSelect sub = (SubSelect) rightItemsList;
                  SelectBody selectBody = sub.getSelectBody();
                  PlainSelect plainSelect = (PlainSelect) selectBody;
                  FromItem fromItem = plainSelect.getFromItem();
                  if (fromItem instanceof Table) {
                      Table table = (Table) fromItem;
                      if (StringUtil.isEmpty(table.getAlias())) {
                          Alias alias = new Alias(table.getName());
                          plainSelect.getFromItem().setAlias(alias);
                      }
                  } else if (fromItem instanceof SubSelect) {
                      SubSelect subSelect = (SubSelect) fromItem;
                      generateSubqueryAlias(subSelect.getSelectBody().toString());
                  }
                  if (StringUtil.isNotEmpty(plainSelect.getWhere())) {
                      setInSubqueryAlias(plainSelect.getWhere());
                  }
              }
          } else if (where instanceof AndExpression) {
              AndExpression whereAnd = (AndExpression) where;
              Expression leftExpression = whereAnd.getLeftExpression();
              setInSubqueryAlias(leftExpression);
              Expression rightExpression = whereAnd.getRightExpression();
              setInSubqueryAlias(rightExpression);
          } else if (where instanceof OrExpression) {
              OrExpression whereOr = (OrExpression) where;
              Expression leftExpression = whereOr.getLeftExpression();
              setInSubqueryAlias(leftExpression);
              Expression rightExpression = whereOr.getRightExpression();
              setInSubqueryAlias(rightExpression);
          }
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


    private  void setDefaultResultRows(SQLQuerySource sqlQuerySource, int defaultResultRows) {
        if (StringUtil.isBlank(sqlQuerySource.getLimit())) {
            sqlQuerySource.setSqlBody(sqlQuerySource.getSqlBody() + " limit " + defaultResultRows);
            sqlQuerySource.setLimit(String.valueOf(defaultResultRows));
        }

    }

      /**
       * 设置最大缓存数据集
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

      String orginInnerSql = innerSqlQuerySource.getSqlBody();
      String limitInnerSql = innerSqlQuerySource.getSqlBody();

      if (StringUtil.isNotBlank(innerSqlQuerySource.getLimit())) {

          String[] limits = innerSqlQuerySource.getLimit().split(",");

          if (Integer.valueOf(limits[limits.length-1]) > maxCacheResultRows) {
              limits[limits.length-1] = String.valueOf(maxCacheResultRows);

             if (limits.length == 2 ) {
                 limitInnerSql = SQLHelper.getLimitString(limitInnerSql, Integer.valueOf(limits[0]), Integer.valueOf(limits[1]));
             } else {
                 limitInnerSql = SQLHelper.getLimitString(limitInnerSql,0, Integer.valueOf(limits[0]));
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
          sqlQuerySource.setSqlBody(sqlQuerySource.getSqlBody().replace(orginInnerSql, limitInnerSql));
      }

  }

    public Optimizer generateSQL() {
        SQLQuerySource sqlQuerySource = param.getDbQuerySource();
        sql = sqlQuerySource.getSqlBody();
        return this;
    }

      /**
       * count(distinct())转为uniq函数
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
       * IN 转换为 集群 Global IN 模式
       * @return
       */
      public Optimizer generateGlobalIn() {
          Matcher m = pGlobalIn.matcher(sql);
          StringBuffer sb = new StringBuffer();
          while (m.find()) {
              m.appendReplacement(sb, " global in (");
          }

          m.appendTail(sb);
          sql = sb.toString();
          return this;
      }

      /**
       * count(distinct())转为uniqCombined函数
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
       * 事件索引子查询，目的提高日志查询检索性能:
       * 方式1.基于索引表
       * 方式2.基于分区键
       * 限制：
       * 1. 仅适用一级普通查询，带where （无group by 统计）的查询
       * 2. 不对count 统计进行生成
       * @return
       */
     public Optimizer generateSubquery() {

         if (StringUtil.isBlank(param.getDbQuerySource().getExpr())) {
             return this;
         }

         if (StringUtil.isNotEmpty(param.getDbQuerySource().getGroupByElement())) {
             return this;
         }

         if (pCount.matcher(param.getDbQuerySource().getSelectItems()).find()) {
             return this;
         }

         String indexTable = getAvaliableIndexTable();
         if (StringUtil.isNotBlank(indexTable)) {
             sql = getSubqueryByIndexTable(indexTable);
         } else {
             sql = getSubqueryByPartitionKey(sql);
         }
         return this;
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
              if (!param.getDbQuerySource().getExpr().contains(indexKey)) {
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
              setValidityByWhereExpressionsInSchema(param.getDbQuerySource().getWhereExpression(), masterTableSchema, indexTableSchema);
              if (getSubIndexValidFlag() && isExist(indexTableSchema.getFullName())) {
                  indexTable = indexTableSchema.getFullName();
                  break;
              }
          }
          return indexTable;
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
          sb.append("select ").append(param.getDbQuerySource().getSelectItems()).append(" from ").append(param.getTableName()).append(" where ").append(primaryKey).append(" in ( ").append(subQuery).append(")");
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
       * 获取子sql
       * @param columnName
       * @param tableName
       * @return
       */
      private StringBuffer getSubquerySql(String columnName, String tableName) {
          StringBuffer subQuery = new StringBuffer();

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
                  if(StringUtil.isNotBlank(originalField)){
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
       * @param tableName
       * @return
       */
      private boolean isExist(String tableName) {
          Map<String, String> result = executeHttpGet(String.format("select 1 from %s limit 1", tableName));
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

      private void  setSubIndexValidFlag(Boolean subIndexValidFlag) {
          this.subIndexValidFlag = subIndexValidFlag;
      }

      private Boolean getSubIndexValidFlag() {
          return subIndexValidFlag;
      }

      public String build() {
          return sql;
      }



}



    @Override
    public Dialect executeQuery() {
        generateBaseResult(convertQuery(findBestQuery()));
        return this;
    }

    @Override
    public Dialect executeAdministrativeQuery() {
        generateBaseResult(param.getQuery());
        return this;
    }


    @Override
    public Dialect executeSyntaxCheck() {
        String sql = convertQuery(getSyntaxCheckSql());
        log.info("option=syntax-check:  original sql: {}, after transformation(return message) info: {}", param.getQuery(), sql);

        Map<String, String> results = executeHttpGet(sql);
        if (results.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), ResultCodeEnum.EXECUTE_SUCCESS.getCode(), Encodes.urlDecode(sql),
                    JsonMapper.fromJsonString(results.get("result"), Map.class), null, null, QueryFormatEnum.JSON.getValue());
        } else {
            baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(), results.get("message"),
                    results.get("result"), null, null, param.getFormat());
        }
        return this;
    }

    private void generateBaseResult(String sql) {

        Map<String, String> results = executeHttpGet(sql);
        if (results.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), ResultCodeEnum.EXECUTE_SUCCESS.getCode(), "ok",
                    JsonMapper.fromJsonString(results.get("result"), Map.class), null, null, QueryFormatEnum.JSON.getValue());
        } else {
            baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(), results.get("message"),
                    results.get("result"), null, null, param.getFormat());
        }
    }


    @Override
    public BaseResult build() {

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
            baseResult.setData(resultMap.get("data"));
            baseResult.setMeta(resultMap.get("meta"));
            baseResult.setStatistics(statistics);
        } else if (baseResult.getStatus().equals(HttpStatus.SC_NOT_FOUND)) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND,
                    ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), StringUtil.isEmpty(dataObject) ? "System Runtime Exception, Please contact Galaxy Data Platform Project manager" : String.valueOf(dataObject));
        } else if (baseResult.getStatus().equals(HttpStatus.SC_INTERNAL_SERVER_ERROR)) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(),
                    StringUtil.isEmpty(dataObject) ? "System Runtime Exception, Please contact Galaxy Data Platform Project manager" : String.valueOf(dataObject));
        } else {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(),
                    StringUtil.isEmpty(dataObject) ? "System Runtime Exception, Please contact Galaxy Data Platform Project manager " : String.valueOf(dataObject));
        }
        return baseResult;
    }



    /**
     * 获取语法检测sql
     *
     * @return
     */
    private String getSyntaxCheckSql() {
        String sql = param.getDbQuerySource().getSqlBody();
        try {
            sql = String.valueOf(parserAndUpdateSql(sql));
        } catch (Exception e) {
            log.error("syntax-check sql error {}, execute original sql: {}, error is: {} ",
                    sql, sql = param.getDbQuerySource().getSqlBody(), e.getMessage() == null ? e.getCause() : e.getMessage());
            e.printStackTrace();
        }
        return sql;
    }


    /**
     * 做数据集限制: 将最内层sql的表名, 替换成(select * from tableName limit XXX) as alias
     *   涉及from 和 where中存在子查询
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
                tableName, 10000, alias);
        Statement parse = CCJSqlParserUtil.parse(sql);
        Select select = (Select) parse;
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        return plainSelect.getFromItem();
    }

    /**
     * ClickHouse Http Query Interface
     * @param sql SQL语句
     * @return
     */
    private Map<String, String> executeHttpGet(String sql) {
        StringBuilder urlBuilder = new StringBuilder("http://")
                .append(clickHouseHttpSource.getUrl()).append("/?");

        String queryParameter = buildHttpQueryParameter(sql);

        log.info("DB Engine is :{}, Execute Http Query is: {}", DBTypeEnum.CLICKHOUSE.getValue(), urlBuilder.toString() +
                Encodes.urlDecode(queryParameter));

        List<NameValuePair> values = URLEncodedUtils.parse(queryParameter, Charset.forName("UTF-8"));

        return httpClientService.httpGet(urlBuilder.toString() + URLEncodedUtils.format(values, "utf-8"));
    }

    private String buildHttpQueryParameter(String sql) {

        StringBuilder queryParameterBuilder = new StringBuilder(1024);

        String dbName = metadataService.getDBNameByTableName(param.getTableName());

        if (dbName.equalsIgnoreCase(clickHouseHttpSource.getSystemDBName())) {
            queryParameterBuilder.append("user=")
                    .append(clickHouseHttpSource.getLongTermAccount().getUsername()).append("&")
                    .append("password=").append(clickHouseHttpSource.getLongTermAccount().getPassword()).append("&")
                    .append("database=").append(dbName).append("&")  ;

        }  else if (QueryOptionEnum.LONG_TERM.getValue().equalsIgnoreCase(param.getOption())) {

            queryParameterBuilder.append("user=")
                    .append(clickHouseHttpSource.getLongTermAccount().getUsername()).append("&")
                    .append("password=").append(clickHouseHttpSource.getLongTermAccount().getPassword()).append("&")
                    .append("database=").append(dbName).append("&")
                    .append("query_id=").append(ReportCacheUtils.getQueryId(param.getResultId(), param.getQuery())).append("&");
        } else {
            queryParameterBuilder.append("user=")
                    .append(clickHouseHttpSource.getRealTimeAccount().getUsername()).append("&")
                    .append("password=").append(clickHouseHttpSource.getRealTimeAccount().getPassword()).append("&")
                    .append("database=").append(dbName).append("&");
        }


        queryParameterBuilder.append("query=").append(sql)
                .append(" FORMAT ").append(QueryFormatEnum.JSON.getValue().toUpperCase()).append(" ;");
        return queryParameterBuilder.toString();
    }

}

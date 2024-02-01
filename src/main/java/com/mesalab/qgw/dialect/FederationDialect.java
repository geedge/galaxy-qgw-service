package com.mesalab.qgw.dialect;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.calcite.CalciteConnectionProcessor;
import com.mesalab.common.enums.DBEngineType;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.common.utils.sqlparser.SQLFunctionUtil;
import com.mesalab.common.utils.sqlparser.SampleSQLHelper;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.enums.OutputMode;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.common.utils.sqlparser.ExpressionColumnCollectAdapter;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.EngineConfigSource;
import com.mesalab.qgw.model.basic.SQLQueryContext;
import com.mesalab.qgw.model.basic.SelectStatement;
import com.mesalab.qgw.model.basic.udf.UDF;
import com.geedgenetworks.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;


import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 联邦查询-基于Calcite支持多源联合查询
 */
@Slf4j
public class FederationDialect extends AbstractEngineDialect {
    private Stopwatch watch = Stopwatch.createUnstarted();
    private static final String META_CATEGORY = "category";
    private static final String META_CATEGORY_METRIC = "Metric";
    private static final String META_CATEGORY_DIMENSION = "Dimension";
    private final EngineConfigSource engineConfigSource = (EngineConfigSource) SpringContextUtil.getBean("engineConfigSource");

    private static final Map<String, String> metaMap = Maps.newHashMap();
    public SQLQueryContext sqlQueryContext;
    public BaseResult baseResult;

    static {
        metaMap.put("BIGINT", DataTypeMapping.LONG);
        metaMap.put("DOUBLE", DataTypeMapping.DOUBLE);
        metaMap.put("VARCHAR", DataTypeMapping.STRING);
    }

    public FederationDialect(SQLQueryContext sqlQueryContext, BaseResult result) {
        this.sqlQueryContext = sqlQueryContext;
        this.baseResult = result;
    }

    @Override
    public void init() {
        if (StringUtil.isNotEmpty(sqlQueryContext.getFederationSelectStatement())) {
            sqlQueryContext.getFederationSelectStatement().setExecSQL(sqlQueryContext.getFederationSelectStatement().getSqlBody());
        } else {
            SelectStatement fdSelectStatement = new SelectStatement();
            fdSelectStatement.setExecSQL(null);
            sqlQueryContext.setFederationSelectStatement(fdSelectStatement);
        }
    }

    @Override
    public String getFederateQuery() {
        return sqlQueryContext.getFederationSelectStatement().getExecSQL();
    }

    @Override
    public String getSampleQuery() {
        String sql = sqlQueryContext.getFederationSelectStatement().getExecSQL();
        if (!isExecute()) {
            return sql;
        }
        try {
            sql = new SampleSQLHelper(sql, null, false, engineConfigSource.getDefaultResultNum()).build();
        } catch (JSQLParserException e) {
            log.error("syntax-check sql error {}, execute original sql: {}, error is: {} ",
                    sql, sql = sqlQueryContext.getDbSelectStatement().getSqlBody(), e.getMessage() == null ? e.getCause() : e.getMessage());
        }
        return sql;
    }

    @Override
    public String getSyntaxCheckQuery() {
        String sql = sqlQueryContext.getFederationSelectStatement().getExecSQL();
        if (!isExecute()) {
            return sql;
        }
        return SQLHelper.getLimitString(sql, 0, 0);
    }

    @Override
    public boolean supportsLimit() {
        return false;
    }

    @Override
    final public BaseResult executeKillQuery(String queryId) {
        return null;
    }

    @Override
    final public BaseResult getProcesses(String queryId) {
        return null;
    }

    @Override
    public boolean isExecute() {
        return StringUtil.isNotEmpty(sqlQueryContext.getFederationSelectStatement().getExecSQL());
    }


    @Override
    public BaseResult generateBaseResult(String sql) {
        try {
            watch.start();
            if (isExecute()) {
                log.info("DB engine is :{}, execute query is: {}", DBEngineType.QGW.getValue(), JSON.toJSONString(sql));
                SelectStatement fdSelectStatement = sqlQueryContext.getFederationSelectStatement();
                Map resultMap = CalciteConnectionProcessor.getInstance().executeMemoryQuery(fdSelectStatement.getTableNames().get(0),
                        (List<Map<String, String>>) baseResult.getMeta(),
                        (List<Map<String, Object>>) baseResult.getData(), sql);
                baseResult.setData(resultMap.get("data"));
                baseResult.setMeta(unifyMeta(resultMap.get("meta")));
            }
            log.debug("Federation query elapsed time : {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
        } catch (RuntimeException ex) {
            log.error("Federation query error:{}", ex);
            throw new QGWBusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(),
                    CommonErrorCode.SQL_FEDERATION_QUERY_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.SQL_FEDERATION_QUERY_EXCEPTION.getMessage(), ex.getMessage()));
        }
        return build();
    }


    private BaseResult build() {

        long executeTime = watch.elapsed(TimeUnit.MILLISECONDS);
        Map<String, Object> statisticMap = (Map<String, Object>) baseResult.getStatistics();
        statisticMap.put("elapsed", Long.parseLong(String.valueOf(statisticMap.get("elapsed"))) + executeTime);
        if (StringUtil.isEmpty(baseResult.getData())) {
            statisticMap.put("result_bytes", 0);
            statisticMap.put("result_rows", 0);
        } else {
            statisticMap.put("result_bytes", JSON.toJSONBytes(baseResult.getData(), JSONWriter.Feature.LargeObject).length);
            statisticMap.put("result_rows", ((List) baseResult.getData()).size());
        }
        extendMetaInfo(baseResult.getMeta());
        if (sqlQueryContext.getFormat().equalsIgnoreCase(OutputMode.CSV.getValue())) {
            convertJsonToCSV();
        }
        baseResult.setFormatType(sqlQueryContext.getFormat());
        log.debug("Federation execute and build result time : {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
        return baseResult;
    }

    private void extendMetaInfo(Object meta) {
        if (StringUtil.isEmpty(meta)) {
            return;
        }
        List<Map<String, Object>> data = JsonPath.read(meta, "$");
        SelectStatement querySource = StringUtil.isEmpty(sqlQueryContext.getFederationSelectStatement()) || StringUtil.isEmpty(sqlQueryContext.getFederationSelectStatement().getExecSQL()) ? sqlQueryContext.getDbSelectStatement() : sqlQueryContext.getFederationSelectStatement();
        Map<String, String> groupDimension = querySource.getGroupDimension();
        Map<String, String> aliasFields = querySource.getAliasFields();
        meta:
        for (Map<String, Object> map : data) {
            String field = String.valueOf(map.get("name"));
            if (StrUtil.isEmptyIfStr(groupDimension)) {
                map.put(META_CATEGORY, META_CATEGORY_METRIC);
                continue;
            }
            for (String dimensionKey : groupDimension.keySet()) {
                if (Objects.equals(field, dimensionKey) || Objects.equals(field, groupDimension.get(dimensionKey))) {
                    map.put(META_CATEGORY, META_CATEGORY_DIMENSION);
                    continue meta;
                }
                try {
                    String selectItemExpr = aliasFields.get(field) == null ? SQLFunctionUtil.generateDateFunction(field, sqlQueryContext.getDbEngine()) : aliasFields.get(field);
                    String dimensionExpr = groupDimension.get(dimensionKey) == null ? SQLFunctionUtil.generateDateFunction(dimensionKey, sqlQueryContext.getDbEngine()) : groupDimension.get(dimensionKey);
                    if (dimensionExpr != null && selectItemExpr != null) {
                        ExpressionColumnCollectAdapter selectItemAdapter = new ExpressionColumnCollectAdapter();
                        Expression expr1 = CCJSqlParserUtil.parseExpression(selectItemExpr);
                        expr1.accept(selectItemAdapter);
                        Set<String> selectItemColumn = selectItemAdapter.getColumns();

                        ExpressionColumnCollectAdapter dimensionAdapter = new ExpressionColumnCollectAdapter();
                        Expression expr2 = CCJSqlParserUtil.parseExpression(dimensionExpr);
                        expr2.accept(dimensionAdapter);
                        Set<String> dimensionColumn = dimensionAdapter.getColumns();

                        boolean hasDuplicates = selectItemColumn.stream().anyMatch(dimensionColumn::contains);
                        if (hasDuplicates) {
                            map.put(META_CATEGORY, META_CATEGORY_DIMENSION);
                            continue meta;
                        }
                    }
                } catch (JSQLParserException e) {
                    log.warn("build meta error, message is: {}", e.getMessage());
                }
            }
            map.put(META_CATEGORY, META_CATEGORY_METRIC);
        }
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
            x.put("type", metaMap.get(type) == null ? DataTypeMapping.STRING : metaMap.get(type));
        });
        return metaList;
    }

    @Override
    public void executeUDF() {
        SelectStatement sqlQuerySource = sqlQueryContext.getDbSelectStatement();
        Set<UDF> udfSet = sqlQuerySource.getUdfSet();
        for (UDF udf : udfSet) {
            List<Map<String, String>> targetResult = (List<Map<String, String>>) udf.execute(sqlQuerySource,
                    baseResult);
            baseResult.setData(targetResult);
        }
    }


    private void convertJsonToCSV() {

        List<String> results = Lists.transform((List<Map<String, Object>>) baseResult.getData(), new Function<Map<String, Object>, String>() {

            @Override
            public String apply(@Nullable Map<String, Object> input) {
                return Joiner.on(",").useForNull("").join(input.values());
            }
        });

        List<Object> metas = Lists.transform((List<Map<String, Object>>) baseResult.getMeta(), new Function<Map<String, Object>, Object>() {

            @Override
            public Object apply(@Nullable Map<String, Object> input) {
                return input.get("name");
            }
        });

        baseResult.setData(Joiner.on("\n").useForNull("").join(results));
        baseResult.setMeta(Joiner.on(",").useForNull("").join(metas));
    }


    private Expression generateDatasetWhereSql(String where) throws JSQLParserException {
        String sql = String.format("SELECT * FROM tableName WHERE  %s %s ", where, "OR 1=1");
        Statement parse = CCJSqlParserUtil.parse(sql);
        Select select = (Select) parse;
        PlainSelect selectBody = (PlainSelect) select.getSelectBody();
        return selectBody.getWhere();
    }

}

package com.mesalab.qgw.dialect;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.calcite.CalciteMemoryUtils;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.qgw.utils.sqlparser.SQLHelper;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.qgw.model.api.udf.UDF;
import com.zdjizhi.utils.StringUtil;
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
public class FederationDialect extends AbstractEngineDialect{
    private Stopwatch watch = Stopwatch.createUnstarted();
    private static final Map<String, String> metaMap = Maps.newHashMap();
    public ApiParam param;
    public BaseResult baseResult;

    static {
        metaMap.put("BIGINT", DataTypeMapping.LONG);
        metaMap.put("DOUBLE", DataTypeMapping.DOUBLE);
        metaMap.put("VARCHAR", DataTypeMapping.STRING);
    }

    public FederationDialect(ApiParam param, BaseResult result) {
        this.param = param;
        this.baseResult = result;
    }

    @Override
    public void init() {
        if (StringUtil.isNotEmpty(param.getEngineQuerySource())) {
            param.setSql(param.getEngineQuerySource().getSqlBody());
        } else {
            param.setSql(null);
        }
    }

    @Override
    public String getFederateQuery() {
        return param.getSql();
    }

    @Override
    public String getSampleQuery() {
        String sql = param.getSql();
        if (!isExecute()) {
            return sql;
        }
        try {
            sql = String.valueOf(convertSampleSql(sql));
        } catch (JSQLParserException e) {
            log.error("syntax-check sql error {}, execute original sql: {}, error is: {} ",
                    sql, sql = param.getDbQuerySource().getSqlBody(), e.getMessage() == null ? e.getCause() : e.getMessage());
        }
        return sql;
    }

    @Override
    public String getSyntaxCheckQuery() {
        String sql = param.getSql();
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
    final public BaseResult getProcesses(String queryId){
        return null;
    }

    @Override
    public boolean isExecute() {
        return StringUtil.isNotEmpty(param.getEngineQuerySource());
    }


    @Override
    public BaseResult generateBaseResult(String sql) {
        try {
            watch.start();
            if (isExecute()) {
                SQLQuerySource sqlQuerySource = param.getEngineQuerySource();
                Map resultMap = CalciteMemoryUtils.executeMemoryQuery(sqlQuerySource.getTableName(),
                        (List<Map<String, String>>) baseResult.getMeta(),
                        (List<Map<String, Object>>) baseResult.getData(), sql);
                baseResult.setData(resultMap.get("data"));
                baseResult.setMeta(unifyMeta(resultMap.get("meta")));
            }
            log.debug("Calcite execute time :" + watch.elapsed().toMillis() / 1000 + "s");

        } catch (RuntimeException ex) {
            log.error("Engine execute memory-query error:{}", ex);
            throw new QGWBusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),
                    QGWErrorCode.SQL_QUERY_FEDERATION_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_QUERY_FEDERATION_EXCEPTION.getMessage(),ex.getMessage()));
        }
        return build();
    }



    private BaseResult build() {

        long executeTime = watch.elapsed(TimeUnit.MILLISECONDS);
        Map<String, Object> statisticMap = (Map<String, Object>) baseResult.getStatistics();
        statisticMap.put("elapsed", Long.parseLong(String.valueOf(statisticMap.get("elapsed"))) + executeTime);
        if (StringUtil.isEmpty(baseResult.getData())) {
            statisticMap.put("result_size", 0);
            statisticMap.put("result_rows", 0);
        } else {
            statisticMap.put("result_size", baseResult.getData().toString().getBytes().length);
            statisticMap.put("result_rows", ((List) baseResult.getData()).size());
        }
        extendMetaInfo(baseResult.getMeta());
        if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.CSV.getValue())) {
            convertJsonToCSV();
        }
        baseResult.setFormatType(param.getFormat());
        log.debug("Execute build Time : {}",  watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0);
        return baseResult;
    }

    private void extendMetaInfo(Object meta) {
        if (StringUtil.isEmpty(meta)) {
            return;
        }
        List<Map<String, Object>> data = JsonPath.read(meta, "$");
        SQLQuerySource querySource = StringUtil.isEmpty(param.getEngineQuerySource()) ? param.getDbQuerySource() : param.getEngineQuerySource();
        Map<String, String> groupDimension = querySource.getGroupDimension();
        for (Map<String, Object> map : data) {
            String field = String.valueOf(map.get("name"));
            if (StringUtil.isNotEmpty(groupDimension) && ((groupDimension.containsKey(field) || groupDimension.containsValue(field)))) {
                map.put("category", "Dimension");
            } else {
                map.put("category", "Metric");
            }
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
        SQLQuerySource sqlQuerySource = param.getDbQuerySource();
        Set<UDF> udfSet = sqlQuerySource.getUdfSet();
        for (UDF udf : udfSet) {
            List<Map<String, String>> targetResult = (List<Map<String, String>>) udf.execute(sqlQuerySource,
                    (List<Object>) baseResult.getData());
            baseResult.setData(targetResult);
        }
    }


    private void convertJsonToCSV() {

        List<String> results = Lists.transform( (List<Map<String, Object>>) baseResult.getData(), new Function<Map<String, Object>, String>() {

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

    /**
     * Desc: where、having至无效：添加 OR 1=1
     *
     * @param sql
     * @return {@link SelectBody}
     * @created by wWei
     * @date 2022/4/27 5:18 下午
     */
    private SelectBody convertSampleSql(String sql) throws JSQLParserException {
        Statement parse = CCJSqlParserUtil.parse(sql);
        if (parse instanceof Select) {
            Select select = (Select) parse;
            SelectBody selectBody = select.getSelectBody();
            if (selectBody instanceof PlainSelect) {
                PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
                FromItem fromItem = plainSelect.getFromItem();
                if (fromItem instanceof SubSelect) {
                    SubSelect subSelect = (SubSelect) plainSelect.getFromItem();
                    subSelect.setSelectBody(convertSampleSql(subSelect.getSelectBody().toString()));
                }
                if (StringUtil.isNotEmpty(plainSelect.getWhere())) {
                    plainSelect.setWhere(generateDatasetWhereSql(plainSelect.getWhere().toString()));
                }
                if (StringUtil.isNotEmpty(plainSelect.getHaving())) {
                    plainSelect.setHaving(generateDatasetWhereSql(plainSelect.getHaving().toString()));
                }
                return plainSelect;
            } else if (selectBody instanceof SetOperationList) {
                SetOperationList setOperationList = (SetOperationList) selectBody;
                List<SelectBody> selects = setOperationList.getSelects();
                for (int i = 0; i < selects.size(); i++) {
                    selects.set(i, convertSampleSql(selects.get(i).toString()));
                }
                return setOperationList;
            } else {
                throw new QGWBusinessException(ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.SQL_PARSE_ONLY_SUPPORT_SELECT));
            }
        }
        throw new QGWBusinessException(ResultCodeEnum.PARAMETER_ERROR.getCode(),
                String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.SQL_PARSE_ONLY_SUPPORT_SELECT));
    }

    private Expression generateDatasetWhereSql(String where) throws JSQLParserException {
        String sql = String.format("SELECT * FROM tableName WHERE  %s %s ", where, "OR 1=1");
        Statement parse = CCJSqlParserUtil.parse(sql);
        Select select = (Select) parse;
        PlainSelect selectBody = (PlainSelect) select.getSelectBody();
        return selectBody.getWhere();
    }

}

package com.mesalab.qgw.dialect;

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
import com.mesalab.common.utils.sqlparser.*;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.enums.OutputMode;
import com.mesalab.qgw.constant.MetaConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.EngineConfigSource;
import com.mesalab.qgw.model.basic.SQLQueryContext;
import com.mesalab.qgw.model.basic.SelectStatement;
import com.mesalab.qgw.model.basic.udf.UDF;
import com.geedgenetworks.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;


import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 联邦查询-基于Calcite支持多源联合查询
 */
@Slf4j
public class FederationDialect extends AbstractEngineDialect {
    private Stopwatch watch = Stopwatch.createUnstarted();
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
        Map<String, Object> statisticMap = (Map<String, Object>) baseResult.getStatistics();
        if (StringUtil.isEmpty(baseResult.getData())) {
            statisticMap.put("result_bytes", 0);
            statisticMap.put("result_rows", 0);
        } else {
            statisticMap.put("result_bytes", JSON.toJSONBytes(baseResult.getData(), JSONWriter.Feature.LargeObject).length);
            statisticMap.put("result_rows", ((List<?>) baseResult.getData()).size());
        }
        baseResult.setMeta(ColumnCategoryHelper.expandMetaCategory(baseResult.getMeta(), sqlQueryContext.getFederationSelectStatement(), null));
        if (baseResult.getMeta() != null
                && baseResult.getData() != null
                && ((List<Map<String, Object>>) baseResult.getMeta()).stream().anyMatch(x -> DataTypeMapping.LONG.equalsIgnoreCase(String.valueOf(x.get("type"))))) {

            List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
            for (int i = 0; i < data.size(); i++) {
                Map<String, Object> map = data.get(i);
                String jsonString = JSON.toJSONString(map, JSONWriter.Feature.WriteNulls, JSONWriter.Feature.BrowserCompatible);
                Map parsedMap = JSON.parseObject(jsonString, LinkedHashMap.class);
                map.clear();
                map.putAll(parsedMap);
            }
            baseResult.setData(data);
        }
        if (baseResult.getMeta() != null) {
            List<Map<String, Object>> metaList = (List<Map<String, Object>>) baseResult.getMeta();
            metaList.forEach(x -> {
                x.remove(MetaConst.META_CATEGORY);
                x.remove(MetaConst.META_DATA_TYPE);
                x.remove(MetaConst.META_FIELD_NAME);
            });

        }
        if (sqlQueryContext.getFormat().equalsIgnoreCase(OutputMode.CSV.getValue())) {
            convertJsonToCSV();
        } else if (sqlQueryContext.getFormat().equalsIgnoreCase(OutputMode.JSON.getValue())) {
            baseResult.setMeta(null);
        } else if (sqlQueryContext.getFormat().equalsIgnoreCase(OutputMode.JSON_COMPACT.getValue())) {
            convertToJsonCompact();
        }
        long executeTime = watch.elapsed(TimeUnit.MILLISECONDS);
        statisticMap.put("elapsed", Long.parseLong(String.valueOf(statisticMap.get("elapsed"))) + executeTime);
        baseResult.setOutputMode(sqlQueryContext.getFormat());
        log.debug("Federation execute and build result time : {} ms", executeTime);
        return baseResult;
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

    private void convertToJsonCompact() {
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) baseResult.getData();
        List<Object> result = dataList.stream()
                .map(Map::values)
                .collect(Collectors.toList());
        baseResult.setData(result);
    }

}

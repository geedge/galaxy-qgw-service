package com.mesalab.qgw.dialect;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.mesalab.calcite.CalciteMemoryUtils;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SQLFunctionUtil;
import com.mesalab.common.utils.SQLHelper;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.DefaultConfigSource;
import com.mesalab.qgw.model.api.DruidIoHttpSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.model.api.udf.UDF;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Alias;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DruidDialect implements  Dialect {

    private DruidIoHttpSource druidIoHttpSource = (DruidIoHttpSource) SpringContextUtil.getBean("druidIoHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");
    private DefaultConfigSource defaultConfigSource = (DefaultConfigSource) SpringContextUtil.getBean("defaultConfigSource");
    private ApiParam param;

    private int pageOffset;

    private BaseResult baseResult;

    private Stopwatch watch = Stopwatch.createUnstarted();

    public DruidDialect(ApiParam param) {

        this.param = param;

        baseResult = BaseResultGenerator.success();

    }

    @Override
    public boolean supportsLimit() {
        return true;
    }

    @Override
    public String getLimitString(String sql, int offset, int limit) {
        StringBuilder stringBuilder = new StringBuilder(sql);
        stringBuilder.append(" limit ");
        stringBuilder.append(limit);
        return stringBuilder.toString();
    }


    private String generateInnerSqlLimit(SQLQuerySource sqlQuerySource) {
        if (StringUtil.isBlank(sqlQuerySource.getLimit())) {
            return getLimitString(sqlQuerySource.getSqlBody(), 0, defaultConfigSource.getMaxNumLimit());
        } else {

            if (sqlQuerySource.getLimit().split(",").length >= 2 ) {
                String[] limits = sqlQuerySource.getLimit().trim().split(",");
                int offset = Integer.valueOf(limits[0].trim());
                int rowNum = Integer.valueOf(limits[1].trim());
                pageOffset = offset;
               return  SQLHelper.removeLimit(sqlQuerySource.getSqlBody()) + " limit " + (offset + rowNum);
            }
            return sqlQuerySource.getSqlBody();
        }

    }

    @Override
    public Dialect queryDefaultPreProcess() {
        param.setQuery(param.getSqlQuerySource().getSqlBody());
        generateSetMaxLimit();
        return this;
    }

    @Override
    public String queryTransformation(String sql) {
        sql = SQLFunctionUtil.generateDateFunction(sql, DBTypeEnum.DRUID.getValue());
        return sql;
    }

    @Override
    public String queryOptimization(String sql) {
        return sql;
    }

    @Override
    public Dialect executeQuery() {
        String query = getExecuteDBSql();

        watch.start();
        StringBuilder urlBuilder = new StringBuilder("http://") .append(druidIoHttpSource.getUrl());
        DruidDialect.DruidQueryParam druidQueryParam = new DruidDialect.DruidQueryParam();
        druidQueryParam.setQuery(query);
        druidQueryParam.getContext().put("sqlTimeZone", druidIoHttpSource.getSqlTimeZone());
        druidQueryParam.getContext().put("skipEmptyBuckets", druidIoHttpSource.getSkipEmptyBuckets());

        log.info("TSDB Query ：{}, SQL: {}", urlBuilder.toString() , param.getSqlQuerySource().getSqlBody());

        if (param.getFormat().equals(QueryFormatEnum.JSON.getValue())) {
            druidQueryParam.setResultFormat("object");
        } else if (param.getFormat().equals(QueryFormatEnum.CSV.getValue())) {
            druidQueryParam.setResultFormat("csv");
        }

        Map<String, String> results = httpClientService.httpPost(urlBuilder.toString(), JsonMapper.toJsonString(druidQueryParam));

        if (!results.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, results.get("result"));
        } else {
            if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.JSON.getValue())) {
                baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), null,
                        (List<Object>) JsonMapper.fromJsonString(results.get("result"), Object.class), null, param.getFormat());
            } else {
                baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), null,
                        results.get("result"), null, param.getFormat());
            }
        }
        return this;

    }

    @Override
    public Dialect executeMemoryQuery() {
        if (baseResult.isSuccess()) {
            if (isNeedMemoryQuery()) {
                try {
                    SQLQuerySource sqlQuerySource = getExecuteDBSqlQuerySourceParent();
                    Alias alias = sqlQuerySource.getSubSelect().getAlias();
                    String tableName = alias == null ? sqlQuerySource.getTableName() : alias.getName();
                    String sql = param.getQuery().replace(sqlQuerySource.getSubSelect().toString(), tableName);
                    Map<String, Object> resultMap = (Map<String, Object>) baseResult.getData();
                    List resultList = CalciteMemoryUtils.calciteQueryMemoryData(tableName, (List<Map<String, String>>) resultMap.get("meta"),
                            (List<Map<String, Object>>) resultMap.get("data"), sql);
                    resultMap.put("data", resultList);
                } catch (Exception ex) {
                    throw new BusinessException(BaseResultEnum.SERVER_ERROR.getCode(), "calcite execute query memory is error: ", ex);
                }
            }
        }
        return this;
    }

    @Override
    public Dialect executeSyntaxCheck() {
        SQLQuerySource sqlQuerySource = getExecuteDBSqlQuerySource();
        String sql = queryTransformation(sqlQuerySource.getSqlBody());
        param.getSqlQuerySource().setSqlBody("select 1");
        executeQuery();
        baseResult.setMessage(sql);
        return this;
    }

    @Override
    public Dialect executeUDF() {

        if (baseResult.isSuccess()) {
            SQLQuerySource sqlQuerySource = getExecuteDBSqlQuerySource();
            Set<UDF> udfSet = sqlQuerySource.getUdfSet();

            List<Object> lists = (List<Object>) baseResult.getData();
            for (UDF udf : udfSet) {
                List<Map<String, String>> targetResult = (List<Map<String, String>>) udf.execute(sqlQuerySource, lists);
                baseResult.setData(targetResult);
            }
        }
        return this;
    }

    @Override
    public BaseResult build() {

        Object dataObject = baseResult.getData();

        if (baseResult.isSuccess()) {
            if (StringUtil.isBlank(baseResult.getMessage())) {
                baseResult.setMessage("ok");
            }
            if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.CSV.getValue())) {
                String result = String.valueOf(dataObject);

                String[] results = result.split("\n");

                if (pageOffset > 0 && StringUtil.isNotEmpty(results)) {
                    if (pageOffset >= results.length) {
                        result = "";
                    } else {
                        String[] subResult = ArrayUtils.subarray(results, pageOffset, results.length);
                        result = Joiner.on("\n").join(subResult).toString();
                    }
                }
                baseResult.setData(result);
            } else if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.JSON.getValue())) {
                List<Object> allResults = (List<Object>) dataObject;
                if (pageOffset > 0 && StringUtil.isNotEmpty(allResults)) {
                    if (pageOffset >= allResults.size()) {
                        allResults = new ArrayList<>();
                    } else {
                        allResults = allResults.subList(pageOffset, allResults.size());
                    }
                }
                Double executeTime = watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0;
                Map<String, Object> statistics = Maps.newHashMap();
                statistics.put("elapsed", String.format("%.2f", executeTime));
                statistics.put("rows_read", allResults.size());

                int resultSize = StringUtil.isEmpty(allResults) ? 0 : JsonMapper.toJsonString(allResults).getBytes().length;
                statistics.put("result_size", resultSize);
                baseResult.setData(allResults);
                baseResult.setStatistics(statistics);
            }
        } else if (baseResult.getStatus().equals(HttpStatus.SC_NOT_FOUND)) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND, dataObject.toString());
        } else if (baseResult.getStatus().equals(HttpStatus.SC_INTERNAL_SERVER_ERROR)) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, dataObject.toString());
        } else {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, dataObject.toString());
        }
        return baseResult;
    }


    /**
     * 设置sqlBody最大查询条数limit限制
     */
    private void generateSetMaxLimit() {
        SQLQuerySource innerSqlQuerySource = param.getSqlQuerySource();
        while (StringUtil.isNotEmpty(innerSqlQuerySource.getSubSelect())) {
            innerSqlQuerySource = innerSqlQuerySource.getSubSqlQuerySources().get(0);
        }
        String sql = generateInnerSqlLimit(innerSqlQuerySource);

        SQLQuerySource sqlQuerySource = param.getSqlQuerySource();
        while (StringUtil.isNotEmpty(sqlQuerySource)) {
            sqlQuerySource.setSqlBody(sqlQuerySource.getSqlBody().replace(innerSqlQuerySource.getSqlBody(), sql));
            if (sqlQuerySource.getSubSqlQuerySources().isEmpty()) {
                break;
            }
            sqlQuerySource = sqlQuerySource.getSubSqlQuerySources().get(0);
        }
    }


    /**
     * 获取在DB中实际执行的sql
     *
     * @return
     */
    private String getExecuteDBSql() {
        SQLQuerySource sqlQuerySource = getExecuteDBSqlQuerySource();
        String sql = sqlQuerySource.getSqlBody();
        sql = queryOptimization(sql);
        sql = queryTransformation(sql);
        return sql;
    }

    /**
     * 判定sql是否存在需要缓存查询
     *
     * @return
     */
    private boolean isNeedMemoryQuery() {
        SQLQuerySource sqlQuerySource = param.getSqlQuerySource();
        while (!sqlQuerySource.isSplitOfMemoryQuery()) {
            if (StringUtil.isEmpty(sqlQuerySource.getSubSelect())) {
                return false;
            } else {
                sqlQuerySource = sqlQuerySource.getSubSqlQuerySources().get(0);
            }
        }
        return true;
    }

    /**
     * 获取实际执行(在DB中)的sqlQuerySource
     *
     * @return
     */
    private SQLQuerySource getExecuteDBSqlQuerySource() {
        SQLQuerySource sqlQuerySource = param.getSqlQuerySource();
        while (true) {
            if (!sqlQuerySource.isSplitOfMemoryQuery()) {
                if (StringUtil.isEmpty(sqlQuerySource.getSubSelect())) {
                    sqlQuerySource = param.getSqlQuerySource();
                    break;
                } else {
                    sqlQuerySource = sqlQuerySource.getSubSqlQuerySources().get(0);
                }
            } else {
                sqlQuerySource = sqlQuerySource.getSubSqlQuerySources().get(0);
                break;
            }
        }
        return sqlQuerySource;
    }

    /**
     * 在存在缓存查询情况下，获取执行sqlQuerySource的上层sqlQuerySource
     *
     * @return
     */
    private SQLQuerySource getExecuteDBSqlQuerySourceParent() {
        SQLQuerySource sqlQuerySource = param.getSqlQuerySource();
        while (StringUtil.isNotEmpty(sqlQuerySource.getSubSelect())) {
            if (sqlQuerySource.isSplitOfMemoryQuery()) {
                return sqlQuerySource;
            } else {
                sqlQuerySource = sqlQuerySource.getSubSqlQuerySources().get(0);
            }
        }
        return null;
    }

    @Data
    class DruidQueryParam {
        private String query;
        private Map<String, String> context = Maps.newHashMap();
        private String resultFormat;

    }

}

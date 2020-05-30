package com.mesalab.qgw.dialect;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.calcite.CalciteMemoryUtils;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.model.api.udf.UDF;
import com.zdjizhi.utils.StringUtil;
import net.sf.jsqlparser.expression.Alias;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 联邦查询-基于Calcite支持多源联合查询
 */
public class FederationDialect extends AbstractEngineDialect{
    private ApiParam param;
    private BaseResult result;
    private Stopwatch watch = Stopwatch.createUnstarted();

    public FederationDialect(ApiParam param, BaseResult result) {
        this.param = param;
        this.result = result;
        executeUDF();
    }


    @Override
    public boolean supportsLimit() {
        return false;
    }

    @Override
    public String getLimitString(String sql, int offset, int limit) {
        return null;
    }

    @Override
    public String convertQuery(String sql) {
        return null;
    }

    @Override
    public String findBestQuery(String sql) {
        return null;
    }

    @Override
    public Dialect queryDefaultPreProcess() {
        if (StringUtil.isNotEmpty(param.getEngineQuerySource())) {
            param.setQuery(param.getEngineQuerySource().getSqlBody());
        }

        return this;
    }

    @Override
    public Dialect executeSyntaxCheck() {
        return null;
    }

    @Override
    public Dialect executeQuery() {
        try {
            watch.start();
            if (result.isSuccess() && StringUtil.isNotEmpty(param.getEngineQuerySource())) {
                SQLQuerySource sqlQuerySource = param.getEngineQuerySource();
                List resultList = CalciteMemoryUtils.calciteQueryMemoryData(sqlQuerySource.getTableName(), (List<Map<String, String>>) result.getMeta(),
                        (List<Map<String, Object>>) result.getData(), param.getQuery());
                result.setData(resultList);
            }
        } catch (Exception ex) {
            throw new BusinessException(BaseResultEnum.SERVER_ERROR.getCode(), "calcite execute query memory is error: ", ex);
        }


        return this;
    }




    @Override
    public BaseResult build() {

        Double executeTime = watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0;
        Map<String, Object> statisticMap = (Map<String, Object>) result.getStatistics();
        statisticMap.put("elapsed", String.format("%.2f", Double.valueOf(statisticMap.get("elapsed").toString()) + executeTime));
        int resultSize = StringUtil.isEmpty(result.getData()) ? 0 : JsonMapper.toJsonString(result.getData()).getBytes().length;
        statisticMap.put("result_size", resultSize);

        return result;
    }



    @Override
    public void executeUDF() {
        if (result.isSuccess()) {
            SQLQuerySource sqlQuerySource = param.getDbQuerySource();
            Set<UDF> udfSet = sqlQuerySource.getUdfSet();
            for (UDF udf : udfSet) {
                List<Map<String, String>> targetResult = (List<Map<String, String>>) udf.execute(sqlQuerySource,
                        (List<Object>) result.getData());
                result.setData(targetResult);
            }
        }
    }



    /**
     * 判定sql是否存在需要缓存查询
     *
     * @return
     */
    private boolean isNeedMemoryQuery() {
        SQLQuerySource sqlQuerySource = param.getDbQuerySource();
        while (!sqlQuerySource.isNeedMemoryQuery()) {
            if (StringUtil.isEmpty(sqlQuerySource.getSubSelect())) {
                return false;
            } else {
                sqlQuerySource = sqlQuerySource.getSubSqlQuerySources().get(0);
            }
        }
        return true;
    }












}

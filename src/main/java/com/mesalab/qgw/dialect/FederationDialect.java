package com.mesalab.qgw.dialect;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.calcite.CalciteMemoryUtils;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.model.api.udf.UDF;
import com.zdjizhi.utils.StringUtil;


import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 联邦查询-基于Calcite支持多源联合查询
 */
public class FederationDialect extends AbstractEngineDialect{
    private Stopwatch watch = Stopwatch.createUnstarted();
    public FederationDialect(ApiParam param, BaseResult result) {
        initialize(param, result);
    }

    @Override
    public boolean supportsLimit() {
        return false;
    }

    @Override
    public Dialect executeSyntaxCheck() {
        return this;
    }

    @Override
    public Dialect executeQuery() {
        try {
            watch.start();
            executeUDF();
            if (baseResult.isSuccess() && StringUtil.isNotEmpty(param.getEngineQuerySource())) {
                SQLQuerySource sqlQuerySource = param.getEngineQuerySource();
                List resultList = CalciteMemoryUtils.calciteQueryMemoryData(sqlQuerySource.getTableName(),
                        (List<Map<String, String>>) baseResult.getMeta(),
                        (List<Map<String, Object>>) baseResult.getData(), param.getQuery());
                baseResult.setData(resultList);
            }
        } catch (Exception ex) {
            throw new BusinessException(BaseResultEnum.SERVER_ERROR.getCode(), "Calcite execute query memory is error: ", ex);
        }

        return this;
    }



    @Override
    public BaseResult build() {
        if (baseResult.isSuccess()) {
            Double executeTime = watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0;
            Map<String, Object> statisticMap = (Map<String, Object>) baseResult.getStatistics();
            statisticMap.put("elapsed", String.format("%.2f", Double.valueOf(statisticMap.get("elapsed").toString()) + executeTime));
            int resultSize = StringUtil.isEmpty(baseResult.getData()) ? 0 : JsonMapper.toJsonString(baseResult.getData()).getBytes().length;
            statisticMap.put("result_size", resultSize);
        }


        return baseResult;
    }


    @Override
    public void executeUDF() {
        if (baseResult.isSuccess()) {
            SQLQuerySource sqlQuerySource = param.getDbQuerySource();
            Set<UDF> udfSet = sqlQuerySource.getUdfSet();
            for (UDF udf : udfSet) {
                List<Map<String, String>> targetResult = (List<Map<String, String>>) udf.execute(sqlQuerySource,
                        (List<Object>) baseResult.getData());
                baseResult.setData(targetResult);
            }
        }
    }


}

package com.mesalab.qgw.dialect;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.mesalab.calcite.CalciteMemoryUtils;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.model.api.udf.UDF;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;


import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 联邦查询-基于Calcite支持多源联合查询
 */
@Slf4j
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
                Map resultMap = CalciteMemoryUtils.calciteQueryMemoryData(sqlQuerySource.getTableName(),
                        (List<Map<String, String>>) baseResult.getMeta(),
                        (List<Map<String, Object>>) baseResult.getData(), param.getQuery());
                baseResult.setData(resultMap.get("data"));
                baseResult.setMeta(resultMap.get("meta"));
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
            statisticMap.put("result_rows", ((List) baseResult.getData()).size());
            if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.CSV.getValue())) {
                convertJsonToCSV();
            }
            baseResult.setFormatType(param.getFormat());
        }
        log.info("Execute build Time : {}",  watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0);
        return baseResult;
    }

    private void convertJsonToCSV() {

        List<String> results = Lists.transform( (List<Map<String, Object>>) baseResult.getData(), new Function<Map<String, Object>, String>() {
            @Nullable
            @Override
            public String apply(@Nullable Map<String, Object> input) {
                return Joiner.on(",").join(input.values());
            }
        });

        List<Object> metas = Lists.transform((List<Map<String, Object>>) baseResult.getMeta(), new Function<Map<String, Object>, Object>() {
            @Nullable
            @Override
            public Object apply(@Nullable Map<String, Object> input) {
                return input.get("name");
            }
        });

        baseResult.setData(Joiner.on("\n").join(results));
        baseResult.setMeta(Joiner.on(",").join(metas));
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

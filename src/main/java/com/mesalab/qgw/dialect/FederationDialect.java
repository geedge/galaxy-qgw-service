package com.mesalab.qgw.dialect;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.mesalab.calcite.CalciteMemoryUtils;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
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
    public ApiParam param;
    public BaseResult baseResult;

    public FederationDialect(ApiParam param, BaseResult result) {
        this.param = param;
        this.baseResult = result;
    }
    @Override
    public void init() {
        if (StringUtil.isNotEmpty(param.getEngineQuerySource())) {
            param.setQuery(param.getEngineQuerySource().getSqlBody());
        }
    }


    @Override
    public boolean supportsLimit() {
        return false;
    }



    @Override
    public boolean isExecuteUDF() {
        return baseResult.isSuccess() ? true : false ;
    }


    @Override
    public BaseResult generateBaseResult() {
        try {
            watch.start();
            if (StringUtil.isNotEmpty(param.getEngineQuerySource())) {
                SQLQuerySource sqlQuerySource = param.getEngineQuerySource();
                Map resultMap = CalciteMemoryUtils.executeMemoryQuery(sqlQuerySource.getTableName(),
                        (List<Map<String, String>>) baseResult.getMeta(),
                        (List<Map<String, Object>>) baseResult.getData(), param.getQuery());
                baseResult.setData(resultMap.get("data"));
                baseResult.setMeta(resultMap.get("meta"));
            }

            log.debug("Calcite execute time :" + watch.elapsed().toMillis()/1000 + "s");
        } catch (Exception ex) {
            throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),
                    ResultCodeEnum.ENGINE_STATISTICS_ERROR.getCode(), "Engine execute memory-query error：", ex);
        }
        return  build();
    }



    private BaseResult build() {

        long executeTime = watch.elapsed(TimeUnit.MILLISECONDS);
        Map<String, Object> statisticMap = (Map<String, Object>) baseResult.getStatistics();
        statisticMap.put("elapsed", Long.parseLong(String.valueOf(statisticMap.get("elapsed"))) + executeTime);
        if (StringUtil.isEmpty(baseResult.getData())) {
            statisticMap.put("result_size", 0);
            statisticMap.put("result_rows", 0);
        } else {
            statisticMap.put("result_size", JsonMapper.toJsonString(baseResult.getData()).getBytes().length);
            statisticMap.put("result_rows", ((List) baseResult.getData()).size());
        }

        if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.CSV.getValue())) {
            convertJsonToCSV();
        }
        baseResult.setFormatType(param.getFormat());

        log.debug("Execute build Time : {}",  watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0);
        return baseResult;
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







}

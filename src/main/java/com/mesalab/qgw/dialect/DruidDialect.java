package com.mesalab.qgw.dialect;

import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SQLHelper;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.model.api.DruidIoHttpSource;
import com.mesalab.qgw.model.api.QueryParam;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
@Slf4j
public class DruidDialect implements  Dialect {

    private DruidIoHttpSource druidIoHttpSource = (DruidIoHttpSource) SpringContextUtil.getBean("druidIoHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");

    private String dbType;

    private int pageOffset;

    public DruidDialect(String dbType) {
        this.dbType = dbType;
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

    @Override
    public String getLimitString(String sql, SQLQuerySource sqlQuerySource) {
        if (StringUtil.isBlank(sqlQuerySource.getLimit())) {
            return getLimitString(sql, 0, 200);
        } else {

            if (sqlQuerySource.getLimit().split(",").length >= 2 ) {
                String[] limits = sqlQuerySource.getLimit().trim().split(",");
                int offset = Integer.valueOf(limits[0].trim());
                int rowNum = Integer.valueOf(limits[1].trim());
                pageOffset = offset;
               return  SQLHelper.removeLimit(sql) + " limit " + (offset + rowNum);
            }
            return sql;
        }

    }

    @Override
    public BaseResult executeHttpQuery(QueryParam param) {
        BaseResult baseResult = null;

        StringBuilder urlBuilder = new StringBuilder("http://") .append(druidIoHttpSource.getUrl());
        DruidDialect.QueryObject queryObject = new DruidDialect.QueryObject();

        queryObject.setQuery(param.getQuery());
        queryObject.getContext().put("sqlTimeZone", druidIoHttpSource.getSqlTimeZone());
        queryObject.getContext().put("skipEmptyBuckets", druidIoHttpSource.getSkipEmptyBuckets());

        if (param.getFormat().equals(QueryFormatEnum.JSON.getValue())) {
            queryObject.setResultFormat("object");
        } else if (param.getFormat().equals(QueryFormatEnum.CSV.getValue())) {
            queryObject.setResultFormat("csv");
        }
        long beginTime = System.currentTimeMillis();
        Map<String, String> resultMap = httpClientService.httpPost(urlBuilder.toString(), JsonMapper.toJsonString(queryObject));
        double executeTime = (System.currentTimeMillis()-beginTime) / 1000.0;

        if (StringUtil.isEmpty(resultMap)) {
            baseResult =  BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "服务繁忙，请联系平台方！");
        } else {
            if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
                List<Object> allResults = (List<Object>) JsonMapper.fromJsonString(resultMap.get("result"), Object.class);
                if (pageOffset > 0 && StringUtil.isNotEmpty(allResults)) {
                    if (pageOffset >= allResults.size()) {
                        allResults = new ArrayList<>();
                    } else {
                        allResults = allResults.subList(pageOffset, allResults.size());
                    }
                }

                Map<String, Object> statistics = Maps.newHashMap();
                statistics.put("elapsed", executeTime);
                statistics.put("rows_read", allResults.size());
                int resultSize = StringUtil.isEmpty(allResults) ? 0 : JsonMapper.toJsonString(allResults).getBytes().length;
                statistics.put("result_size", resultSize);

                baseResult = BaseResultGenerator.success("ok", allResults , statistics);

            } else if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_NOT_FOUND))) {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND,  JsonMapper.fromJsonString(resultMap.get("result"), Object.class).toString());
            } else if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR))) {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, JsonMapper.fromJsonString(resultMap.get("result"), Object.class).toString());
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, JsonMapper.fromJsonString(resultMap.get("result"), Object.class).toString());
            }
        }

        return baseResult;
    }



    @Data
    class QueryObject {
        private String query;
        private Map<String, String> context = Maps.newHashMap();
        private String resultFormat;

    }

    @Override
    public String getDbType() {
        return dbType;
    }
}

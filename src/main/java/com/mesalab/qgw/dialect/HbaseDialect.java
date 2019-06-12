package com.mesalab.qgw.dialect;

import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.HBaseHttpSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpStatus;
import java.util.Map;
@Slf4j
public class HbaseDialect implements  Dialect{
    private HBaseHttpSource hBaseHttpSource = (HBaseHttpSource) SpringContextUtil.getBean("hBaseHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");
    private ApiParam param;

    public HbaseDialect(ApiParam param) {
        this.param = param;
    }

    @Override
    public boolean supportsLimit() {
        return false;
    }

    @Override
    public String getLimitString(String sql, int offset, int limit) {
        return sql;
    }


    public String getLimitString(String sql, SQLQuerySource sqlQuerySource) {

        return sql;
    }

    @Override
    public Dialect sqlDialectTransform() {

        return this;
    }

    @Override
    public BaseResult executeHttpQuery() {
        BaseResult baseResult = null;
        StringBuilder urlBuilder = new StringBuilder("http://")
                .append(hBaseHttpSource.getUrl()).append("/");
        StringBuilder queryparamBuilder = new StringBuilder(hBaseHttpSource.getDbName())
                .append(":").append(hBaseHttpSource.getTableName()).append("/")
                .append(getQueryId(param.getReportId(), param.getQuery())).append("/")
                .append(hBaseHttpSource.getColumnFamily()).append(":").append(hBaseHttpSource.getColumnName());

        log.info("报表结果检索语句：" + queryparamBuilder.toString());

        Map<String, String> resultMap = httpClientService.httpGet(urlBuilder.toString() + queryparamBuilder.toString() );

        if (StringUtil.isEmpty(resultMap)) {
            baseResult =  BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "服务繁忙，请联系平台方！");
        } else {
            if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
                Map<String, Object> maps =(Map<String, Object>) JsonMapper.fromJsonString(resultMap.get("result"),  Map.class);
                Map<String, Object> statisticMap = (Map<String, Object>)maps.get("statistics");
                Map<String, Object> statistics = Maps.newHashMap();
                statistics.put("elapsed", statisticMap.get("elapsed"));
                statistics.put("rows_read", statisticMap.get("rows_read"));
                int resultSize = StringUtil.isEmpty(maps.get("data")) ? 0 : JsonMapper.toJsonString(maps.get("data")).getBytes().length;
                statistics.put("result_size", resultSize);
                baseResult = BaseResultGenerator.success("ok", maps.get("data"), statistics);
            } else if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_NOT_FOUND))) {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND, resultMap.get("result").toString());
            } else if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR))) {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, JsonMapper.fromJsonString(resultMap.get("result"), Object.class).toString());
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, JsonMapper.fromJsonString(resultMap.get("result"), Object.class).toString());
            }
        }


        return  baseResult;
    }


    /**
     * 通过SQL生成查询唯一kEY
     * @param sql
     * @param  reportId
     * @return
     */
    private  static String getQueryId(String reportId, String sql) {
        return DigestUtils.md5Hex(reportId + sql.trim());
    }




}

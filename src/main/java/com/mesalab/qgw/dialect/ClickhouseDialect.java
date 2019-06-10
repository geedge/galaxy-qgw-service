package com.mesalab.qgw.dialect;


import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.Encodes;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ClickhouseDialect implements  Dialect {
    private ClickHouseHttpSource clickHouseHttpSource = (ClickHouseHttpSource) SpringContextUtil.getBean("clickHouseHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");
    private ApiParam param;


    public ClickhouseDialect(ApiParam param) {
        this.param = param;
    }

    @Override
    public boolean supportsLimit() {
        return true;
    }

    @Override
    public String getLimitString(String sql, int offset, int limit) {
        StringBuilder stringBuilder = new StringBuilder(sql);
        stringBuilder.append(" limit ");
        if (offset > 0) {
            stringBuilder.append(offset).append(",").append(limit);
        } else {
            stringBuilder.append(limit);
        }
        return stringBuilder.toString();
    }


    private String generatePageSql(String sql, SQLQuerySource sqlQuerySource) {
        if (StringUtil.isBlank(sqlQuerySource.getLimit())) {
            return getLimitString(sql, 0, 200);
        } else {
            return sql;
        }
    }

    private String generateSqlEncode(String sql) {

        return Encodes.urlEncode(sql);

    }

    private String generateGlobalIn(String sql) {
        Pattern p = Pattern.compile("in\\s*\\(", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, "global in (");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @Override
    public Dialect sqlDialectTransform() {
        param.setQuery(generatePageSql(param.getQuery(), param.getSqlQuerySource()));
        param.setQuery(generateGlobalIn(param.getQuery()));
        param.setQuery(generateSqlEncode(param.getQuery()));

        return this;
    }

    @Override
    public BaseResult executeHttpQuery() {
        BaseResult baseResult = null;
        StringBuilder urlBuilder = new StringBuilder("http://")
                .append(clickHouseHttpSource.getUrl()).append("/?");



        StringBuilder queryparamBuilder = new StringBuilder("user=")
                .append(clickHouseHttpSource.getUsername()).append("&")
                .append("password=").append(clickHouseHttpSource.getPassword()).append("&")
                .append("database=")
                .append(clickHouseHttpSource.getDbname()).append("&")
                .append("max_execution_time=").append(clickHouseHttpSource.getMaxExecutionTime()).append("&")
                .append("enable_http_compression=").append(clickHouseHttpSource.getEnableHttpCompression()).append("&")
               /* .append("query_id=").append(com.mesalab.common.utils.StringUtil.getQueryId(param.getQuery())).append("&")*/
                .append("query=").append(param.getQuery())
                .append(" FORMAT ").append(param.getFormat().toUpperCase()).append(" ;");
        log.info("final query sql: " +urlBuilder.toString() + queryparamBuilder.toString());

        List<NameValuePair> values = URLEncodedUtils.parse(queryparamBuilder.toString(), Charset.forName("UTF-8"));
        Map<String, String> resultMap = httpClientService.httpGet(urlBuilder.toString() +  URLEncodedUtils.format(values,"UTF-8"));

       if (StringUtil.isEmpty(resultMap)) {
           baseResult =  BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "服务繁忙，请联系平台方！");
       } else {
           if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
               Map<String, Object> maps =(Map<String, Object>)JsonMapper.fromJsonString(resultMap.get("result"),  Map.class);
               Map<String, Object> statisticMap = (Map<String, Object>)maps.get("statistics");
               Map<String, Object> statistics = Maps.newHashMap();
               statistics.put("elapsed", statisticMap.get("elapsed"));
               statistics.put("rows_read", statisticMap.get("rows_read"));
               int resultSize = StringUtil.isEmpty(maps.get("data")) ? 0 : JsonMapper.toJsonString(maps.get("data")).getBytes().length;
               statistics.put("result_size", resultSize);
               baseResult = BaseResultGenerator.success("ok", maps.get("data"), statistics);

           }else if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_NOT_FOUND))) {
               baseResult = BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND, resultMap.get("result"));
           } else if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR))) {
               baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, resultMap.get("result"));
           } else {
               baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, resultMap.get("result"));
           }
       }


       return  baseResult;

    }

}

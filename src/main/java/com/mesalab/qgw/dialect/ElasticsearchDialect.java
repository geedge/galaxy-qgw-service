package com.mesalab.qgw.dialect;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.sqlparser.SQLFunctionUtil;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.knowledge.common.utils.HttpConfig;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.ElasticsearchHttpSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import org.apache.http.HttpStatus;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * TODO
 *
 * @Classname ElasticsearchDialect
 * @Date 2021/6/17 11:40 下午
 * @Author wWei
 */
public class ElasticsearchDialect extends AbstractDataSourceDialect {
    private static final Log log = LogFactory.get();
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");
    private ElasticsearchHttpSource elasticsearchHttpSource = (ElasticsearchHttpSource) SpringContextUtil.getBean("elasticsearchHttpSource");
    private HttpConfig httpConfig = (HttpConfig) SpringContextUtil.getBean("httpConfig");
    private int pageOffset;
    public ApiParam param;
    public BaseResult baseResult;

    private Stopwatch watch = Stopwatch.createUnstarted();

    public ElasticsearchDialect(ApiParam param) {
        this.param = param;
    }

    @Override
    void init() {
        if (StringUtil.isNotEmpty(param.getDbQuerySource())) {
            param.setSql(param.getDbQuerySource().getSqlBody());
        } else {
            param.setSql(param.getQuery());
        }
        baseResult = BaseResultGenerator.success();
    }

    @Override
    String convertQuery(String sql) {
        return new Converter(sql).generateStandard().generateDateFunction().build();
    }

    @Override
    String getBestQuery() {
        return param.getSql();
    }

    @Override
    String getOriginQuery() {
        return param.getQuery();
    }

    @Override
    String getSyntaxCheckQuery() {
        return null;
    }

    @Override
    String simplyErrorMessage(Object dataObject) {
        String message = String.valueOf(dataObject);
        try {
            message = StringUtil.isNotEmpty(dataObject) ? JsonPath.read(dataObject.toString(), "$.error.reason")
                    : "System Runtime Exception, Please contact Galaxy Data Platform Project manager";
        } catch (JsonPathException e) {
            log.error("Build error druid-errorMessage data is: {}", dataObject);
        }
        return StrUtil.removeAllLineBreaks("SQL Execution Error: ".concat(message)).trim();
    }

    @Override
    BaseResult generateBaseResult(String sql, Optional<String> message) {

        Map<String, String> results = executeHttpGet(sql);
        if (Integer.parseInt(results.get("status")) == HttpStatus.SC_OK) {
            baseResult = BaseResultGenerator.generate(Integer.parseInt(results.get("status")), ResultCodeEnum.EXECUTE_SUCCESS.getCode(), message.get(),
                    (Map<String, Object>) JsonMapper.fromJsonString(results.get("result"), Object.class), null, null, QueryFormatEnum.JSON.getValue());
        } else {
            baseResult = BaseResultGenerator.generate(Integer.parseInt(results.get("status")), ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(), results.get("message"),
                    results.get("result"), null, null, param.getFormat());
        }
        return build();
    }

    private BaseResult build() {

        Object dataObject = baseResult.getData();

        if (baseResult.isSuccess()) {
            if (StringUtil.isBlank(baseResult.getMessage())) {
                baseResult.setMessage("ok");
            }
            Map<String, Object> allResults = (Map<String, Object>) dataObject;
            List<Map<String, Object>> data = getData(allResults);
            if (pageOffset > 0 && StringUtil.isNotEmpty(data)) {
                if (pageOffset >= data.size()) {
                    data = new ArrayList<>();
                } else {
                    data = data.subList(pageOffset, data.size());
                }
            }
            Map<String, Object> statistics = Maps.newLinkedHashMap();
            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            statistics.put("rows_read", allResults.size());
            baseResult.setData(data);
            baseResult.setMeta(allResults.get("columns"));
            baseResult.setStatistics(statistics);
        } else {
            baseResult = BaseResultGenerator.failure(baseResult.getStatus(), ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(),
                    simplyErrorMessage(dataObject));
        }
        return baseResult;
    }

    private List<Map<String, Object>> getData(Map<String, Object> allResults) {
        List<ArrayList> rowsList = (List<ArrayList>) allResults.get("rows");
        List<Map<String, Object>> list = new ArrayList<>();
        List<Map<String, String>> meta = (List<Map<String, String>>) allResults.get("columns");
        rowsList.forEach(o ->
        {
            HashMap<String, Object> map = new LinkedHashMap<>(o.size());
            for (int i = 0; i < o.size(); i++) {
                map.put(meta.get(i).get("name"), o.get(i));
            }
            list.add(map);
        });
        return list;
    }


    @Override
    public boolean supportsLimit() {
        return true;
    }

    @Override
    public BaseResult executeKillQuery() {
        return null;
    }

    @Override
    public BaseResult getProcesses() {
        return null;
    }

    private Map<String, String> executeHttpGet(String sql) {
        String queryURL = URLUtil.normalize(elasticsearchHttpSource.getUrl() + "?format=json");
        QueryParam queryParam = getQueryParam(sql);
        log.info("DB Engine is :{}, Execute Http Query is: {}", DBTypeEnum.ELASTICSEARCH.getValue(),
                JsonMapper.toJsonString(queryParam));
        return httpClientService.httpPost(queryURL, JsonMapper.toJsonString(queryParam), httpConfig.getElasticsearchSocketTimeOut());
    }

    private QueryParam getQueryParam(String sql) {
        QueryParam queryParam = new QueryParam();
        queryParam.setQuery(sql);
        return queryParam;
    }

    class Converter {
        String sql = null;

        public Converter(String sql) {
            this.sql = sql;
        }

        /**
         * SQL转换为ES SQL语法格式
         * 1. 不支持limit offset,rows 转为 limit rows
         *
         * @return
         */
        public ElasticsearchDialect.Converter generateStandard() {
            SQLQuerySource dbQuerySource = param.getDbQuerySource();
            if (StringUtil.isNotBlank(dbQuerySource.getLimit()) &&
                    dbQuerySource.getLimit().split(",").length >= 2) {

                String[] limits = dbQuerySource.getLimit().trim().split(",");
                int offset = Integer.valueOf(limits[0].trim());
                int rowNum = Integer.valueOf(limits[1].trim());
                pageOffset = offset;
                sql = SQLHelper.getLimitString(dbQuerySource.getSqlBody(), 0, (offset + rowNum));
            }
            return this;
        }


        public ElasticsearchDialect.Converter generateDateFunction() {
            sql = SQLFunctionUtil.generateDateFunction(sql, DBTypeEnum.ELASTICSEARCH.getValue());
            return this;
        }


        public String build() {
            return sql;
        }
    }

    @Data
    class QueryParam {
        private String query;
    }
}

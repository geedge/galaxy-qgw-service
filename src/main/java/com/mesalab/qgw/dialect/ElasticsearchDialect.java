package com.mesalab.qgw.dialect;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.utils.sqlparser.SQLFunctionUtil;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.qgw.model.api.*;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
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
    private static final Map<String, String> metaMap = Maps.newHashMap();
    private RestClient restClient = (RestClient) SpringContextUtil.getBean("restClient");
    private int pageOffset;
    public ApiParam param;
    public BaseResult baseResult;

    private Stopwatch watch = Stopwatch.createUnstarted();

    static {
        metaMap.put("long", DataTypeMapping.LONG);
        metaMap.put("boolean", DataTypeMapping.BOOLEAN);
        metaMap.put("float", DataTypeMapping.FLOAT);
        metaMap.put("double", DataTypeMapping.DOUBLE);
        metaMap.put("text", DataTypeMapping.STRING);
    }

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
    String getOriginalQuery() {
        return param.getQuery();
    }

    @Override
    String getSampleQuery() {
        return convertQuery(param.getQuery());
    }

    @Override
    String getExplainQuery() {
        throw new QGWBusinessException(ResultStatusEnum.FAIL.getCode(), QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                String.format(QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getMessage(),"ES dialect unsupported EXPLAIN Syntax."));
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
        return StrUtil.removeAllLineBreaks(message).trim();
    }

    @Override
    BaseResult generateBaseResult(String sql, Optional<String> message) {
        Request request = new Request("POST", "/_sql?format=json");
        request.setJsonEntity(JsonMapper.toJsonString(getQueryParam(sql)));
        Response response;
        try {
            response = restClient.performRequest(request);
            ResponseEntity<String> respEntity = new ResponseEntity<>(EntityUtils.toString(response.getEntity()), org.springframework.http.HttpStatus.OK);
            baseResult = BaseResultGenerator.generate(ResultStatusEnum.SUCCESS.getCode(), ResultCodeEnum.SUCCESS.getCode(), message.get(),
                    (Map<String, Object>) JsonMapper.fromJsonString(respEntity.getBody(), Object.class), null, null, QueryFormatEnum.JSON.getValue());

        } catch (IOException e) {
            log.error("{} engine error: {}", DBTypeEnum.ELASTICSEARCH.getValue(), e);
            throw new QGWBusinessException(ResultStatusEnum.FAIL.getCode(), QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getMessage(),simplyErrorMessage(e.getMessage())));
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
            baseResult.setMeta(unifyMeta(allResults.get("columns")));
            baseResult.setStatistics(statistics);
        } else {
            throw new QGWBusinessException(baseResult.getStatus(), QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_EXECUTION_SERVER_EXCEPTION.getMessage(),simplyErrorMessage(dataObject)));
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
    public boolean supportsLimit() {
        return true;
    }

    @Override
    public BaseResult executeKillQuery(String queryId) {
        return null;
    }

    @Override
    public BaseResult getProcesses(String queryId) {
        return null;
    }

    private HashMap<String, Object> getQueryParam(String sql) {
        HashMap<String, Object> queryParam = Maps.newHashMap();
        queryParam.put("query", sql);
        if (StringUtil.isNotBlank(param.getDbQuerySource().getLimit())) {
            if (param.getDbQuerySource().getLimit().split(",").length >= 2) {
                String[] limits = param.getDbQuerySource().getLimit().trim().split(",");
                int offset = Integer.valueOf(limits[0].trim());
                int rowNum = Integer.valueOf(limits[1].trim());
                queryParam.put("fetch_size", offset + rowNum);
            } else {
                queryParam.put("fetch_size", Math.max(1, Long.parseLong(param.getDbQuerySource().getLimit())));
            }
        }
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

}

package com.mesalab.qgw.dialect;

import cn.hutool.core.util.URLUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.mesalab.calcite.storage.DataTypeMapping;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.*;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.*;
import com.mesalab.knowledge.common.utils.HttpConfig;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.DruidIoHttpSource;
import com.mesalab.qgw.model.api.EngineConfigSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.service.SystemService;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import org.apache.http.HttpStatus;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class DruidDialect extends AbstractDataSourceDialect {
    private static final Log log = LogFactory.get();
    private DruidIoHttpSource druidIoHttpSource = (DruidIoHttpSource) SpringContextUtil.getBean("druidIoHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");
    private EngineConfigSource engineConfigSource = (EngineConfigSource) SpringContextUtil.getBean("engineConfigSource");
    private HttpConfig httpConfig = (HttpConfig) SpringContextUtil.getBean("httpConfig");
    private SystemService systemService = (SystemService) SpringContextUtil.getBean("systemService");
    private int pageOffset;
    private Stopwatch watch = Stopwatch.createUnstarted();
    public ApiParam param;
    public BaseResult baseResult;

    public DruidDialect(ApiParam param) {
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
    public boolean supportsLimit() {
        return true;
    }


    @Override
    public String convertQuery(String sql) {
        return new Converter(sql).generateStandard().generateDateFunction().build();

    }

    @Override
    public String getBestQuery() {

        return new Optimizer().generateQueryLimit().generateSQL().build();

    }

    @Override
    public String getOriginQuery() {
        return param.getQuery();
    }

    /**
     * 获取语法检测sql
     *
     * @return
     */
    @Override
    public String getSyntaxCheckQuery() {
        String sql = param.getDbQuerySource().getSqlBody();
        try {
            sql = String.valueOf(parserAndUpdateSql(sql));
        } catch (JSQLParserException e) {
            log.error("syntax-check sql error {}, execute original sql: {}, error is: {} ",
                    sql, sql = param.getDbQuerySource().getSqlBody(), e.getMessage() == null ? e.getCause() : e.getMessage());
        }
        return sql;
    }

    @Override
    BaseResult killQuery() {
        String url = druidIoHttpSource.getUrl();
        url = url.substring(0, url.indexOf("/"));
        String deleteUrl = URLUtil.normalize(url).concat("/druid/v2/").concat(param.getQueryId());
        Map<String, String> map = httpClientService.httpDelete(deleteUrl, 1000);
        if (map.get("status").equals(String.valueOf(HttpStatus.SC_OK))
                || map.get("status").equals(String.valueOf(HttpStatus.SC_ACCEPTED))) {
            return BaseResultGenerator.generate(HttpStatus.SC_ACCEPTED, null, "ok", null, null, null, null);
        } else {
            return BaseResultGenerator.failure(Integer.parseInt(map.get("status")), map.get("result"));
        }
    }

    @Override
    BaseResult getQueryProcesses() {
        return BaseResultGenerator.success4Message("ok");
    }

    /**
     * Druid Http Query Interface
     *
     * @param sql SQL语句
     * @return
     */
    private Map<String, String> executeHttpPost(String sql) {
        String queryURL = URLUtil.normalize(druidIoHttpSource.getUrl());
        DruidQueryParam druidQueryParam = getDruidQueryParam(sql);
        log.info("DB Engine is :{}, Execute Query is: {}", DBTypeEnum.DRUID.getValue(), JsonMapper.toJsonString(druidQueryParam));
        int socketTimeOut = httpConfig.getDruidSocketTimeOut();
        return httpClientService.httpPost(queryURL, JsonMapper.toJsonString(druidQueryParam), socketTimeOut);
    }

    private DruidQueryParam getDruidQueryParam(String sql) {
        DruidQueryParam druidQueryParam = new DruidQueryParam();
        druidQueryParam.setQuery(sql);
        druidQueryParam.getContext().put("skipEmptyBuckets", druidIoHttpSource.getSkipEmptyBuckets());
        if (QueryOptionEnum.LONG_TERM.getValue().equalsIgnoreCase(param.getOption())) {
            druidQueryParam.getContext().put("sqlQueryId", systemService.getQueryIdOfInternal(param.getResultId(), param.getQuery()));
        }
        druidQueryParam.setResultFormat("object");
        return druidQueryParam;
    }

    @Override
    public BaseResult generateBaseResult(String sql, Optional<String> message) {
        watch.start();

        Map<String, String> results = executeHttpPost(sql);

        if (results.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), ResultCodeEnum.EXECUTE_SUCCESS.getCode(), message.get(),
                    (List<Object>) JsonMapper.fromJsonString(results.get("result"), Object.class), null, null, QueryFormatEnum.JSON.getValue());
        } else {
            baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(), results.get("message"),
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
            List<Object> allResults = (List<Object>) dataObject;
            if (pageOffset > 0 && StringUtil.isNotEmpty(allResults)) {
                if (pageOffset >= allResults.size()) {
                    allResults = new ArrayList<>();
                } else {
                    allResults = allResults.subList(pageOffset, allResults.size());
                }
            }
            Map<String, Object> statistics = Maps.newLinkedHashMap();
            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            statistics.put("rows_read", allResults.size());
            baseResult.setData(allResults);
            baseResult.setMeta(getMeta(allResults));
            baseResult.setStatistics(statistics);
        } else if (baseResult.getStatus().equals(HttpStatus.SC_NOT_FOUND)) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND, ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(),
                    StringUtil.isEmpty(dataObject) ? "System Runtime Exception,Please contact Galaxy Data Platform Project manager. " : String.valueOf(dataObject));
        } else if (baseResult.getStatus().equals(HttpStatus.SC_INTERNAL_SERVER_ERROR)) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(),
                    StringUtil.isEmpty(dataObject) ? "System Runtime Exception,Please contact Galaxy Data Platform Project manager. " : String.valueOf(dataObject));
        } else {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(),
                    StringUtil.isEmpty(dataObject) ? "System Runtime Exception,Please contact Galaxy Data Platform Project manager. " : String.valueOf(dataObject));
        }
        return baseResult;
    }




    /**
     * 做时间范围条件过滤: 去除原有最内层条件, 重新添加时间范围
     *
     * @param sql
     * @return
     * @throws JSQLParserException
     */
    private SelectBody parserAndUpdateSql(String sql) throws JSQLParserException {
        Statement parse = CCJSqlParserUtil.parse(sql);
        if (parse instanceof Select) {
            Select select = (Select) parse;
            SelectBody selectBody = select.getSelectBody();
            if (selectBody instanceof PlainSelect) {
                PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
                FromItem fromItem = plainSelect.getFromItem();
                if (fromItem instanceof SubSelect) {
                    SubSelect subSelect = (SubSelect) plainSelect.getFromItem();
                    subSelect.setSelectBody(parserAndUpdateSql(subSelect.getSelectBody().toString()));
                } else if (fromItem instanceof Table) {
                    plainSelect.setWhere(generateDataSetSql());
                    return plainSelect;
                }
                return plainSelect;
            } else if (selectBody instanceof SetOperationList) {
                SetOperationList setOperationList = (SetOperationList) selectBody;
                List<SelectBody> selects = setOperationList.getSelects();
                for (int i = 0; i < selects.size(); i++) {
                    selects.set(i, parserAndUpdateSql(selects.get(i).toString()));
                }
                return setOperationList;
            } else {
                throw new BusinessException("Only support selectBody operation: PlainSelect or SetOperationList");
            }
        }
        throw new BusinessException("Only support statement operation as select");
    }


    /**
     * 生成where条件: 以Expression对象形式
     *
     * @return
     * @throws JSQLParserException
     */
    private Expression generateDataSetSql() throws JSQLParserException {
        String sql = String.format("SELECT * FROM tableName WHERE %s >= CURRENT_TIMESTAMP - INTERVAL '%s' HOUR ", param.getDbQuerySource().getPartitionKey(), 12);
        Statement parse = CCJSqlParserUtil.parse(sql);
        Select select = (Select) parse;
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        return plainSelect.getWhere();
    }

    private List getMeta(List<Object> results) {
        List meta = new ArrayList<Map<String, String>>();
        if (results.size() > 0) {
            Map<String, Object> map = (Map) results.get(0);
            for (String key : map.keySet()) {
                Object value = map.get(key);
                Map<String, String> temp = new HashMap<>();
                temp.put("name", key);
                if (value instanceof Integer) {
                    temp.put("type", DataTypeMapping.BIGINT);
                } else if (value instanceof Double) {
                    temp.put("type", DataTypeMapping.DOUBLE);
                } else {
                    temp.put("type", DataTypeMapping.VARCHAR);
                }
                meta.add(temp);
            }
        }
        return meta;
    }

    class Converter {
        String sql = null;

        public Converter(String sql) {
            this.sql = sql;
        }

        /**
         * SQL转换为DRUID语法格式
         * 1. 不支持limit offset,rows 转为 limit rows
         *
         * @return
         */
        public Converter generateStandard() {
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


        public Converter generateDateFunction() {

            try {
                sql = SQLFunctionUtil.generateDateFunction(sql, DBTypeEnum.DRUID.getValue());
            } catch (Exception ex) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "参数校验：SQL 日期函数解析错误", ex);
            }

            return this;
        }


        public String build() {
            return sql;
        }
    }


    class Optimizer {
        String sql = null;

        /**
         * 对SQL最内层的结果数据集进行限制
         * 参见查询网关系统参数：engine.maxCacheNum
         *
         * @return
         */
        public Optimizer generateQueryLimit() {

            setDefaultResultRows(StringUtil.isEmpty(param.getEngineQuerySource()) ? param.getDbQuerySource() : param.getEngineQuerySource(), engineConfigSource.getDefaultResultNum());

            setMaxCacheResultRows(param.getDbQuerySource(), engineConfigSource.getMaxCacheNum());


            return this;
        }


        private void setDefaultResultRows(SQLQuerySource sqlQuerySource, int defaultResultRows) {
            if (StringUtil.isBlank(sqlQuerySource.getLimit())) {
                sqlQuerySource.setSqlBody(sqlQuerySource.getSqlBody() + " limit " + defaultResultRows);
                sqlQuerySource.setLimit(String.valueOf(defaultResultRows));
            }

        }

        /**
         * 设置最大缓存数据集
         *
         * @param sqlQuerySource
         * @return
         */
        private void setMaxCacheResultRows(SQLQuerySource sqlQuerySource, int maxCacheResultRows) {
            SQLQuerySource innerSqlQuerySource = sqlQuerySource;
            while (StringUtil.isNotEmpty(innerSqlQuerySource.getSubSelect())) {
                innerSqlQuerySource = innerSqlQuerySource.getSubSqlQuerySources().get(0);
            }

            if (!innerSqlQuerySource.isEnableLimit()) {
                return;
            }

            String orginInnerSql = innerSqlQuerySource.getSqlBody();

            String limitInnerSql = innerSqlQuerySource.getSqlBody();
            if (StringUtil.isNotBlank(innerSqlQuerySource.getLimit())) {

                String[] limits = innerSqlQuerySource.getLimit().split(",");

                if (Integer.valueOf(limits[limits.length - 1]) > maxCacheResultRows) {
                    limits[limits.length - 1] = String.valueOf(maxCacheResultRows);

                    if (limits.length == 2) {
                        limitInnerSql = SQLHelper.getLimitString(limitInnerSql, Integer.valueOf(limits[0]), Integer.valueOf(limits[1]));
                    } else {
                        limitInnerSql = SQLHelper.getLimitString(limitInnerSql, 0, Integer.valueOf(limits[0]));
                    }

                    innerSqlQuerySource.setLimit(String.join(",", limits).toString());
                    innerSqlQuerySource.setSqlBody(limitInnerSql);

                }

            } else {
                limitInnerSql = innerSqlQuerySource.getSqlBody() + " limit " + maxCacheResultRows;
                innerSqlQuerySource.setLimit(String.valueOf(maxCacheResultRows));
                innerSqlQuerySource.setSqlBody(limitInnerSql);

            }

            if (StringUtil.isNotEmpty(sqlQuerySource.getSubSqlQuerySources())) {
                sqlQuerySource.setSqlBody(sqlQuerySource.getSqlBody().replace(orginInnerSql, limitInnerSql));
            }

        }

        public Optimizer generateSQL() {
            SQLQuerySource sqlQuerySource = param.getDbQuerySource();
            sql = sqlQuerySource.getSqlBody();
            return this;
        }

        public String build() {
            return sql;
        }


    }


    @Data
    class DruidQueryParam {
        private String query;
        private Map<String, String> context = Maps.newHashMap();
        private String resultFormat;

    }

}

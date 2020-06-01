package com.mesalab.qgw.dialect;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SQLFunctionUtil;
import com.mesalab.common.utils.SQLHelper;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.EngineConfigSource;
import com.mesalab.qgw.model.api.DruidIoHttpSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class DruidDialect extends  AbstractDataSourceDialect {

    private DruidIoHttpSource druidIoHttpSource = (DruidIoHttpSource) SpringContextUtil.getBean("druidIoHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");
    private EngineConfigSource engineConfigSource = (EngineConfigSource) SpringContextUtil.getBean("engineConfigSource");
    Pattern pLimit = Pattern.compile("limit\\s*(.*?)", Pattern.CASE_INSENSITIVE);
    private int pageOffset;
    private Stopwatch watch = Stopwatch.createUnstarted();

    public DruidDialect(ApiParam param) {
        initialize(param);
    }

    @Override
    public boolean supportsLimit() {
        return true;
    }

    @Override
    public String getLimitString(String sql, int offset, int rowCount) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            Select selectStatement = (Select) statement;
            PlainSelect select = (PlainSelect) selectStatement.getSelectBody();
            Limit limit = new Limit();
            if (offset > 0) {
                limit.setOffset(new LongValue(offset));
            }
            limit.setRowCount(new LongValue(rowCount));
            select.setLimit(limit);
            sql = String.valueOf(selectStatement.getSelectBody());
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Druid Parser getLimit error : ", e);
        }

        return sql;
    }



    @Override
    public String convertQuery(String sql) {
        return new Converter(sql).
                generateStandard().generateDateFunction().build();
    }

    @Override
    public String findBestQuery() {
        return new Optimizer().generateQueryLimit().
                generateSQL().build();
    }

    @Override
    public Dialect executeQuery() {

        String sql = convertQuery(findBestQuery());

        watch.start();
        StringBuilder urlBuilder = new StringBuilder("http://") .append(druidIoHttpSource.getUrl());
        DruidDialect.DruidQueryParam druidQueryParam = new DruidDialect.DruidQueryParam();
        druidQueryParam.setQuery(sql);
        druidQueryParam.getContext().put("sqlTimeZone", druidIoHttpSource.getSqlTimeZone());
        druidQueryParam.getContext().put("skipEmptyBuckets", druidIoHttpSource.getSkipEmptyBuckets());

        log.info("DB Engine is :{}, Execute Query is: {}", DBTypeEnum.DRUID.getValue(), JsonMapper.toJsonString(druidQueryParam));

        if (param.getFormat().equals(QueryFormatEnum.JSON.getValue())) {
            druidQueryParam.setResultFormat("object");
        } else if (param.getFormat().equals(QueryFormatEnum.CSV.getValue())) {
            druidQueryParam.setResultFormat("csv");
        }

        Map<String, String> results = httpClientService.httpPost(urlBuilder.toString(), JsonMapper.toJsonString(druidQueryParam));

        if (!results.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, results.get("result"));
        } else {
            if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.JSON.getValue())) {
                baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), null,
                        (List<Object>) JsonMapper.fromJsonString(results.get("result"), Object.class), null, null, param.getFormat());
            } else {
                baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), null, null,
                        results.get("result"), null, param.getFormat());
            }
        }
        return this;

    }



    @Override
    public Dialect executeSyntaxCheck() {
        SQLQuerySource sqlQuerySource = param.getDbQuerySource();
        String sql = convertQuery(sqlQuerySource.getSqlBody());
        param.getDbQuerySource().setSqlBody("select 1");
        executeQuery();
        baseResult.setMessage(sql);
        return this;
    }



    @Override
    public BaseResult build() {

        Object dataObject = baseResult.getData();

        if (baseResult.isSuccess()) {
            if (StringUtil.isBlank(baseResult.getMessage())) {
                baseResult.setMessage("ok");
            }
            if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.CSV.getValue())) {
                String result = String.valueOf(dataObject);

                String[] results = result.split("\n");

                if (pageOffset > 0 && StringUtil.isNotEmpty(results)) {
                    if (pageOffset >= results.length) {
                        result = "";
                    } else {
                        String[] subResult = ArrayUtils.subarray(results, pageOffset, results.length);
                        result = Joiner.on("\n").join(subResult).toString();
                    }
                }
                baseResult.setData(result);
            } else if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.JSON.getValue())) {
                List<Object> allResults = (List<Object>) dataObject;
                if (pageOffset > 0 && StringUtil.isNotEmpty(allResults)) {
                    if (pageOffset >= allResults.size()) {
                        allResults = new ArrayList<>();
                    } else {
                        allResults = allResults.subList(pageOffset, allResults.size());
                    }
                }
                Double executeTime = watch.elapsed(TimeUnit.MILLISECONDS) / 1000.0;
                Map<String, Object> statistics = Maps.newHashMap();
                statistics.put("elapsed", String.format("%.2f", executeTime));
                statistics.put("rows_read", allResults.size());

                int resultSize = StringUtil.isEmpty(allResults) ? 0 : JsonMapper.toJsonString(allResults).getBytes().length;
                statistics.put("result_size", resultSize);
                baseResult.setData(allResults);
                baseResult.setStatistics(statistics);
            }
        } else if (baseResult.getStatus().equals(HttpStatus.SC_NOT_FOUND)) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND, dataObject.toString());
        } else if (baseResult.getStatus().equals(HttpStatus.SC_INTERNAL_SERVER_ERROR)) {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, dataObject.toString());
        } else {
            baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, dataObject.toString());
        }
        return baseResult;
    }



    class Converter {
        String sql = null;

        public Converter(String sql) {
            this.sql = sql;
        }

        /**
         * SQL转换为DRUID语法格式
         * 1. 不支持limit offset,rows 转为 limit rows
         * @return
         */
        public Converter generateStandard(){
            SQLQuerySource dbQuerySource = param.getDbQuerySource();
            if (StringUtil.isNotBlank(dbQuerySource.getLimit()) &&
                    dbQuerySource.getLimit().split(",").length >= 2 ) {

                String[] limits = dbQuerySource.getLimit().trim().split(",");
                int offset = Integer.valueOf(limits[0].trim());
                int rowNum = Integer.valueOf(limits[1].trim());
                pageOffset = offset;
                sql = getLimitString(dbQuerySource.getSqlBody(),0, (offset + rowNum));
            }
            return this;
        }


        public Converter generateDateFunction() {
            sql = SQLFunctionUtil.generateDateFunction(sql, DBTypeEnum.DRUID.getValue());
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

            if(StringUtil.isEmpty(param.getEngineQuerySource())) {
                setDefaultResultRows(param.getDbQuerySource(), engineConfigSource.getDefaultResultNum());
            }

            setMaxCacheResultRows(param.getDbQuerySource(), engineConfigSource.getMaxCacheNum());


            return this;
        }


        private  void setDefaultResultRows(SQLQuerySource sqlQuerySource, int defaultResultRows) {
            if (StringUtil.isBlank(sqlQuerySource.getLimit())) {
                sqlQuerySource.setSqlBody(sqlQuerySource.getSqlBody() + " limit " + defaultResultRows);
                sqlQuerySource.setLimit(String.valueOf(defaultResultRows));
            }

        }

        /**
         * 设置最大缓存数据集
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

                if (Integer.valueOf(limits[limits.length-1]) > maxCacheResultRows) {
                    limits[limits.length-1] = String.valueOf(maxCacheResultRows);
                    Matcher m = pLimit.matcher(orginInnerSql);
                    StringBuffer sb = new StringBuffer();
                    while (m.find()) {
                        if (m.groupCount() == 1) {
                            m.appendReplacement(sb, "limit " + String.join(",", limits).toString());
                        }
                    }
                    limitInnerSql = sb.toString();
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

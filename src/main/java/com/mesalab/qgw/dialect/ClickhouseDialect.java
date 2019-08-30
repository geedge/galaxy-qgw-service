package com.mesalab.qgw.dialect;


import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SQLDateFunctionUtil;
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

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ClickhouseDialect implements Dialect {
    Pattern pGlobalIn = Pattern.compile("\\s+in\\s*\\(", Pattern.CASE_INSENSITIVE);
    Pattern pUniq = Pattern.compile("count\\s*\\(\\s*distinct\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE);
    Pattern pUniqCombined = Pattern.compile("count\\s*\\(\\s*distinct\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE);
    Pattern pWhere = Pattern.compile("where\\s*", Pattern.CASE_INSENSITIVE);
    Pattern pCount = Pattern.compile("select \\s*count\\(\\s*\\*\\s*\\)|\\s*count\\(\\s*\\)", Pattern.CASE_INSENSITIVE);
    /**
     * 子查询匹配的字段(别名)  例如:(recv_time|domain|client_ip)
     */
    Pattern pKeyPartition = Pattern.compile("(recv_time)\\s*(<|>|=)",Pattern.CASE_INSENSITIVE);
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


    /**
     * 默认分页处理
     * @param sql
     * @param sqlQuerySource
     * @return
     */
    private String generatePageSql(String sql, SQLQuerySource sqlQuerySource) {
        if (StringUtil.isBlank(sqlQuerySource.getLimit())) {
            return getLimitString(sql, 0, 200);
        } else {
            return sql;
        }
    }

    /**
     * http GET  sql 里特殊符号需要做encode编码
     * @param sql
     * @return
     */
    private String generateSqlEncode(String sql) {

        return Encodes.urlEncode(sql);

    }

    /**
     * IN 转换为 集群 Global IN 模式
     * @param sql
     * @return
     */
    private String generateGlobalIn(String sql) {
        Matcher m = pGlobalIn.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, " global in (");
        }

        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * count(distinct())转为uniqCombined函数
     * @param sql
     * @return
     */
    private String generateUniqCombined(String sql) {

        Matcher m = pUniqCombined.matcher(sql);

        StringBuffer sb = new StringBuffer();

        while (m.find()) {

            if (m.groupCount() == 1) {
                String replaceValue = m.group(1);
                m.appendReplacement(sb, "uniqCombined(" + replaceValue);

            }
        }

        m.appendTail(sb);

        return sb.toString();


    }

    /**
     * count(distinct())转为uniq函数
     * @param sql
     * @return
     */
    private String generateUniq(String sql) {

        Matcher m = pUniq.matcher(sql);

        StringBuffer sb = new StringBuffer();

        while (m.find()) {

            if (m.groupCount() == 1) {
                String replaceValue = m.group(1);
                m.appendReplacement(sb, "uniq(" + replaceValue);

            }
        }

        m.appendTail(sb);

        return sb.toString();


    }

    /**
     * 生成基于recv_time 事件索引子查询，目的提高日志查询检索性能
     * 限制：
     * 1. 仅适用一级普通查询，带where （无group by 统计）的查询
     * 2. 不对count 统计进行生成
     * @param sql
     * @return
     */
    private String generateSubQueryByPartition(String sql) {


        if (StringUtil.isBlank(param.getSqlQuerySource().getExpr())) {
            return sql;
        }

        if (StringUtil.isNotBlank(param.getSqlQuerySource().getGroupByClause())) {
            return sql;
        }
        Matcher mKey = pKeyPartition.matcher(param.getSqlQuerySource().getExpr());
        if (!mKey.find() || pCount.matcher(sql).find()) {
            return sql;
        }
        String mKeyStrTemp = null;
        while (mKey.find()) {
            String mKeyStr = mKey.group(1);
            if (mKeyStr.equals(mKeyStrTemp)) {
                continue;
            }
            mKeyStrTemp = mKeyStr;
            StringBuffer subQuery = new StringBuffer();
            Pattern pAsRecvTime = Pattern.compile("(.*,|^)(.*?\\s+(as|As|AS|aS)\\s+"+mKeyStr+")\\s*($|,)");
            Matcher mAsKey = pAsRecvTime.matcher(param.getSqlQuerySource().getSelectItems());
            if (mAsKey.find()) {
                subQuery.append("select " + mAsKey.group(2) + " from ").append(param.getSqlQuerySource().getTableName())
                        .append(" where ").append(param.getSqlQuerySource().getExpr());
            } else {
                subQuery.append("select " + mKeyStr + " from ").append(param.getSqlQuerySource().getTableName())
                        .append(" where ").append(param.getSqlQuerySource().getExpr());
            }

            if (StringUtil.isNotBlank(param.getSqlQuerySource().getOrderBy())) {
                subQuery.append(" order by ").append(param.getSqlQuerySource().getOrderBy());
            }

            if (StringUtil.isNotBlank(param.getSqlQuerySource().getLimit())) {
                String[] limits = param.getSqlQuerySource().getLimit().split(",");
                if (limits.length == 2) {
                    int rows = Integer.valueOf(limits[0].trim()) + Integer.valueOf(limits[1].trim());
                    subQuery.append(" limit ").append(rows);
                } else {
                    subQuery.append(" limit ").append(param.getSqlQuerySource().getLimit());
                }
            }

            Matcher m = pWhere.matcher(sql);
            StringBuffer sb = new StringBuffer();
            if (m.find()) {
                m.appendReplacement(sb, "where " + mKeyStr + " in ( " + subQuery + ") and  ");
            }
            m.appendTail(sb);
            sql = sb.toString();
        }
        return sql;
    }



    @Override
    public Dialect sqlDialectTransform() {
        param.setQuery(generatePageSql(param.getQuery(), param.getSqlQuerySource()));
        param.setQuery(generateUniq(param.getQuery()));
        param.setQuery(generateSubQueryByPartition(param.getQuery()));
        param.setQuery(SQLDateFunctionUtil.generateDateFunction(param.getQuery(),DBTypeEnum.CLICKHOUSE.getValue()));
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
                .append("query=").append(param.getQuery())
                .append(" FORMAT ").append(param.getFormat().toUpperCase()).append(" ;");

        log.info("Query Session and Events Query : {}" , urlBuilder.toString() + Encodes.urlDecode(queryparamBuilder.toString()));

        List<NameValuePair> values = URLEncodedUtils.parse(queryparamBuilder.toString(), Charset.forName("UTF-8"));

        Map<String, String> resultMap = httpClientService.httpGet(urlBuilder.toString() + URLEncodedUtils.format(values, "utf-8"));

       if (StringUtil.isEmpty(resultMap)) {
           baseResult =  BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "服务繁忙，请联系数据平台方！");
       } else {
           if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {

               if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.CSV.getValue())) {
                   baseResult = BaseResultGenerator.success("ok", resultMap.get("result"), null);
               } else if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.JSON.getValue())) {
                   Map<String, Object> maps =(Map<String, Object>)JsonMapper.fromJsonString(resultMap.get("result"),  Map.class);
                   Map<String, Object> statisticMap = (Map<String, Object>)maps.get("statistics");
                   Map<String, Object> statistics = Maps.newHashMap();
                   statistics.put("elapsed", statisticMap.get("elapsed"));
                   statistics.put("rows_read", statisticMap.get("rows_read"));
                   int resultSize = StringUtil.isEmpty(maps.get("data")) ? 0 : JsonMapper.toJsonString(maps.get("data")).getBytes().length;
                   statistics.put("result_size", resultSize);
                   baseResult = BaseResultGenerator.success("ok", maps.get("data"), statistics);
               }





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

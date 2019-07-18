package com.mesalab.qgw.dialect;


import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.Encodes;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ClickhouseDialect implements  Dialect {
    Pattern pDateFunStrToDate = Pattern.compile("STR_TO_DATE\\s*\\[(.*?)\\]|DATE_FORMAT\\s*\\[(.*?)\\]", Pattern.CASE_INSENSITIVE);
    Pattern pDateFunDateAdd = Pattern.compile("DATE_ADD\\[(.*?)\\s*,\\s*INTERVAL(.*?)\\]", Pattern.CASE_INSENSITIVE);
    Pattern pDateFunDateSub = Pattern.compile("DATE_SUB\\[(.*?)\\s*,\\s*INTERVAL(.*?)\\]", Pattern.CASE_INSENSITIVE);
    Pattern pDateFunDate = Pattern.compile("DATE\\s*\\[(.*?)\\]", Pattern.CASE_INSENSITIVE);
    Pattern pDateFunUnixTimeStamp = Pattern.compile("UNIX_TIMESTAMP|FROM_UNIXTIME", Pattern.CASE_INSENSITIVE);
    Pattern pDateFunFromUnixTime = Pattern.compile("FROM_UNIXTIME\\s*\\[(.*?\\].*?,.*?)\\]", Pattern.CASE_INSENSITIVE);
    Pattern pDateFunWeek = Pattern.compile("STR_TO_DATE\\s*\\(\\s*DATE_FORMAT\\s*\\((.*?),(.*?)\\)\\s*,(.*?)\\s*\\)", Pattern.CASE_INSENSITIVE);
    Pattern pDateAdd = Pattern.compile("(.*?)([A-Z]{3,7}$)", Pattern.CASE_INSENSITIVE);
    Pattern p_num = Pattern.compile("^-?\\s*\\d+$", Pattern.CASE_INSENSITIVE);
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
        Pattern p = Pattern.compile("in\\s*\\(", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, "global in (");
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

        Pattern p = Pattern.compile("count\\s*\\(\\s*distinct\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE);

        Matcher m = p.matcher(sql);

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

        Pattern p = Pattern.compile("count\\s*\\(\\s*distinct\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE);

        Matcher m = p.matcher(sql);

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
     * mysql时间范围函数转换
     * @param sql
     * @return
     */
    private String generateDateFun(String sql) {
        sql = dateFunWholeReplace(sql);
        StringBuffer sqlResult = new StringBuffer("(" + sql + ")");
        int start = -1;
        int end = -1;
        for (; ; ) {
            if (sqlResult.indexOf("(") == -1) {
                String result = sqlResult.toString();
                result = replaceOnly(result);
                return result;
            }

            //STR_TO_DATE、DATE_FORMAT替换
            start = sqlResult.lastIndexOf("(");
            end = sqlResult.indexOf(")", start);
            StringBuffer sb = new StringBuffer();
            StringBuffer sqlBuffer = new StringBuffer(sqlResult.substring(start+1,end));
            Matcher mStrToDate = pDateFunStrToDate.matcher(sqlBuffer);
            while (mStrToDate.find()) {
                if (mStrToDate.groupCount() == 2) {
                    String value =  mStrToDate.group(1)!=null?mStrToDate.group(1):mStrToDate.group(2);
                    String innerValue = convertValueStrToDate(value);
                    mStrToDate.appendReplacement(sb, innerValue);
                }
            }
            mStrToDate.appendTail(sb);

            //DATE_ADD替换
            sqlResult = sqlResult.replace(start+1,end,sb.toString());
            start = sqlResult.lastIndexOf("(");
            end = sqlResult.indexOf(")", start);
            sb = new StringBuffer();
            sqlBuffer = new StringBuffer(sqlResult.substring(start+1,end));
            Matcher mDateAdd = pDateFunDateAdd.matcher(sqlBuffer);
            while (mDateAdd.find()) {
                if (mDateAdd.groupCount() == 2) {
                    String innerValue = convertValueDateAdd(mDateAdd.group(1),mDateAdd.group(2),true);
                    mDateAdd.appendReplacement(sb, innerValue);
                }
            }
            mDateAdd.appendTail(sb);

            //DATE_SUB替换
            sqlResult = sqlResult.replace(start+1,end,sb.toString());
            start = sqlResult.lastIndexOf("(");
            end = sqlResult.indexOf(")", start);
            sb = new StringBuffer();
            sqlBuffer = new StringBuffer(sqlResult.substring(start+1,end));
            Matcher m_DATE_SUB = pDateFunDateSub.matcher(sqlBuffer);
            while (m_DATE_SUB.find()) {
                if (m_DATE_SUB.groupCount() == 2) {
                    String innerValue = convertValueDateAdd(m_DATE_SUB.group(1),m_DATE_SUB.group(2),false);
                    m_DATE_SUB.appendReplacement(sb, innerValue);
                }
            }
            m_DATE_SUB.appendTail(sb);

            //DATE替换
            sqlResult = sqlResult.replace(start+1,end,sb.toString());
            start = sqlResult.lastIndexOf("(");
            end = sqlResult.indexOf(")", start);
            sb = new StringBuffer();
            sqlBuffer = new StringBuffer(sqlResult.substring(start+1,end));
            Matcher m_DATE = pDateFunDate.matcher(sqlBuffer);
            while (m_DATE.find()) {
                if (m_DATE.groupCount() == 1) {
                    String innerValue = "toStartOfDay[toDateTime["+ m_DATE.group(1).trim()+"]]";
                    m_DATE.appendReplacement(sb, innerValue);
                }
            }
            m_DATE.appendTail(sb);

            // FROM_UNIXTIME(unix_timestamp,format)的替换
            sqlResult = sqlResult.replace(start+1,end,sb.toString());
            start = sqlResult.lastIndexOf("(");
            end = sqlResult.indexOf(")", start);
            sqlBuffer = new StringBuffer(sqlResult.substring(start+1,end));
            Matcher mFromUnixTime = pDateFunFromUnixTime.matcher(sqlBuffer);
            sb = new StringBuffer();
            while (mFromUnixTime.find()) {
                if(mFromUnixTime.groupCount()==1){
                    String innerValue = convertValueStrToDate(mFromUnixTime.group(1));
                    mFromUnixTime.appendReplacement(sb, innerValue);
                }
            }
            mFromUnixTime.appendTail(sb);

            sqlResult = sqlResult.replace(start,end+1,"["+sb+"]");
        }
    }


    @Override
    public Dialect sqlDialectTransform() {
        param.setQuery(generatePageSql(param.getQuery(), param.getSqlQuerySource()));
        param.setQuery(generateGlobalIn(param.getQuery()));
        param.setQuery(generateUniq(param.getQuery()));
        param.setQuery(generateDateFun(param.getQuery()));
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
                /*.append("max_execution_time=").append(600).append("&")*/
                /*.append("enable_http_compression=").append(clickHouseHttpSource.getEnableHttpCompression()).append("&")*/
                /*.append("query_id=").append(DateUtils.getCurrentDate("yyyymmddhhmmss")).append("&")*/
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

    /**
     * 单纯替换字符
     * @param sql
     * @return
     */
    public String replaceOnly(String sql){
        sql = sql.replace("[","(");
        sql = sql.replace("]",")");
        sql = sql.substring(1,sql.length()-1);

        StringBuffer sb = new StringBuffer();
        //UNIX_TIMESTAMP、FROM_UNIOXTIME(date)替换
        Matcher mUnixTimeStamp = pDateFunUnixTimeStamp.matcher(sql);
        while (mUnixTimeStamp.find()) {
            mUnixTimeStamp.appendReplacement(sb, "toDateTime");
        }
        mUnixTimeStamp.appendTail(sb);
        return sb.toString();
    }

    /**
     * FORMAT
     * @param innerValue
     * @return
     */
    private String convertValueStrToDate(String innerValue) {
        String[] split = StringUtil.split(innerValue, ",");
        if (split.length == 2) {
            String format = split[1].trim().replaceAll("\\'|\\\"","");
            switch (format) {
                case "%Y-%m-%d %H:%i:%s": innerValue = "toDateTime[" + split[0] + "]"; break;
                case "%Y-%m-%d 00:00:00": innerValue = "toStartOfDay[toDateTime[" + split[0] + "]]"; break;
                case "%Y-%m-01 00:00:00": innerValue = "toDateTime[toStartOfMonth[toDateTime[" + split[0] + "]]]"; break;
                case "%Y-01-01 00:00:00": innerValue = "toDateTime[toStartOfYear[toDateTime[" + split[0] + "]]]"; break;
                case "%Y-%m-%d": innerValue = "toDateTime(" + split[0] + ")"; break;
                default:innerValue = innerValue;
            }
        }
        return innerValue;
    }

    /**
     *  INVERTAL
     * @param date
     * @param expr
     * @param shift 判断 DATE_ADD:true    DATE_SUB:false
     * @return
     */
    private String convertValueDateAdd(String date,String expr,boolean shift) {
        expr = expr.trim().toUpperCase();
        Matcher matcher = pDateAdd.matcher(expr);
        if(matcher.find()){
            if(matcher.groupCount()==2){
                expr = matcher.group(1).trim();
                Matcher m_num = p_num.matcher(expr);
                if(m_num.matches()){
                    expr = expr.replaceAll("\\s*","");
                    int exprNum = Integer.parseInt(expr);
                    if (exprNum > 0) {
                        expr = "+" + expr;
                    }
                    if (!shift) {
                        expr = String.valueOf(-exprNum);
                    }
                }
                switch (matcher.group(2)){
                    case "SECOND": date = "toDateTime[" + date + "]" + expr; break;
                    case "MINUTE": date = "toDateTime[" + date + "]" + expr + "*60"; break;
                    case "HOUR": date = "toDateTime[" + date + "]" + expr + "*3600"; break;
                    case "DAY": date = "toDateTime[" + date + "]" + expr+ "*86400"; break;//24*3600
                    case "WEEK": date = "toDateTime[" + date + "]" + expr + "*604800"; break;//7*24*3600
                    case "MONTH": date = "toDateTime[" + date + "]" + expr + "*2678400"; break;//31*24*3600
                    case "QUARTER": date = "toDateTime[" + date + "]" + expr + "*8035200"; break;//3*31*24*3600
                    case "YEAR": date = "toDateTime[" + date + "]" + expr + "*31536000"; break;//365*24*3600
                    default:date = date + expr; break;
                }
            }
        }
        return date;
    }

    /**
     * 整体替换：不拆分SQL条件下替换相匹配函数
     * @param sql
     * @return
     */
    private String dateFunWholeReplace(String sql) {
        String regexp = "\\'|\\\"|\\s*";

        //This Week
        Matcher matcher = pDateFunWeek.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()){
            String innerValue = "";
            if(matcher.groupCount()==3){
                switch (matcher.group(2).replaceAll(regexp,"")){
                    case "%X%VSunday": innerValue = "toDateTime[toMonday["+matcher.group(1)+"]]-86400";break;
                    default : innerValue=sql; break;
                }
            }
            matcher.appendReplacement(sb,innerValue );
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

}

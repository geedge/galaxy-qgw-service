package com.mesalab.qgw.dialect;


import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.enums.QueryOptionEnum;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SQLHelper;
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
public class ClickhouseDialect implements  Dialect {
    Pattern pGlobalIn = Pattern.compile("in\\s*\\(", Pattern.CASE_INSENSITIVE);
    Pattern pUniq = Pattern.compile("count\\s*\\(\\s*distinct\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE);
    Pattern pUniqCombined = Pattern.compile("count\\s*\\(\\s*distinct\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE);
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
     * mysql时间范围函数转换
     * @param sql
     * @return
     */
    private String generateDateFunction(String sql) {
        sql = dateFunWholeReplace(sql);
        StringBuffer sqlResult = new StringBuffer("(" + sql + ")");
        while (true) {
            if (sqlResult.indexOf("(") == -1) {
                return replaceOnly(sqlResult.toString());
            }
            StringBuffer sqlBuffer = getNeedMatchStr(sqlResult);
            if (!SQLHelper.pKeyWord.matcher(sqlBuffer).find()) {
                sqlResult = getResultReplaceMark(sqlResult);
                continue;
            }
            StringBuffer sb = new StringBuffer();

            sb = replaceExprFormat(sqlBuffer);
            sqlResult = getResultAfterReplace(sqlResult, sb.toString());

            sqlBuffer = getNeedMatchStr(sqlResult);
            sb = replaceAboutAddInterval(sqlBuffer);
            sqlResult = getResultAfterReplace(sqlResult, sb.toString());

            sqlBuffer = getNeedMatchStr(sqlResult);
            sb = replaceAboutSubInterval(sqlBuffer);
            sqlResult = getResultAfterReplace(sqlResult, sb.toString());

            sqlBuffer = getNeedMatchStr(sqlResult);
            sb = replaceAboutExprDays(sqlBuffer);
            sqlResult = getResultAfterReplace(sqlResult, sb.toString());

            sqlBuffer = getNeedMatchStr(sqlResult);
            sb = replaceAboutGetOnlyNum(sqlBuffer);
            sqlResult = sqlResult.replace(sqlResult.lastIndexOf("("), sqlResult.indexOf(")", sqlResult.lastIndexOf("(")) + 1, "[" + sb + "]");
        }
    }

    @Override
    public Dialect sqlDialectTransform() {
        param.setQuery(generatePageSql(param.getQuery(), param.getSqlQuerySource()));
        param.setQuery(generateGlobalIn(param.getQuery()));
        param.setQuery(generateUniq(param.getQuery()));
        param.setQuery(generateDateFunction(param.getQuery()));
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
    public String replaceOnly(String sql) {
        sql = sql.replace("[", "(");
        sql = sql.replace("]", ")");
        sql = sql.replace("#", "");
        sql = sql.substring(1, sql.length() - 1);

        StringBuffer sb = new StringBuffer();
        //UNIX_TIMESTAMP(date)、FROM_UNIOXTIME(date)替换
        Matcher mUnixTimeStamp = SQLHelper.pUnixTime.matcher(sql);
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
    private String convertValueExprFormat(String innerValue) {
        String[] split = StringUtil.split(innerValue, ",\\s*'");
        if (split.length == 2) {
            String format = split[1].replaceAll("\\'|\\\"", "").trim();
            switch (format) {
                case "%Y-%m-%d %H:%i:%s": innerValue = "toDateTime#[" + split[0] + "]"; break;
                case "%Y-%m-%d %H:%i:00": innerValue = "toStartOfMinute#[toDateTime#[" + split[0] + "]]"; break;
                case "%Y-%m-%d %H:00:00": innerValue = "toStartOfHour#[toDateTime#[" + split[0] + "]]"; break;
                case "%Y-%m-%d 00:00:00": innerValue = "toStartOfDay#[toDateTime#[" + split[0] + "]]"; break;
                case "%Y-%m-01 00:00:00": innerValue = "toDateTime#[toStartOfMonth#[toDateTime#[" + split[0] + "]]]"; break;
                case "%Y-01-01 00:00:00": innerValue = "toDateTime#[toStartOfYear#[toDateTime#[" + split[0] + "]]]"; break;
                case "%Y-%m-%d": innerValue = "toStartOfMonth#[toDateTime#[" + split[0] + "]]"; break;
                case "%Y-%m-01": innerValue = "toStartOfMonth#[toDateTime#[" + split[0] + "]]"; break;
                case "%Y-01-01": innerValue = "toStartOfYear#[toDateTime#[" + split[0] + "]]"; break;
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
    private String convertValueDateAdd(String date, String expr, boolean shift) {
        expr = expr.trim().toUpperCase();
        Matcher matcher = SQLHelper.pDateUnit.matcher(expr);
        if (matcher.find()) {
            if (matcher.groupCount() == 2) {
                expr = matcher.group(1).trim();
                Matcher mNum = SQLHelper.pNum.matcher(expr);
                if (mNum.matches()) {
                    expr = expr.replaceAll("\\s*", "");
                    int exprNum = Integer.parseInt(expr);
                    if (exprNum > 0) {
                        expr = "+" + expr;
                    }
                    if (!shift) {
                        expr = String.valueOf(-exprNum);
                    }
                }
                switch (matcher.group(2)){
                    case "SECOND": date = "toDateTime#[" + date + "]" + expr; break;
                    case "MINUTE": date = "toDateTime#[" + date + "]" + expr + "*60"; break;
                    case "HOUR": date = "toDateTime#[" + date + "]" + expr + "*3600"; break;
                    case "DAY": date = "toDateTime#[" + date + "]" + expr+ "*86400"; break;//24*3600
                    case "WEEK": date = "toDateTime#[" + date + "]" + expr + "*604800"; break;//7*24*3600
                    case "MONTH": date = "toDateTime#[" + date + "]" + expr + "*2678400"; break;//31*24*3600
                    case "QUARTER": date = "toDateTime#[" + date + "]" + expr + "*8035200"; break;//3*31*24*3600
                    case "YEAR": date = "toDateTime#[" + date + "]" + expr + "*31536000"; break;//365*24*3600
                    default:date = date + expr; break;
                }
            }
        }
        return date;
    }

    /**
     * ADDDATE(expr,days)、SUBDATE(expr,days)替换
     * @param date
     * @param expr
     * @param shift 判断 ADDDATE:true    SUBDATE:false
     * @return
     */
    private String convertValueAddDate(String expr, String date, boolean shift) {
        date = date.replaceAll("\\s*", "");
        int num = Integer.parseInt(date);
        if (shift) {
            if (num < 0) {
                expr = "toDateTime#[" + expr + "]" + num * 86400;
            } else {
                expr = "toDateTime#[" + expr + "]+" + num * 86400;
            }
        } else {
            if (num < 0) {
                expr = "toDateTime#[" + expr + "]+" + -num * 86400;
            } else {
                expr = "toDateTime#[" + expr + "]" + -num * 86400;
            }
        }
        return expr;
    }

    /**
     * 整体替换：不拆分SQL条件下替换相匹配函数
     * @param sql
     * @return
     */
    private String dateFunWholeReplace(String sql) {
        StringBuffer sb = new StringBuffer(sql);
        sb = replaceGranularityWeek(sb);
        sb = replaceGranularityQuarter(sb);
        sb = replaceGranularityTime(sb);
        return sb.toString();
    }

    /**
     * 时间粒度替换(30s、1min、5min)
     * @param sb
     * @return
     */
    private StringBuffer replaceGranularityTime(StringBuffer sb) {
        Matcher mGranularity = SQLHelper.pDateGranularity.matcher(sb.toString());
        sb = new StringBuffer();
        while (mGranularity.find()) {
            String replaceValue = "";
            if (mGranularity.groupCount() == 4) {
                String expr = mGranularity.group(1) != null ? mGranularity.group(1).toUpperCase() : mGranularity.group(3).toUpperCase();
                String param = mGranularity.group(2) != null ? mGranularity.group(2) : mGranularity.group(4);
                if (expr.contains("SECOND")) {
                    replaceValue = "toDateTime#[intDiv#[toUInt32#[toDateTime#[toDateTime#[" + param + "]]], 30]*30]";
                } else if (expr.contains("FLOOR")) {
                    replaceValue = "toDateTime#[intDiv#[toUInt32#[toDateTime#[toDateTime#[" + param + "]]], 300]*300]";
                } else if (expr.contains("MINUTE")) {
                    replaceValue = "toStartOfMinute#[toDateTime#[" + param + "]]";
                } else if (expr.contains("HOUR")) {
                    replaceValue = "toStartOfHour#[toDateTime#[toDateTime#[" + param + "]]]";
                } else {
                    replaceValue = mGranularity.group(0);
                }
            }
            mGranularity.appendReplacement(sb, replaceValue);
        }
        mGranularity.appendTail(sb);
        return sb;
    }

    /**
     * 替换WEEK粒度函数
     * @param sb
     * @return
     */
    private StringBuffer replaceGranularityWeek(StringBuffer sb) {
        String regexp = "\\'|\\\"|\\s*";
        Matcher mThisWeek = SQLHelper.pWeek.matcher(sb);
        sb = new StringBuffer();
        while (mThisWeek.find()) {
            String replaceValue = "";
            if (mThisWeek.groupCount() == 3) {
                switch (mThisWeek.group(2).replaceAll(regexp, "")) {
                    case "%X%VSunday": replaceValue = "toDateTime#[toMonday#[toDateTime#["+mThisWeek.group(1)+"]]]-86400";break;
                    default : replaceValue=mThisWeek.group(0); break;
                }
            }
            mThisWeek.appendReplacement(sb, replaceValue);
        }
        mThisWeek.appendTail(sb);
        return sb;
    }

    /**
     * 替换季度粒度函数
     * @param sb
     * @return
     */
    private StringBuffer replaceGranularityQuarter(StringBuffer sb) {
        Matcher mQuarter = SQLHelper.pConcatQuarter.matcher(sb.toString());
        sb = new StringBuffer();
        while (mQuarter.find()) {
            String replaceValue = "";
            if (mQuarter.groupCount() == 1) {
                replaceValue = "toStartOfQuarter#[toDateTime#[toDateTime#[" + mQuarter.group(1) + "]]]";
            }
            mQuarter.appendReplacement(sb, replaceValue);
        }
        mQuarter.appendTail(sb);
        return sb;
    }

    /**
     * 取出字符串中最后一个括号中的值
     * @param str
     * @return
     */
    public StringBuffer getNeedMatchStr(StringBuffer str) {
        int start = str.lastIndexOf("(");
        int end = str.indexOf(")", start);
        return new StringBuffer(str.substring(start + 1, end));
    }

    /**
     * 获取替换后的串
     * @param resultStr 原始值
     * @param str 需替换值
     * @return
     */
    public StringBuffer getResultAfterReplace(StringBuffer resultStr, String str) {
        int start = resultStr.lastIndexOf("(");
        int end = resultStr.indexOf(")", start);
        return resultStr.replace(start + 1, end, str);
    }

    /**
     * 获取更新sql标记[]后的串
     * @param sqlResult
     * @return
     */
    public StringBuffer getResultReplaceMark(StringBuffer sqlResult) {
        int start = sqlResult.lastIndexOf("(");
        int end = sqlResult.indexOf(")", start);
        sqlResult = sqlResult.replace(start, start + 1, "[");
        sqlResult = sqlResult.replace(end, end + 1, "]");
        return sqlResult;
    }

    /**
     * DATE(expr)、YEAR(date)、MONTH(date)替换
     * @param sqlBuffer
     * @return
     */
    private StringBuffer replaceAboutGetOnlyNum(StringBuffer sqlBuffer) {
        StringBuffer sb = new StringBuffer();
        String replaceValue = "";
        Matcher mYear = SQLHelper.pYear.matcher(sqlBuffer);
        while (mYear.find()) {
            if (mYear.groupCount() == 6) {
                if (mYear.group(1) != null) {
                    replaceValue = "toYear#[toDateTime#[toDateTime#[" + mYear.group(1).trim() + "]]]";
                } else if (mYear.group(2) != null) {
                    replaceValue = "toQuarter#[toDateTime#[toDateTime#[" + mYear.group(2).trim() + "]]]";
                } else if (mYear.group(3) != null) {
                    replaceValue = "toMonth#[toDateTime#[toDateTime#[" + mYear.group(3).trim() + "]]]";
                } else if (mYear.group(4) != null) {
                    replaceValue = "toDateTime#[toMonday#[toDateTime#[" + mYear.group(4).trim() + "]]]-86400";
                } else if (mYear.group(5) != null) {
                    replaceValue = "toDayOfMonth#[toDateTime#[" + mYear.group(5).trim() + "]]";
                } else if (mYear.group(6) != null) {
                    replaceValue = "toHour#[toDateTime#[toDateTime#[" + mYear.group(6).trim() + "]]]";
                }
                mYear.appendReplacement(sb, replaceValue);
            }
        }
        mYear.appendTail(sb);
        return sb;
    }

    /**
     * ADDDATE(expr,days)、SUBDATE(expr,days)替换
     * @param sqlBuffer
     * @return
     */
    private StringBuffer replaceAboutExprDays(StringBuffer sqlBuffer) {
        StringBuffer sb = new StringBuffer();
        String replaceValue = "";
        Matcher mAddDate = SQLHelper.pAddDate.matcher(sqlBuffer);
        while (mAddDate.find()) {
            if (mAddDate.groupCount() == 4) {
                String expr = mAddDate.group(1) != null ? mAddDate.group(1) : mAddDate.group(3);
                String days = mAddDate.group(2) != null ? mAddDate.group(2) : mAddDate.group(4);
                replaceValue = convertValueAddDate(expr, days, mAddDate.group(1) != null);
                mAddDate.appendReplacement(sb, replaceValue);
            }
        }
        mAddDate.appendTail(sb);
        return sb;
    }

    /**
     * DATE_SUB(date,INTERVAL expr type)、SUBDATE(date,INTERVAL expr type)替换
     * @param sqlBuffer
     * @return
     */
    private StringBuffer replaceAboutSubInterval(StringBuffer sqlBuffer) {
        StringBuffer sb = new StringBuffer();
        String replaceValue = "";
        Matcher mSubInterval = SQLHelper.pSubInterval.matcher(sqlBuffer);
        while (mSubInterval.find()) {
            if (mSubInterval.groupCount() == 4) {
                String date = mSubInterval.group(1) != null ? mSubInterval.group(1) : mSubInterval.group(3);
                String expr = mSubInterval.group(2) != null ? mSubInterval.group(2) : mSubInterval.group(4);
                replaceValue = convertValueDateAdd(date, expr, false);
                mSubInterval.appendReplacement(sb, replaceValue);
            }
        }
        mSubInterval.appendTail(sb);
        return sb;
    }

    /**
     * DATE_ADD(date,INTERVAL expr type)、ADDDATE(date,INTERVAL expr type)替换
     * @param sqlBuffer
     * @return
     */
    private StringBuffer replaceAboutAddInterval(StringBuffer sqlBuffer) {
        StringBuffer sb = new StringBuffer();
        String replaceValue = "";
        Matcher mAddInterval = SQLHelper.pAddInterval.matcher(sqlBuffer);
        while (mAddInterval.find()) {
            if (mAddInterval.groupCount() == 4) {
                String date = mAddInterval.group(1) != null ? mAddInterval.group(1) : mAddInterval.group(3);
                String expr = mAddInterval.group(2) != null ? mAddInterval.group(2) : mAddInterval.group(4);
                replaceValue = convertValueDateAdd(date, expr, true);
                mAddInterval.appendReplacement(sb, replaceValue);
            }
        }
        mAddInterval.appendTail(sb);
        return sb;
    }

    /**
     * STR_TO_DATE(str,format)、DATE_FORMAT(str,format)、FROM_UNIXTIME(unix_timestamp,format)替换
     * @param sqlBuffer
     * @return
     */
    private StringBuffer replaceExprFormat(StringBuffer sqlBuffer) {
        String previousValue = "";
        StringBuffer sb = new StringBuffer();
        Matcher mExprFormat = SQLHelper.pExprFormat.matcher(sqlBuffer);
        while (mExprFormat.find()) {
            if (mExprFormat.groupCount() == 3) {
                if (mExprFormat.group(1) != null) {
                    previousValue = mExprFormat.group(1);
                } else if (mExprFormat.group(2) != null) {
                    previousValue = mExprFormat.group(2);
                } else if (mExprFormat.group(3) != null) {
                    previousValue = mExprFormat.group(3);
                } else {
                    previousValue = mExprFormat.group(0);
                }
                mExprFormat.appendReplacement(sb, convertValueExprFormat(previousValue));
            }
        }
        mExprFormat.appendTail(sb);
        return sb;
    }
}

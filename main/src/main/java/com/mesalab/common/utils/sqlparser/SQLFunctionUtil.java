package com.mesalab.common.utils.sqlparser;

import cn.hutool.core.util.URLUtil;
import com.alibaba.fastjson2.JSON;
import com.geedgenetworks.utils.Encodes;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.common.utils.StringUtil;
import com.mesalab.common.utils.TimeZoneUtil;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.common.configuration.HttpConfig;
import com.mesalab.qgw.model.basic.ClickHouseHttpSource;
import com.mesalab.qgw.model.basic.DruidIoHttpSource;
import com.mesalab.common.httpClient.HttpClientService;
import lombok.Data;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author wangwei
 * @data 2019/8/1 10:13
 */
public class SQLFunctionUtil {

    public static final String TIME_FLOOR_WITH_FILL = "TIME_FLOOR_WITH_FILL";
    public static final String IP_TO_GEO = "IP_TO_GEO";
    public static final String IP_TO_CITY = "IP_TO_CITY";
    public static final String IP_TO_COUNTRY = "IP_TO_COUNTRY";
    public static final String IP_TO_ISP = "IP_TO_ISP";
    public static final String IP_TO_ASN = "IP_TO_ASN";
    public static final String IP_TO_ASN_DETAIL = "IP_TO_ASN_DETAIL";
    public static final String IP_TO_ASN_ORG = "IP_TO_ASN_ORG";
    public static final String MAX_DURATION = "MAX_DURATION";
    public static final String MEDIAN_HDR = "MEDIAN_HDR";
    public static final String QUANTILE_HDR = "QUANTILE_HDR";
    public static final String PERCENTILES_HDR = "PERCENTILES_HDR";
    public static final String COUNT_DISTINCT = "COUNT_DISTINCT";
    public static final String APPROX_COUNT_DISTINCT_HLLD = "APPROX_COUNT_DISTINCT_HLLD";

    public static final String CONCAT = "concat";
    public static final String NOT_EMPTY = "notEmpty";
    public static final String EMPTY = "empty";

    private static DruidIoHttpSource druidIoHttpSource = (DruidIoHttpSource) SpringContextUtil.getBean("druidIoHttpSource");
    private static ClickHouseHttpSource clickHouseHttpSource = (ClickHouseHttpSource) SpringContextUtil.getBean("clickHouseHttpSource");
    private static HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");
    private static HttpConfig httpConfig = (HttpConfig) SpringContextUtil.getBean("httpConfig");

    /**
     * 自定义函数
     */
    public static final Pattern pTimeFloorWithFill = Pattern.compile("\\b" + TIME_FLOOR_WITH_FILL + "\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pMaxDuration = Pattern.compile("\\b" + MAX_DURATION + "\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pIpToGeo = Pattern.compile("\\b" + IP_TO_GEO + "\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pIpToCity = Pattern.compile("\\b" + IP_TO_CITY + "\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pIpToCountry = Pattern.compile("\\b" + IP_TO_COUNTRY + "\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pIpToIsp = Pattern.compile("\\b" + IP_TO_ISP + "\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pIpToAsn = Pattern.compile("\\b" + IP_TO_ASN + "\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pIpToAsnDetail = Pattern.compile("\\b" + IP_TO_ASN_DETAIL + "\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pIpToAsnOrg = Pattern.compile("\\b" + IP_TO_ASN_ORG + "\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern periodOfPT = Pattern.compile("PT(\\d+)(\\w+)", Pattern.CASE_INSENSITIVE);
    public static final Pattern periodOfP = Pattern.compile("P(\\d+)(\\w+)", Pattern.CASE_INSENSITIVE);

    public static final Pattern pMedianHdr = Pattern.compile("\\b" + MEDIAN_HDR + "\\(", Pattern.CASE_INSENSITIVE);

    public static final Pattern pQuantileHdr = Pattern.compile("\\b" + QUANTILE_HDR + "\\(", Pattern.CASE_INSENSITIVE);

    public static final Pattern pPercentilesHdr = Pattern.compile("\\b" + PERCENTILES_HDR + "\\(", Pattern.CASE_INSENSITIVE);

    public static final Pattern pCountDistinct = Pattern.compile("\\b" + COUNT_DISTINCT + "\\(", Pattern.CASE_INSENSITIVE);

    public static final Pattern pApproxCountDistinctHlld = Pattern.compile("\\b" + APPROX_COUNT_DISTINCT_HLLD + "\\(", Pattern.CASE_INSENSITIVE);

    public static final HashMap<String, String> functions = Maps.newHashMap();
    private static final Map<String, String> distributedGroupByFunction = Maps.newHashMap();

    public static final Map<String, String> timeSlicingFunction = Maps.newHashMap();

    static {
        functions.put(TIME_FLOOR_WITH_FILL, null);
        functions.put(IP_TO_GEO, null);
        functions.put(IP_TO_CITY, null);
        functions.put(IP_TO_COUNTRY, null);
        functions.put(IP_TO_ISP, null);
        functions.put(IP_TO_ASN, null);
        functions.put(IP_TO_ASN_DETAIL, null);
        functions.put(IP_TO_ASN_ORG, null);
        distributedGroupByFunction.put("count","sum");
        distributedGroupByFunction.put("sum","sum");
        distributedGroupByFunction.put("avg","avg");
        distributedGroupByFunction.put("min","min");
        distributedGroupByFunction.put("max","max");
        distributedGroupByFunction.put("median","median");
        timeSlicingFunction.put("count","sum");
        timeSlicingFunction.put("sum","sum");
        timeSlicingFunction.put("avg","avg");
        timeSlicingFunction.put("min","min");
        timeSlicingFunction.put("max","max");
    }

    /**
     * 标准函数
     */
    //time functions
    public static final Pattern pAggregateFunStandard = Pattern.compile("\\b(count|min|max|sum|avg|any|stddevPop|stddevSamp|varPop|varSamp|covarPop|covarSamp)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pDateFormat = Pattern.compile("\\W(FROM_UNIXTIME|DATE_FORMAT|STR_TO_DATE)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pGroupByFormat = Pattern.compile("(SELECT\\s+|GROUP\\s*BY\\s+|,\\s*)(FROM_UNIXTIME|DATE_FORMAT|STR_TO_DATE)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pDateRelative = Pattern.compile("\\W(ADDDATE|DATE_ADD|DATE_SUB|SUBDATE)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pIntervalNumUnit = Pattern.compile("INTERVAL(.*?)(YEAR|QUARTER|MONTH|WEEK|DAY|HOUR|MINUTE|SECOND)$", Pattern.CASE_INSENSITIVE);
    public static final Pattern pUnixTime = Pattern.compile("\\WUNIX_TIMESTAMP\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pDate = Pattern.compile("\\W(DATE)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pDateCalculateNum = Pattern.compile("\\(\\s*(DATE)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pUnitFuncGetNum = Pattern.compile("\\W(YEAR|QUARTER|MONTH|DAY|HOUR|MINUTE|SECOND|DAYOFYEAR|DAYOFMONTH|DAYOFWEEK)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pMarkDate = Pattern.compile("\\W(MAKEDATE)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pLastDateFunc = Pattern.compile("\\W(LAST_DAY)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pCKTimeStamp = Pattern.compile("\\s+TIMESTAMP\\s+([^'])", Pattern.CASE_INSENSITIVE);
    public static final Pattern pNow = Pattern.compile("NOW\\(\\s*\\)", Pattern.CASE_INSENSITIVE);
    public static final Pattern pStrDateTime = Pattern.compile("(<|>|=|between|and)\\s*('\\d{4}-\\d{2}-\\d{2}\\s*\\d{2}:\\d{2}:\\d{2}')", Pattern.CASE_INSENSITIVE);
    public static final Pattern pMergeFunction = Pattern.compile("\\WDATE_FORMAT\\(FROM_UNIXTIME\\s*\\(\\s*FLOOR\\s*\\(\\s*UNIX_TIMESTAMP\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pYearFunNum = Pattern.compile("(YEAR)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pTimeZone = Pattern.compile("\\W(CONVERT_TZ)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static final Pattern pTZFormat = Pattern.compile("\\+\\d{2}:\\d{2}", Pattern.CASE_INSENSITIVE);
    public static final Pattern strFormatDateTime = Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}", Pattern.CASE_INSENSITIVE);

    //other
    public static final Pattern pQuantile = Pattern.compile("\\WQUANTILE\\s*\\(");
    public static final Pattern pMedian = Pattern.compile("\\WMEDIAN\\s*\\(");
    public static final Pattern pCast = Pattern.compile("\\WCAST\\s*\\(");
    public static final Pattern pNotEmpty = Pattern.compile("\\b" + NOT_EMPTY + "\\(");
    public static final Pattern pEmpty = Pattern.compile("\\b" + EMPTY + "\\(");
    public static final Pattern pConcat = Pattern.compile("\\b" + CONCAT + "\\((?!toString\\(|CAST\\()", Pattern.CASE_INSENSITIVE);

    /**
     * 解析mysql函数替换为对应数据库类型函数
     *
     * @param sql    要解析的sql
     * @param dbType 数据库类型
     * @return
     */
    public static String generateDateFunction(String sql, String dbType) {
        sql = translateCommonFun(sql, dbType);
        sql = parseQuantile(sql, dbType);
        sql = parseMedian(sql, dbType);
        sql = parseCast(sql, dbType);
        sql = parseMergepFunction(sql, dbType);
        sql = parseMarkDate(sql, dbType);
        sql = parseDateCalculateNum(sql, dbType);
        sql = parseLastDayFunc(sql, dbType);
        sql = parseGroupByFormat(sql, dbType);
        sql = parseDateFormat(sql, dbType);
        sql = parseDateRelative(sql, dbType);
        sql = parseUnixTime(sql, dbType);
        sql = parseDate(sql, dbType);
        sql = parseUnitFuncGetNum(sql, dbType);
        sql = specialDisposeEnd(sql, dbType);
        sql = timeZoneFunc(sql, dbType);
        sql = replaceMark(sql);
        return sql;
    }

    public static String timeZoneFunc(String sql, String dbType) {
        while (true) {
            Matcher matcher = pTimeZone.matcher(sql);
            if (!matcher.find()) {
                return sql;
            }
            int start = matcher.start() + 1;
            String sqlParse = sql.substring(start);
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = dateTZReplace(sqlParse, bracketsMatch[0], dbType);
            sql = sql.substring(0, start) + str;
        }
    }

    public static String dateTZReplace(String sqlParse, int num, String dbType) {
        Pattern pDateFormatMark = Pattern.compile("(CONVERT_TZ)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)", Pattern.CASE_INSENSITIVE);
        Matcher mDateFormatMark = pDateFormatMark.matcher(sqlParse);
        StringBuffer sb = new StringBuffer();
        if (mDateFormatMark.find()) {
            String group2 = mDateFormatMark.group(2);
            String replaceValue = group2;
            if (StringUtil.getBracketsMatch(group2, "(", false)[0] >= 0) {
                replaceValue = dateTZReplaceByDB(mDateFormatMark.group(1), group2, dbType);
            }
            mDateFormatMark.appendReplacement(sb, replaceValue);
        }
        mDateFormatMark.appendTail(sb);
        return sb.toString();
    }

    public static String dateTZReplaceByDB(String func, String param, String dbType) {
        List<String> params = diviParam(param, ",");
        String replaceValue = null;
        if (("CONVERT_TZ").equals(func) && params.size() == 3) {
            int timeDifference;
            String oldTZString = params.get(1).replaceAll("'", "").replaceAll(" ", "");
            String startTZString = params.get(2).replaceAll("'", "").replaceAll(" ", "");
            if (pTZFormat.matcher(params.get(1)).find()) {
                timeDifference = (Integer.parseInt(startTZString.replaceAll(":", ""))) / 100 - (Integer.parseInt(oldTZString.replaceAll(":", ""))) / 100;
            } else {
                int oldTZInt = (Integer.parseInt(TimeZoneUtil.timeZoneTransition(oldTZString))) / 100;
                int startTZInt = (Integer.parseInt(TimeZoneUtil.timeZoneTransition(startTZString))) / 100;
                timeDifference = startTZInt - oldTZInt;
            }
            if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
                replaceValue = "addHours#[toDateTime#[" + params.get(0) + "," + params.get(1) + "#]," + timeDifference + "," + params.get(1) + "#]";
            } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
                if (!strFormatDateTime.matcher(params.get(0)).find()) {
                    replaceValue = "TIME_FORMAT#[TIME_SHIFT#[" + params.get(0) + "," + "'PT1H'," + timeDifference + "," + params.get(1) + "#],'yyyy-MM-dd HH:mm:ss'#]";
                } else {
                    replaceValue = "TIME_FORMAT#[TIME_SHIFT#[TIME_PARSE#[" + params.get(0) + "#]," + "'PT1H'," + timeDifference + "," + params.get(1) + "#],'yyyy-MM-dd HH:mm:ss'#]";
                }
            } else if (DBTypeEnum.HBASE.getValue().equals(dbType)) {
                if (!strFormatDateTime.matcher(params.get(0)).find()) {
                    replaceValue = "TO_CHAR#[" + func + "#[ " + params.get(0) + ",'" + TimeZoneUtil.timeZoneTransition(oldTZString) + "','" + TimeZoneUtil.timeZoneTransition(startTZString) + "'#],'yyyy-MM-dd HH:mm:ss'#]";
                } else {
                    replaceValue = "TO_CHAR#[" + func + "#[TO_TIMESTAMP#[" + params.get(0) + "#],'" + TimeZoneUtil.timeZoneTransition(oldTZString) + "','" + TimeZoneUtil.timeZoneTransition(startTZString) + "'#],'yyyy-MM-dd HH:mm:ss'#]";
                }
            }
        }
        return replaceValue;
    }

    public static String getDistributedGroupByFunction(String sourceAggregateFunctionName) {
        return distributedGroupByFunction.get(sourceAggregateFunctionName);
    }
    /**
     * 公共函数转义
     *
     * @param sql
     * @param dbType
     * @return
     */
    public static String translateCommonFun(String sql, String dbType) {
        String resultSql = translateUDF(dbType, sql);
        resultSql = translateStrFun(dbType, resultSql);
        return resultSql;
    }

    /**
     * Desc: notEmpty、empty、concat
     *
     * @param dbType
     * @param resultSql
     * @return {@link String}
     * @created by wWei
     * @date 2022/3/21 9:11 下午
     */
    private static String translateStrFun(String dbType, String resultSql) {
        resultSql = translateByPattern(resultSql, dbType, pNotEmpty);
        resultSql = translateByPattern(resultSql, dbType, pEmpty);
        resultSql = translateByPattern(resultSql, dbType, pConcat);
        return resultSql;
    }

    /**
     * Desc: 转义UDF
     *
     * @param dbType
     * @param resultSql
     * @return {@link String}
     * @created by wWei
     * @date 2022/3/21 9:02 下午
     */
    private static String translateUDF(String dbType, String resultSql) {
        resultSql = translateByPattern(resultSql, dbType, pTimeFloorWithFill);
        resultSql = translateByPattern(resultSql, dbType, pMaxDuration);
        resultSql = translateByPattern(resultSql, dbType, pIpToGeo);
        resultSql = translateByPattern(resultSql, dbType, pIpToCity);
        resultSql = translateByPattern(resultSql, dbType, pIpToCountry);
        resultSql = translateByPattern(resultSql, dbType, pIpToIsp);
        resultSql = translateByPattern(resultSql, dbType, pIpToAsn);
        resultSql = translateByPattern(resultSql, dbType, pIpToAsnDetail);
        resultSql = translateByPattern(resultSql, dbType, pIpToAsnOrg);
        resultSql = translateByPattern(resultSql, dbType, pMedianHdr);
        resultSql = translateByPattern(resultSql, dbType, pQuantileHdr);
        resultSql = translateByPattern(resultSql, dbType, pPercentilesHdr);
        resultSql = translateByPattern(resultSql, dbType, pCountDistinct);
        resultSql = translateByPattern(resultSql, dbType, pApproxCountDistinctHlld);
        return resultSql;
    }

    /**
     * 查询数据源获取函数实际值
     *
     * @param query
     * @param dbType
     * @return
     */
    public static String getQueryValue(String query, String dbType) {
        if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
            query = generateDateFunction("select " + query + " as actualValue", dbType);
            query = Encodes.urlEncode(query);
            Map<String, String> results = executeHttpGetOfCK(query);
            Map<String, Object> maps = JSON.parseObject(results.get("result"), Map.class);
            List<Map<String, Object>> dates = (List<Map<String, Object>>) maps.get("data");
            Map<String, Object> data = dates.get(0);
            Object actualValue = data.get("actualValue");
            return actualValue.toString();
        } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
            query = generateDateFunction("select TIME_FORMAT(" + query + ", 'yyyy-MM-dd HH:mm:ss') as actualValue", dbType);
            Map<String, String> results = executeHttpPostOfDruid(query);
            List<Object> maps = (List<Object>) JSON.parseObject(results.get("result"), Object.class);
            Map<String, Object> data = (Map<String, Object>) maps.get(0);
            Object actualValue = data.get("actualValue");
            return actualValue.toString();
        } else {
            return query;
        }
    }

    /**
     * post查询druid
     *
     * @param query
     * @return
     */
    public static Map<String, String> executeHttpPostOfDruid(String query) {
        String queryURL = URLUtil.normalize(druidIoHttpSource.getUrl());
        DruidQueryParam druidQueryParam = new DruidQueryParam();
        druidQueryParam.setQuery(query);
        druidQueryParam.getContext().put("skipEmptyBuckets", druidIoHttpSource.getSkipEmptyBuckets());
        druidQueryParam.setResultFormat("object");
        int socketTimeOut = httpConfig.getDruidSocketTimeOut();
        Map<String, String> stringStringMap = httpClientService.httpPost(queryURL, JSON.toJSONString(druidQueryParam), socketTimeOut);
        return stringStringMap;

    }

    /**
     * get查询clickHouse
     *
     * @param sql
     * @return
     */
    private static Map<String, String> executeHttpGetOfCK(String sql) {
        String queryURL = URLUtil.normalize(clickHouseHttpSource.getUrl() + "/?");
        StringBuilder queryparamBuilder = new StringBuilder("user=")
                .append(clickHouseHttpSource.getRealTimeAccountUserName()).append("&")
                .append("password=").append(clickHouseHttpSource.getRealTimeAccountPin()).append("&")
                .append("database=").append(clickHouseHttpSource.getDbName()).append("&")
                .append("query=").append(sql)
                .append(" FORMAT JSON;");
        List<NameValuePair> values = URLEncodedUtils.parse(queryparamBuilder.toString(), Charset.forName("UTF-8"));
        int socketTimeOut = httpConfig.getCkRealTimeAccountSocketTimeOut();
        Map<String, String> stringStringMap = httpClientService.httpGet(queryURL + URLEncodedUtils.format(values, "utf-8"), socketTimeOut);
        return stringStringMap;
    }


    /**
     * 匹配连接函数: DATE_FORMAT(FROM_UNIXTIME(UNIX_TIMESTAMP(
     *
     * @param sql
     * @param dbType
     * @return
     */
    private static String parseMergepFunction(String sql, String dbType) {
        while (true) {
            Matcher matcher = pMergeFunction.matcher(sql);
            if (!matcher.find()) {
                return sql;
            }
            int start = matcher.start() + 1;
            String sqlParse = sql.substring(start);
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = mergeFromUnixToTimeReplaceByDB(sqlParse, bracketsMatch[0], dbType);
            sql = sql.substring(0, start) + str;
        }
    }


    /**
     * 匹配范围：FROM_UNIXTIME(、DATE_FORMAT(、STR_TO_DATE(
     *
     * @param sql    需要解析的sql
     * @param dbType
     * @return
     */
    public static String parseDateFormat(String sql, String dbType) {
        while (true) {
            Matcher matcher = pDateFormat.matcher(sql);
            if (!matcher.find()) {
                return sql;
            }
            int start = matcher.start() + 1;
            String sqlParse = sql.substring(start);
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = dateFormatdateFormatReplace(sqlParse, bracketsMatch[0], false, dbType);
            sql = sql.substring(0, start) + str;
        }
    }

    /**
     * 匹配范围：QUANTILE(
     *
     * @param sql    需要解析的sql
     * @param dbType 数据库的类型
     * @return
     */
    public static String parseQuantile(String sql, String dbType) {
        while (true) {
            Matcher matcher = pQuantile.matcher(sql);
            if (!matcher.find()) {
                return sql;
            }
            int start = matcher.start() + 1;
            String sqlParse = sql.substring(start);
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = parseQuantileReplace(sqlParse, bracketsMatch[0], dbType);
            sql = sql.substring(0, start) + str;
        }
    }

    /**
     * 替换：QUANTILE(expr,level)、QUANTILE(expr)
     *
     * @param sqlParse
     * @param num      包含括号的个数
     * @param dbType
     * @return
     */
    public static String parseQuantileReplace(String sqlParse, int num, String dbType) {
        Pattern pNumQuantile = Pattern.compile("(QUANTILE)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)");
        Matcher matcher = pNumQuantile.matcher(sqlParse);
        StringBuffer sb = new StringBuffer();
        if (matcher.find()) {
            String group2 = matcher.group(2);
            String replaceValue = group2;
            if (StringUtil.getBracketsMatch(group2, "(", false)[0] >= 0) {
                replaceValue = quantileReplaceByDB(group2, dbType);
            }
            matcher.appendReplacement(sb, replaceValue);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 替换：匹配范围：QUANTILE(
     *
     * @param param  截取到的括号内的参数
     * @param dbType
     * @return
     */
    private static String quantileReplaceByDB(String param, String dbType) {
        List<String> params = diviParam(param, ",");
        String replaceValue = null;
        if (params.size() == 1) {
            params.add("0.5");
        }
        if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
            replaceValue = "quantile#[" + params.get(1) + "#]#[" + params.get(0) + "#]";
        } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
            replaceValue = "APPROX_QUANTILE_DS#[" + params.get(0) + "," + params.get(1) + "#]";
        }
        return replaceValue;
    }

    public static String parseMedian(String sql, String dbType){
        while (true) {
            Matcher matcher = pMedian.matcher(sql);
            if (!matcher.find()){
                return sql;
            }
            int start = matcher.start() + 1;
            String sqlParse = sql.substring(start);
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = parseMedianReplace(sqlParse, bracketsMatch[0], dbType);
            sql = sql.substring(0, start) + str;
        }
    }

    public static String parseMedianReplace(String sqlParse, int num, String dbType){
        Pattern pNumMedian = Pattern.compile("(MEDIAN)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)");
        Matcher matcher = pNumMedian.matcher(sqlParse);
        StringBuffer sb = new StringBuffer();
        if (matcher.find()) {
            String group2 = matcher.group(2);
            String replaceValue = group2;
            if (StringUtil.getBracketsMatch(group2, "(", false)[0] >= 0) {
                replaceValue = medianReplaceByDB(group2, dbType);
            }
            matcher.appendReplacement(sb, replaceValue);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }


    private static String medianReplaceByDB(String param, String dbType){
        String replaceValue = null;
        if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
            replaceValue = "median#[" + param + "#]";
        } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
            replaceValue = "APPROX_QUANTILE_DS#[" + param + "," + 0.5 + "#]";
        }
        return replaceValue;
    }

    public static String parseCast(String sql, String dbType) {
        while (true) {
            Matcher matcher = pCast.matcher(sql);
            if (!matcher.find()) {
                return sql;
            }
            int start = matcher.start() + 1;
            String sqlParse = sql.substring(start);
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = parseCastReplace(sqlParse, bracketsMatch[0], dbType);
            sql = sql.substring(0, start) + str;
        }
    }

    public static String parseCastReplace(String sqlParse, int num, String dbType) {
        Pattern pNumCast = Pattern.compile("(CAST)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)");
        Matcher matcher = pNumCast.matcher(sqlParse);
        StringBuffer sb = new StringBuffer();
        if (matcher.find()) {
            String group0 = matcher.group(0);
            String replaceValue = group0;
            if (StringUtil.getBracketsMatch(group0, "(", false)[0] >= 0) {
                replaceValue = castReplaceByDB(group0, dbType);
            }
            matcher.appendReplacement(sb, replaceValue);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }


    private static String castReplaceByDB(String param, String dbType) {
        String replaceValue = "";
        try {
            Expression expression = CCJSqlParserUtil.parseExpression(param);
            if (expression instanceof CastExpression) {
                CastExpression castExpression = (CastExpression) expression;
                if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
                    String dataType = castExpression.getType().getDataType();
                    castExpression.getType().setDataType("Nullable(" + dataType + ")");
                }
                replaceValue = String.valueOf(castExpression).replace("(", "#[").replace(")", "#]");
            }
        } catch (JSQLParserException e) {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getMessage(), e.getMessage()));
        }
        return replaceValue;
    }

    /**
     * 匹配格式化 FROM_UNIXTIME(、DATE_FORMAT(、STR_TO_DATE(
     *
     * @param sql
     * @param dbType
     * @return
     */
    public static String parseGroupByFormat(String sql, String dbType) {
        while (true) {
            Matcher matcher = pGroupByFormat.matcher(sql);
            if (!matcher.find()) {
                return sql;
            }
            int start = matcher.start();
            String sqlParse = sql.substring(start);
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = dateFormatdateFormatReplace(sqlParse, bracketsMatch[0], true, dbType);
            sql = sql.substring(0, start) + str;
        }
    }

    /**
     * 匹配 SUBDATE(、SUBDATE(、SUBDATE(、ADDDATE(、DATE_SUB(、DATE_ADD(
     *
     * @param sql
     * @param dbType
     * @return
     */
    public static String parseDateRelative(String sql, String dbType) {
        while (true) {
            Matcher matcher = pDateRelative.matcher(sql);
            if (!matcher.find()) {
                return sql;
            }
            int start = matcher.start() + 1;
            String sqlParse = sql.substring(start);
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, null, false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = dateReplace(sqlParse, bracketsMatch[0], dbType);
            sql = sql.substring(0, start) + str;
        }
    }

    /**
     * 匹配 UNIX_TIMESTAMP(
     *
     * @param sql
     * @param dbType
     * @return
     */
    public static String parseUnixTime(String sql, String dbType) {
        while (true) {
            Matcher matcher = pUnixTime.matcher(sql);
            if (!matcher.find()) {
                return sql;
            }
            int start = matcher.start() + 1;
            String sqlParse = sql.substring(start);
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, null, false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = parseUnixTimeReplaceByDB(sqlParse, bracketsMatch[0], dbType);
            sql = sql.substring(0, start) + str;
        }
    }

    /**
     * 匹配 DATE(
     *
     * @param sql
     * @param dbType
     * @return
     */
    public static String parseDate(String sql, String dbType) {
        while (true) {
            Matcher matcher = pDate.matcher(sql);
            if (!matcher.find()) {
                return sql;
            }
            int start = matcher.start() + 1;
            String sqlParse = sql.substring(start);
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = parseDateReplaceByDB(sqlParse, bracketsMatch[0], dbType);
            sql = sql.substring(0, start) + str;
        }
    }

    /**
     * 匹配 YEAR(、QUARTER(、MONTH(、DAY(、HOUR(、MINUTE(、SECOND、DAYOFYEAR(、DAYOFMONTH(、DAYOFWEEK(
     *
     * @param sql
     * @param dbType
     * @return
     */
    public static String parseUnitFuncGetNum(String sql, String dbType) {
        while (true) {
            Matcher matcher = pUnitFuncGetNum.matcher(sql);
            if (!matcher.find()) {
                return sql;
            }
            int start = matcher.start() + 1;
            String sqlParse = sql.substring(start);
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = parseUnitFuncGetNumReplace(sqlParse, bracketsMatch[0], dbType);
            sql = sql.substring(0, start) + str;
        }
    }

    /**
     * 匹配 pMarkDate(
     *
     * @param sql
     * @param dbType
     * @return
     */
    public static String parseMarkDate(String sql, String dbType) {
        while (true) {
            Matcher matcher = pMarkDate.matcher(sql);
            if (!matcher.find()) {
                return sql;
            }
            int start = matcher.start() + 1;
            String sqlParse = sql.substring(start);
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = parseMarkDateReplace(sqlParse, bracketsMatch[0], dbType);
            sql = sql.substring(0, start) + str;
        }
    }

    /**
     * 匹配 LAST_DAY(
     *
     * @param sql
     * @param dbType
     * @return
     */
    public static String parseLastDayFunc(String sql, String dbType) {
        while (true) {
            Matcher matcher = pLastDateFunc.matcher(sql);
            if (!matcher.find()) {
                return sql;
            }
            int start = matcher.start() + 1;
            String sqlParse = sql.substring(start);
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = parseLastDayFuncReplace(sqlParse, bracketsMatch[0], dbType);
            sql = sql.substring(0, start) + str;
        }
    }

    /**
     * 匹配 涉及DATE()-、DATE()+
     *
     * @param sql
     * @param dbType
     * @return
     */
    public static String parseDateCalculateNum(String sql, String dbType) {
        String sqlTmp = "";
        while (true) {
            Matcher matcher = pDateCalculateNum.matcher(sql);
            if (!matcher.find()) {
                sql = sql.replace("##", "(");
                return sql;
            }
            String sqlParse = sql.substring(matcher.start());
            if (sqlTmp.equals(sqlParse)) {
                sqlParse = "##" + sqlParse.substring(1);
                sql = sql.substring(0, matcher.start()) + sqlParse;
                continue;
            }
            sqlTmp = sqlParse;
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String sqlParse2 = sql.substring(matcher.start() + 1);
            int[] bracketsMatch2 = StringUtil.getBracketsMatch(sqlParse2, "(", false);
            if (bracketsMatch2[0] >= 1) {
                --bracketsMatch2[0];
            }
            String str = parseDateCalculateNumReplaceByDB(sqlParse, bracketsMatch[0], bracketsMatch2[0], dbType);
            sql = sql.substring(0, matcher.start()) + str;
        }
    }


    /**
     * 替换连接函数: DATE_FOERMAT(FROM_UNIXTIME(UNIX_TIMESTAMP(...)
     *
     * @param sql
     * @param num
     * @param dbType
     * @return
     */
    private static String mergeFromUnixToTimeReplaceByDB(String sql, int num, String dbType) {
        Pattern p = Pattern.compile("DATE_FORMAT\\s*\\(\\s*FROM_UNIXTIME\\s*\\(\\s*FLOOR\\s*\\(\\s*UNIX_TIMESTAMP\\s*(\\((.*?\\).*?){" + num + "})\\)", Pattern.CASE_INSENSITIVE);
        StringBuffer sb = new StringBuffer();
        Matcher m = p.matcher(sql);
        if (m.find()) {
            String group = m.group(1);
            int[] bracketsMatch = StringUtil.getBracketsMatch(group, "(", false);
            String param = group.substring(1, bracketsMatch[1]);
            String var = group.substring(bracketsMatch[1] + 1);
            var = var.replaceAll(" ", "");
            String replaceValue = m.group(0);
            if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
                switch (var) {
                    case "/60)*60),'%Y-%m-%d%H:%i:%s'":
                        replaceValue = "toStartOfMinute#[toDateTime#[" + param + "#]#]";
                        break;
                    case "/3600)*3600),'%Y-%m-%d%H:%i:%s'":
                        replaceValue = "toStartOfHour#[toDateTime#[" + param + "#]#]";
                        break;
                }
            } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
                if (param.matches("'\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}'")) {
                    param = "TIMESTAMP " + param;
                }
                switch (var) {
                    case "/30)*30),'%Y-%m-%d%H:%i:%s'":
                        replaceValue = "TIME_FORMAT#[TIME_FLOOR#[" + param + ",'PT30S'#],'yyyy-MM-dd HH:mm:ss'#]";
                        break;
                    case "/30)*30-30),'%Y-%m-%d%H:%i:%s'":
                        replaceValue = "TIME_FORMAT#[TIME_SHIFT#[TIME_FLOOR#[" + param + ",'PT30S'#],'PT30S',-1#],'yyyy-MM-dd HH:mm:ss'#]";
                        break;
                    case "/30)*30+30),'%Y-%m-%d%H:%i:%s'":
                        replaceValue = "TIME_FORMAT#[TIME_SHIFT#[TIME_FLOOR#[" + param + ",'PT30S'#],'PT30S',1#],'yyyy-MM-dd HH:mm:ss'#]";
                        break;
                    case "/60)*60),'%Y-%m-%d%H:%i:%s'":
                        replaceValue = "TIME_FORMAT#[TIME_FLOOR#[" + param + ",'PT1M'#],'yyyy-MM-dd HH:mm:ss'#]";
                        break;
                    case "/60)*60-60),'%Y-%m-%d%H:%i:%s'":
                        replaceValue = "TIME_FORMAT#[TIME_SHIFT#[TIME_FLOOR#[" + param + ",'PT1M'#],'PT1M',-1#],'yyyy-MM-dd HH:mm:ss'#]";
                        break;
                    case "/60)*60+60),'%Y-%m-%d%H:%i:%s'":
                        replaceValue = "TIME_FORMAT#[TIME_SHIFT#[TIME_FLOOR#[" + param + ",'PT1M'#],'PT1M',1#],'yyyy-MM-dd HH:mm:ss'#]";
                        break;
                    case "/300)*300),'%Y-%m-%d%H:%i:%s'":
                        replaceValue = "TIME_FORMAT#[TIME_FLOOR#[" + param + ",'PT5M'#],'yyyy-MM-dd HH:mm:ss'#]";
                        break;
                    case "/300)*300-300),'%Y-%m-%d%H:%i:%s'":
                        replaceValue = "TIME_FORMAT#[TIME_SHIFT#[TIME_FLOOR#[" + param + ",'PT5M'#],'PT5M',-1#],'yyyy-MM-dd HH:mm:ss'#]";
                        break;
                    case "/300)*300+300),'%Y-%m-%d%H:%i:%s'":
                        replaceValue = "TIME_FORMAT#[TIME_SHIFT#[TIME_FLOOR#[" + param + ",'PT5M'#],'PT5M',1#],'yyyy-MM-dd HH:mm:ss'#]";
                        break;
                    case "/3600)*3600),'%Y-%m-%d%H:%i:%s'":
                        replaceValue = "TIME_FORMAT#[TIME_FLOOR#[" + param + ",'PT1H'#],'yyyy-MM-dd HH:mm:ss'#]";
                        break;
                    case "/3600)*3600-3600),'%Y-%m-%d%H:%i:%s'":
                        replaceValue = "TIME_FORMAT#[TIME_SHIFT#[TIME_FLOOR#[" + param + ",'PT1H'#],'PT1H',-1#],'yyyy-MM-dd HH:mm:ss'#]";
                        break;
                    case "/3600)*3600+3600),'%Y-%m-%d%H:%i:%s'":
                        replaceValue = "TIME_FORMAT#[TIME_SHIFT#[TIME_FLOOR#[" + param + ",'PT1H'#],'PT1H',1#],'yyyy-MM-dd HH:mm:ss'#]";
                        break;
                }
            }
            m.appendReplacement(sb, replaceValue);
        }
        m.appendTail(sb);
        return sb.toString();
    }


    /**
     * 替换 涉及DATE()-、DATE()+
     *
     * @param sqlParse
     * @param num      括号总个数
     * @param num2     date函数内括号数
     * @param dbType
     * @return
     */
    private static String parseDateCalculateNumReplaceByDB(String sqlParse, int num, int num2, String dbType) {
        Pattern pDateCalculateNumParse = Pattern.compile("(DATE)\\s*\\((.*?(.*?\\).*?){" + num2 + "})\\)(.*?(.*?\\).*?){" + (num - num2 - 1) + "})\\)", Pattern.CASE_INSENSITIVE);
        StringBuffer sb = new StringBuffer();
        Matcher mDateCalculateNumParse = pDateCalculateNumParse.matcher(sqlParse);
        if (mDateCalculateNumParse.find() && (mDateCalculateNumParse.group(4).trim().startsWith("+") || mDateCalculateNumParse.group(4).trim().startsWith("-"))) {
            String group0 = mDateCalculateNumParse.group(0);
            String group2 = mDateCalculateNumParse.group(2);
            String group4 = mDateCalculateNumParse.group(4);
            if (StringUtil.getBracketsMatch(group0.substring(0, group0.length() - 1), "(", true)[0] == num) {
                String[] split = group4.split(",");
                String replaceValue = "";
                if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
                    if (split.length == 1) {
                        replaceValue = "toStartOfDay#[toDateTime#[" + group2 + "#]#]+#[" + group4 + "#]*86400 )";
                    } else if (split.length == 2) {
                        replaceValue = "toStartOfDay#[toDateTime#[" + group2 + "#]#]+#[" + split[0] + "#]*86400 ," + split[1] + ")";
                    }
                } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
                    if (split.length == 1) {
                        replaceValue = "TIME_SHIFT#[FLOOR#[ TIMESTAMP " + group2 + " to day#],'P1D'," + group4 + "#])";
                    } else if (split.length == 2) {
                        replaceValue = "TIME_SHIFT#[FLOOR#[ TIMESTAMP " + group2 + "to day#],'P1D'," + split[0] + "#]," + split[1] + ")";
                    }
                }
                mDateCalculateNumParse.appendReplacement(sb, replaceValue);
            }
        }
        mDateCalculateNumParse.appendTail(sb);
        return sb.toString();
    }


    /**
     * 替换 LAST_DAY(date)
     *
     * @param sqlParse
     * @param num      包含括号个数
     * @param dbType
     * @return
     */
    public static String parseLastDayFuncReplace(String sqlParse, int num, String dbType) {
        Pattern pLastDayParse = Pattern.compile("(LAST_DAY)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)", Pattern.CASE_INSENSITIVE);
        StringBuffer sb = new StringBuffer();
        Matcher mLastDayParse = pLastDayParse.matcher(sqlParse);
        if (mLastDayParse.find()) {
            String replaceValue = lastDayReplace(mLastDayParse.group(1), mLastDayParse.group(2), dbType);
            mLastDayParse.appendReplacement(sb, replaceValue);
        }
        mLastDayParse.appendTail(sb);
        return sb.toString();
    }

    /**
     * 实现替换：LAST_DAY(date)
     *
     * @param unit
     * @param param
     * @param dbType
     * @return
     */
    private static String lastDayReplace(String unit, String param, String dbType) {
        String replaceValue = null;
        if ("LAST_DAY".equals(StringUtils.upperCase(unit))) {
            if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
                replaceValue = "addDays#[addMonths#[toStartOfMonth#[toDateTime#[" + param + "#]#],1#],-1#]";
            } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
                replaceValue = "TIME_SHIFT#[FLOOR#[TIME_SHIFT#[ TIMESTAMP " + param + ", 'P1M', 1#] to month#], 'P1D', -1#]";
            }
        }
        return replaceValue;
    }

    /**
     * 替换：MAKEDATE(year,dayofyear)
     *
     * @param sqlParse
     * @param num      函数参数包含括号的个数
     * @param dbType
     * @return
     */
    public static String parseMarkDateReplace(String sqlParse, int num, String dbType) {
        Pattern pMarkDateParse = Pattern.compile("(MAKEDATE)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)", Pattern.CASE_INSENSITIVE);
        StringBuffer sb = new StringBuffer();
        Matcher mMarkDateParse = pMarkDateParse.matcher(sqlParse);
        if (mMarkDateParse.find()) {
            String replaceValue = markDateReplaceByDb(mMarkDateParse.group(1), mMarkDateParse.group(2), dbType);
            mMarkDateParse.appendReplacement(sb, replaceValue);
        }
        mMarkDateParse.appendTail(sb);
        return sb.toString();
    }

    /**
     * 实现替换：MAKEDATE(year,dayofyear)
     *
     * @param func
     * @param expr
     * @param dbType
     * @return
     */
    private static String markDateReplaceByDb(String func, String expr, String dbType) {
        String replaceValue = null;
        List<String> params = diviParam(expr, ",");
        if ("MAKEDATE".equals(StringUtils.upperCase(func)) && params.size() == 2) {
            Matcher matcher = pYearFunNum.matcher(expr);
            if (matcher.find() && "YEAR".equals(StringUtils.upperCase(matcher.group(1)))) {
                String temp = expr.substring(matcher.start());
                int[] bracketsMatch = StringUtil.getBracketsMatch(temp, "(", false);
                if (bracketsMatch[0] >= 1) {
                    --bracketsMatch[0];
                }
                Pattern pYear = Pattern.compile("^(.*?)(YEAR)\\s*\\((.*?(.*?\\).*?){" + bracketsMatch[0] + "})\\)(.*?)$", Pattern.CASE_INSENSITIVE);
                Matcher mYear = pYear.matcher(params.get(0));
                if (mYear.find()) {
                    String group1 = mYear.group(1);
                    String group5 = mYear.group(5);
                    if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
                        if (group1 != null && !"".equals(group1.trim()) && group5 != null && !"".equals(group5.trim())) {
                            replaceValue = "toDateTime#[addDays#[addYears#[toStartOfYear#[toDateTime#[" + mYear.group(3) + "#]#]," + group1 + " " + group5 + "#]," + params.get(1) + " -1 #]#]";
                        } else if (group5 != null && !"".equals(group5.trim())) {
                            replaceValue = "toDateTime#[addDays#[addYears#[toStartOfYear#[toDateTime#[" + mYear.group(3) + "#]#]," + group5 + "#]," + params.get(1) + " -1 #]#]";
                        } else if (group1 != null && !"".equals(group1.trim())) {
                            replaceValue = "toDateTime#[addDays#[addYears#[toStartOfYear#[toDateTime#[" + mYear.group(3) + "#]#]," + group1 + "0 #]," + params.get(1) + " -1 #]#]";
                        } else {
                            replaceValue = "toDateTime#[addDays#[toStartOfYear#[toDateTime#[" + mYear.group(3) + "#]#]," + params.get(1) + " -1 #]#]";
                        }
                    } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
                        if (group1 != null && !"".equals(group1.trim()) && group5 != null && !"".equals(group5.trim())) {
                            replaceValue = "TIME_SHIFT#[TIME_SHIFT#[FLOOR#[ TIMESTAMP " + mYear.group(3) + " to year#],'P1Y'," + group1 + " " + group5 + " #],'P1D'," + params.get(1) + " -1 #]";
                        } else if (group5 != null && !"".equals(group5.trim())) {
                            replaceValue = "TIME_SHIFT#[TIME_SHIFT#[FLOOR#[ TIMESTAMP " + mYear.group(3) + " to year#],'P1Y'," + group5 + "#],'P1D'," + params.get(1) + " -1 #]";
                        } else if (group1 != null && !"".equals(group1.trim())) {
                            replaceValue = "TIME_SHIFT#[TIME_SHIFT#[FLOOR#[ TIMESTAMP " + mYear.group(3) + " to year#],'P1Y'," + group1 + "0 #],'P1D'," + params.get(1) + " -1 #]";
                        } else {
                            replaceValue = "TIME_SHIFT#[FLOOR#[ TIMESTAMP " + mYear.group(3) + " to year#],'P1D'," + params.get(1) + "-1 #]";
                        }
                    }
                }
            }
        }
        return replaceValue;
    }

    /**
     * 替换 YEAR(expr)、QUARTER(expr)、MONTH(expr)、DAY(expr)、HOUR(expr)、MINUTE(expr)、SECONDexpr)、DAYOFYEAR(expr)、DAYOFMONTH(expr)、DAYOFWEEK(expr)
     *
     * @param sqlParse
     * @param num
     * @param dbType
     * @return
     */
    private static String parseUnitFuncGetNumReplace(String sqlParse, int num, String dbType) {
        Pattern pUnitFuncParse = Pattern.compile("(YEAR|QUARTER|MONTH|DAY|HOUR|MINUTE|SECOND|DAYOFYEAR|DAYOFMONTH|DAYOFWEEK)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)", Pattern.CASE_INSENSITIVE);
        StringBuffer sb = new StringBuffer();
        Matcher mUnitFuncParse = pUnitFuncParse.matcher(sqlParse);
        if (mUnitFuncParse.find()) {
            String replaceValue = unitFuncGetNumReplaceByDB(mUnitFuncParse.group(1), mUnitFuncParse.group(2), dbType);
            mUnitFuncParse.appendReplacement(sb, replaceValue);
        }
        mUnitFuncParse.appendTail(sb);
        return sb.toString();
    }

    /**
     * 实现替换：YEAR(expr)、QUARTER(expr)、MONTH(expr)、DAY(expr)、HOUR(expr)、MINUTE(expr)、SECONDexpr)、DAYOFYEAR(expr)、DAYOFMONTH(expr)、DAYOFWEEK(expr)
     *
     * @param unit
     * @param expr
     * @param dbType
     * @return
     */
    private static String unitFuncGetNumReplaceByDB(String unit, String expr, String dbType) {
        String replaceValue = null;
        unit = StringUtils.upperCase(unit);
        if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
            switch (unit) {
                case "YEAR":
                    replaceValue = "toYear#[toDateTime#[" + expr + "#]#]";
                    break;
                case "QUARTER":
                    replaceValue = "toQuarter#[toDateTime#[" + expr + "#]#]";
                    break;
                case "MONTH":
                    replaceValue = "toMonth#[toDateTime#[" + expr + "#]#]";
                    break;
                case "DAY":
                    replaceValue = "toDayOfMonth#[toDateTime#[" + expr + "#]#]";
                    break;
                case "HOUR":
                    replaceValue = "toHour#[toDateTime#[" + expr + "#]#]";
                    break;
                case "MINUTE":
                    replaceValue = "toMinute#[toDateTime#[" + expr + "#]#]";
                    break;
                case "SECOND":
                    replaceValue = "toSecond#[toDateTime#[" + expr + "#]#]";
                    break;
                case "DAYOFYEAR":
                    replaceValue = "toDayOfYear#[toDateTime#[" + expr + "#]#]";
                    break;
                case "DAYOFMONTH":
                    replaceValue = "toDayOfMonth#[toDateTime#[" + expr + "#]#]";
                    break;
                case "DAYOFWEEK":
                    replaceValue = "toDayOfWeek#[addDays#[toDateTime#[" + expr + "#],1#]#]";
                    break;
                default:
                    replaceValue = unit + "#[" + expr + "#]";
            }
        } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
            switch (unit) {
                case "YEAR":
                    replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0#], 'YEAR' #]";
                    break;
                case "QUARTER":
                    replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0#], 'QUARTER' #]";
                    break;
                case "MONTH":
                    replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0#], 'MONTH' #]";
                    break;
                case "DAY":
                    replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0#], 'DAY' #]";
                    break;
                case "HOUR":
                    replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0#], 'HOUR' #]";
                    break;
                case "MINUTE":
                    replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0#], 'MINUTE' #]";
                    break;
                case "SECOND":
                    replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0#], 'SECOND' #]";
                    break;
                case "DAYOFYEAR":
                    replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0#], 'DOY' #]";
                    break;
                case "DAYOFMONTH":
                    replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0#], 'DAY' #]";
                    break;
                case "DAYOFWEEK":
                    replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'P1D',1#], 'DOW' #]";
                    break;
                default:
                    replaceValue = unit + "#[" + expr + "#]";
            }
        }
        return replaceValue;
    }

    /**
     * 实现相对时间相关替换：
     *
     * @param params
     * @param sign   ADD相关为true,SUB相关为false;
     * @param dbType
     * @return
     */
    private static String dateReplaceByDb(List<String> params, Boolean sign, String dbType) {
        String replaceValue = "";
        if (params.size() == 2 && StringUtils.upperCase(params.get(1)).contains("INTERVAL")) {
            String param1 = params.get(0);
            String param2 = params.get(1);
            Matcher matcher = pIntervalNumUnit.matcher(param2);
            if (matcher.find()) {
                param2 = matcher.group(1);
                if (!sign) {
                    param2 = "-  #[" + param2 + "#]";
                }
                if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
                    switch (StringUtils.upperCase(matcher.group(2))) {
                        case "SECOND":
                            replaceValue = "toDateTime#[" + param1 + "#]" + param2;
                            break;
                        case "MINUTE":
                            replaceValue = "toDateTime#[" + param1 + "#]" + param2 + "*60";
                            break;
                        case "HOUR":
                            replaceValue = "toDateTime#[" + param1 + "#]" + param2 + "*3600";
                            break;
                        case "DAY":
                            replaceValue = "toDateTime#[" + param1 + "#]" + param2 + "*86400";
                            break;
                        case "WEEK":
                            replaceValue = "toDateTime#[" + param1 + "#]" + param2 + "*604800";
                            break;
                        case "MONTH":
                            replaceValue = "addMonths#[toDateTime#[" + param1 + "#] ," + param2 + "#]";
                            break;
                        case "QUARTER":
                            replaceValue = "addQuarter#[toDateTime#[" + param1 + "#]," + param2 + "#]";
                            break;
                        case "YEAR":
                            replaceValue = "addYears#[toDateTime#[" + param1 + "#]," + param2 + "#]";
                            break;
                        default:
                            replaceValue = param1 + param2;
                            break;
                    }
                } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
                    switch (StringUtils.upperCase(matcher.group(2))) {
                        case "SECOND":
                            replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'PT1S'," + param2 + "#]";
                            break;
                        case "MINUTE":
                            replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'PT1M'," + param2 + "#]";
                            break;
                        case "HOUR":
                            replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'PT1H'," + param2 + "#]";
                            break;
                        case "DAY":
                            replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'P1D'," + param2 + "#]";
                            break;
                        case "WEEK":
                            replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'P1W'," + param2 + "#]";
                            break;
                        case "MONTH":
                            replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'P1M'," + param2 + "#]";
                            break;
                        case "QUARTER":
                            replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'P1M'," + param2 + "#]";
                            break;
                        case "YEAR":
                            replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'P1Y'," + param2 + "#]";
                            break;
                        default:
                            replaceValue = param1 + param2;
                            break;
                    }
                }
                return replaceValue;
            }
        } else if (params.size() == 2) {
            String param1 = params.get(0);
            String param2 = params.get(1);
            if (!sign) {
                param2 = "-  #[" + param2 + "#]";
            }
            if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
                replaceValue = "addDays#[toDateTime#[" + param1 + "#]," + param2 + "#]";
            } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
                replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'P1D'," + param2 + "#]";
            }
        } else if (params.size() == 3 && DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
            replaceValue = "DATE_ADD#[" + params.get(0) + "," + params.get(1) + "," + params.get(2) + "#]";

        }
        return replaceValue;
    }

    /**
     * 实现替换：FROM_UNIXTIME(unix_timestamp)、FROM_UNIXTIME(date,format)、DATE_FORMAT(date,format)、STR_TO_DATE(date,format)
     *
     * @param func
     * @param param
     * @param dbType
     * @return
     */
    private static String dateFormatdateFormatReplaceByDB(String func, String param, boolean bool, String dbType) {
        List<String> params = diviParam(param, ",");
        String param1 = params.get(0);
        String replaceValue = null;
        if ("FROM_UNIXTIME".equals(StringUtils.upperCase(func)) && params.size() == 1) {
            if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
                replaceValue = "toDateTime#[" + param + "#]";
            } else if (DBTypeEnum.DRUID.getValue().equals(dbType) && !bool) {
                replaceValue = "MILLIS_TO_TIMESTAMP#[ 1000 * #[" + param + "#]#]";
            } else if (DBTypeEnum.DRUID.getValue().equals(dbType) && bool) {
                replaceValue = "TIME_FORMAT#[MILLIS_TO_TIMESTAMP#[ 1000 * #[" + param + "#]#],'YYYY-MM-dd HH:mm:ss'#]";
            }
        } else if (func.contains("FROM_UNIXTIME") && DBTypeEnum.DRUID.getValue().equals(dbType) && "%Y-%m-%d %H:%i:%s".equals(params.get(1).replaceAll("\\'|\\\"", "").trim())) {
            if (!bool) {
                replaceValue = "MILLIS_TO_TIMESTAMP#[ 1000*#[" + param1 + "#]#]";
            } else if (bool) {
                replaceValue = "TIME_FORMAT#[MILLIS_TO_TIMESTAMP#[ 1000 * #[" + param + "#]#],'YYYY-MM-dd HH:mm:ss'#]";
            }
        } else {
            String param2 = params.get(1).replaceAll("\\'|\\\"", "").trim();
            if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType) && bool) {
                switch (param2) {
                    case "%Y-%m-%d %H:%i:%s":
                        replaceValue = "toDateTime#[" + param1 + "#]";
                        break;
                    case "%Y-%m-%d %H:%i:00":
                        replaceValue = "toStartOfMinute#[toDateTime#[" + param1 + "#]#]";
                        break;
                    case "%Y-%m-%d %H:00:00":
                        replaceValue = "toStartOfHour#[toDateTime#[" + param1 + "#]#]";
                        break;
                    case "%Y-%m-%d 00:00:00":
                        replaceValue = "toStartOfDay#[toDateTime#[" + param1 + "#]#]";
                        break;
                    case "%Y-%m-01 00:00:00":
                        replaceValue = "toDateTime#[toStartOfMonth#[toDateTime#[" + param1 + "#]#]#]";
                        break;
                    case "%Y-01-01 00:00:00":
                        replaceValue = "toDateTime#[toStartOfYear#[toDateTime#[" + param1 + "#]#]#]";
                        break;
                    case "%Y-%m-%d":
                        replaceValue = "formatDateTime#[toDateTime#[" + param1 + "#], '%Y-%m-%d'#]";
                        break;
                    case "%Y-%m-01":
                        replaceValue = "formatDateTime#[toDateTime#[" + param1 + "#], '%Y-%m-01'#]";
                        break;
                    case "%Y-01-01":
                        replaceValue = "formatDateTime#[toDateTime#[" + param1 + "#], '%Y-01-01'#]";
                        break;
                    default:
                        replaceValue = "toDateTime#[" + param1 + "#]";
                }
            } else if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType) && !bool) {
                switch (param2) {
                    case "%Y-%m-%d %H:%i:%s":
                        replaceValue = "toDateTime#[" + param1 + "#]";
                        break;
                    case "%Y-%m-%d %H:%i:00":
                        replaceValue = "toStartOfMinute#[toDateTime#[" + param1 + "#]#]";
                        break;
                    case "%Y-%m-%d %H:00:00":
                        replaceValue = "toStartOfHour#[toDateTime#[" + param1 + "#]#]";
                        break;
                    case "%Y-%m-%d 00:00:00":
                        replaceValue = "toStartOfDay#[toDateTime#[" + param1 + "#]#]";
                        break;
                    case "%Y-%m-01 00:00:00":
                        replaceValue = "toDateTime#[toStartOfMonth#[toDateTime#[" + param1 + "#]#]#]";
                        break;
                    case "%Y-01-01 00:00:00":
                        replaceValue = "toDateTime#[toStartOfYear#[toDateTime#[" + param1 + "#]#]#]";
                        break;
                    case "%Y-%m-%d":
                        replaceValue = "toStartOfDay#[toDateTime#[" + param1 + "#]#]";
                        break;
                    case "%Y-%m-01":
                        replaceValue = "toStartOfMonth#[toDateTime#[" + param1 + "#]#]";
                        break;
                    case "%Y-01-01":
                        replaceValue = "toStartOfYear#[toDateTime#[" + param1 + "#]#]";
                        break;
                    default:
                        replaceValue = "toDateTime#[" + param1 + "#]";
                }
            } else if (DBTypeEnum.DRUID.getValue().equals(dbType) && bool) {
                switch (param2) {
                    case "%Y-%m-%d %H:%i:%s":
                        replaceValue = "TIME_FORMAT#[ TIMESTAMP " + param1 + ",'YYYY-MM-dd HH:mm:ss'#]";
                        break;
                    case "%Y-%m-%d %H:%i:00":
                        replaceValue = "TIME_FORMAT#[ TIMESTAMP " + param1 + ",'YYYY-MM-dd HH:mm:00'#]";
                        break;
                    case "%Y-%m-%d %H:00:00":
                        replaceValue = "TIME_FORMAT#[ TIMESTAMP " + param1 + ",'YYYY-MM-dd HH:00:00'#]";
                        break;
                    case "%Y-%m-%d 00:00:00":
                        replaceValue = "TIME_FORMAT#[ TIMESTAMP " + param1 + ",'YYYY-MM-dd 00:00:00'#]";
                        break;
                    case "%Y-%m-01 00:00:00":
                        replaceValue = "TIME_FORMAT#[ TIMESTAMP " + param1 + ",'YYYY-MM-01 00:00:00'#]";
                        break;
                    case "%Y-01-01 00:00:00":
                        replaceValue = "TIME_FORMAT#[ TIMESTAMP " + param1 + ",'YYYY-01-01 00:00:00'#]";
                        break;
                    case "%Y-%m-%d":
                        replaceValue = "TIME_FORMAT#[ TIMESTAMP " + param1 + ",'YYYY-MM-dd'#]";
                        break;
                    case "%Y-%m-01":
                        replaceValue = "TIME_FORMAT#[ TIMESTAMP " + param1 + ",'YYYY-MM-01'#]";
                        break;
                    case "%Y-01-01":
                        replaceValue = "TIME_FORMAT#[ TIMESTAMP " + param1 + ",'YYYY-01-01'#]";
                        break;
                    default:
                        replaceValue = "TIME_FORMAT#[ TIMESTAMP " + param1 + ",'YYYY-MM-dd HH:mm:ss'#]";
                }
            } else if (DBTypeEnum.DRUID.getValue().equals(dbType) && !bool) {
                switch (param2) {
                    case "%Y-%m-%d %H:%i:%s":
                        replaceValue = " TIMESTAMP " + param1;
                        break;
                    case "%Y-%m-%d %H:%i:00":
                        replaceValue = "FLOOR#[ TIMESTAMP " + param1 + " to minute#]";
                        break;
                    case "%Y-%m-%d %H:00:00":
                        replaceValue = "FLOOR#[ TIMESTAMP " + param1 + " to hour#]";
                        break;
                    case "%Y-%m-%d 00:00:00":
                        replaceValue = "FLOOR#[ TIMESTAMP " + param1 + " to day#]";
                        break;
                    case "%Y-%m-01 00:00:00":
                        replaceValue = "FLOOR#[ TIMESTAMP " + param1 + " to month#]";
                        break;
                    case "%Y-01-01 00:00:00":
                        replaceValue = "FLOOR#[ TIMESTAMP " + param1 + " to year#]";
                        break;
                    case "%Y-%m-%d":
                        replaceValue = "TIME_FLOOR#[ TIMESTAMP " + param1 + ",'P1D'#]";
                        break;
                    case "%Y-%m-01":
                        replaceValue = "TIME_FLOOR#[ TIMESTAMP " + param1 + ",'P1M'#]";
                        break;
                    case "%Y-01-01":
                        replaceValue = "TIME_FLOOR#[ TIMESTAMP " + param1 + ",'P1Y'#]";
                        break;
                    default:
                        replaceValue = " TIMESTAMP " + param1;
                        break;
                }
            }
        }
        return replaceValue;
    }

    /**
     * 解析之后的sql处理
     *
     * @param sql
     * @param dbType
     * @return
     */
    public static String specialDisposeEnd(String sql, String dbType) {
        if (sql == null) {
            return sql;
        }
        StringBuffer sb = new StringBuffer();
        if (DBTypeEnum.DRUID.getValue().equals(dbType)) {

            Matcher mNowFun = pNow.matcher(sql);
            while (mNowFun.find()) {
                String innerValue = "CURRENT_TIMESTAMP";
                mNowFun.appendReplacement(sb, innerValue);
            }
            mNowFun.appendTail(sb);
            sql = sb.toString();

            Matcher matcher = pCKTimeStamp.matcher(sql);
            sb = new StringBuffer();
            while (matcher.find()) {
                if (matcher.groupCount() == 1) {
                    matcher.appendReplacement(sb, " " + matcher.group(1));
                }
            }
            matcher.appendTail(sb);
            sql = sb.toString();
        }
        return sql;
    }

    /**
     * 替换解析时候加入的[、]、#
     *
     * @param sql
     * @return
     */
    public static String replaceMark(String sql) {
        sql = sql.replace("#[", "(");
        sql = sql.replace("#]", ")");
        return sql;
    }

    /**
     * 替换：FROM_UNIXTIME(unix_timestamp)、FROM_UNIXTIME(date,format)、DATE_FORMAT(date,format)、STR_TO_DATE(date,format)
     *
     * @param sqlParse
     * @param num      包含括号的个数
     * @param bool     应用的分组中为true 条件范围中则为false
     * @param dbType
     * @return
     */
    public static String dateFormatdateFormatReplace(String sqlParse, int num, boolean bool, String dbType) {
        Pattern pDateFormatMark = Pattern.compile("(FROM_UNIXTIME|DATE_FORMAT|STR_TO_DATE)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)", Pattern.CASE_INSENSITIVE);
        Matcher mDateFormatMark = pDateFormatMark.matcher(sqlParse);
        StringBuffer sb = new StringBuffer();
        if (mDateFormatMark.find()) {
            String group2 = mDateFormatMark.group(2);
            String replaceValue = group2;
            if (StringUtil.getBracketsMatch(group2, "(", false)[0] >= 0) {
                replaceValue = dateFormatdateFormatReplaceByDB(mDateFormatMark.group(1), group2, bool, dbType);
            }
            mDateFormatMark.appendReplacement(sb, replaceValue);
        }
        mDateFormatMark.appendTail(sb);
        return sb.toString();
    }

    /**
     * 替换SUBDATE(expr,days)、SUBDATE(expr,days)、SUBDATE(date,INTERVAL expr dbType)、ADDDATE(date,INTERVAL expr dbType)、DATE_SUB(date,INTERVAL expr dbType)、DATE_ADD(date,INTERVAL expr dbType)
     *
     * @param sqlParse
     * @param num      包含的括号个数
     * @param dbType
     * @return
     */
    public static String dateReplace(String sqlParse, int num, String dbType) {
        Pattern pDateRelativeParse = Pattern.compile("(ADDDATE|DATE_ADD|SUBDATE|DATE_SUB)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcherParse = pDateRelativeParse.matcher(sqlParse);
        String innerValue = "";
        StringBuffer sb = new StringBuffer();
        if (matcherParse.find()) {
            Boolean sign = StringUtils.upperCase(matcherParse.group(1)).contains("ADD");
            if (StringUtil.getBracketsMatch(matcherParse.group(2), null, false)[0] >= 0) {
                List<String> params = diviParam(matcherParse.group(2), ",");
                innerValue = dateReplaceByDb(params, sign, dbType);
            }
            matcherParse.appendReplacement(sb, innerValue);
        }
        matcherParse.appendTail(sb);
        return sb.toString();
    }

    /**
     * 实现替换：UNIX_TIMESTAMP()、UNIX_TIMESTAMP(expr)
     *
     * @param sqlParse
     * @param num      包含的括号个数
     * @param dbType
     * @return
     */
    public static String parseUnixTimeReplaceByDB(String sqlParse, int num, String dbType) {
        Pattern pUnixTimeParse = Pattern.compile("(UNIX_TIMESTAMP)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcherParse = pUnixTimeParse.matcher(sqlParse);
        StringBuffer sb = new StringBuffer();
        String innerValue = null;
        if (matcherParse.find()) {
            if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
                if (matcherParse.group(2) == null || "".equals(matcherParse.group(2).trim())) {
                    innerValue = "toUnixTimestamp#[now#[#]#]";
                } else {
                    innerValue = "toUnixTimestamp#[parseDateTimeBestEffort#[toString#[ " + matcherParse.group(2) + "#]#]#]";
                }
            } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
                if (matcherParse.group(2) == null || "".equals(matcherParse.group(2).trim())) {
                    innerValue = "0.001 * TIMESTAMP_TO_MILLIS#[CURRENT_TIMESTAMP#]";
                } else {
                    if (matcherParse.group(2).startsWith("'") && matcherParse.group(2).endsWith("'")) {
                        innerValue = "TIMESTAMP_TO_MILLIS#[ TIME_PARSE#[TIME_FORMAT#[ TIMESTAMP " + matcherParse.group(2) + ", 'yyyy-MM-dd HH:mm:ss'#]#]#]/1000";
                    } else {
                        innerValue = "0.001 * TIMESTAMP_TO_MILLIS#[ TIMESTAMP " + matcherParse.group(2) + "#]";
                    }
                }
            }
            matcherParse.appendReplacement(sb, innerValue);
        }
        matcherParse.appendTail(sb);
        return sb.toString();
    }

    /**
     * 实现替换：DATE(expr)
     *
     * @param sqlParse
     * @param num      包含的括号个数
     * @param dbType
     * @return
     */
    public static String parseDateReplaceByDB(String sqlParse, int num, String dbType) {
        Pattern pDateParse = Pattern.compile("(DATE)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcherParse = pDateParse.matcher(sqlParse);
        StringBuffer sb = new StringBuffer();
        String innerValue = null;
        if (matcherParse.find()) {
            if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
                innerValue = "toStartOfDay#[parseDateTimeBestEffort#[toString#[" + matcherParse.group(2) + "#]#]#]";
            } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
                innerValue = "TIME_FLOOR#[ TIMESTAMP " + matcherParse.group(2) + ",'P1D'#]";
            }
            matcherParse.appendReplacement(sb, innerValue);
        }
        matcherParse.appendTail(sb);
        return sb.toString();
    }

    /**
     * 获取函数参数
     *
     * @param str
     * @param div 参数分隔符
     * @return
     */
    public static List<String> diviParam(String str, String div) {
        if (str == null) {
            return null;
        }
        List<String> result = new ArrayList<>();
        String[] split = str.split(div);
        String resultTemp = "";
        for (int i = 0; i < split.length; i++) {
            resultTemp += split[i];
            if (StringUtil.getBracketsMatch(resultTemp, "(", true)[0] >= 0
                    && StringUtil.getBracketsMatch(resultTemp, "[", true)[0] >= 0) {
                result.add(resultTemp);
                resultTemp = "";
                continue;
            }
            resultTemp += div;
        }
        return result;
    }


    /**
     * 公共，通过pattern匹配转义
     *
     * @param sql
     * @param dbType
     * @param pattern
     * @return
     */
    private static String translateByPattern(String sql, String dbType, Pattern pattern) {
        while (true) {
            Matcher matcher = pattern.matcher(sql);
            if (!matcher.find()) {
                return sql;
            }
            String sqlParse = sql.substring(matcher.start());
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);

            if (bracketsMatch[0] >= 1) {
                int start = StringUtils.indexOf(sqlParse, "(");
                Function function = new Function();
                function.setName(sqlParse.substring(0, start));
                int end = bracketsMatch[1];
                List<String> strings = Splitter.on(",").trimResults().omitEmptyStrings().splitToList(sqlParse.substring(start + 1, end));
                StringBuffer sb = new StringBuffer();
                for (String str : strings) {
                    sb.append(str);
                    int[] a = StringUtil.getBracketsMatch(sb.toString(), "(", true);
                    if (a[0] != -1) {
                        function.getParamsList().add(sb.toString());
                        sb = new StringBuffer();
                        continue;
                    } else {
                        sb.append(",");
                    }
                }

                String replace = translateFunction(function, dbType);
                sql = sql.substring(0, matcher.start()) + replace + sql.substring(matcher.start() + 1 + end);
            }
        }
    }

    /**
     * 转义函数
     *
     * @param fun
     * @param dbType
     * @return
     */
    private static String translateFunction(Function fun, String dbType) {
        if (CONCAT.equalsIgnoreCase(fun.getName())) {
            return translateConcat(fun, dbType);
        }
        return translateCaseInsensitive(fun, dbType);
    }

    private static String translateCaseInsensitive(Function fun, String dbType) {
        String data;
        switch (fun.getName()){
            case TIME_FLOOR_WITH_FILL:
                data = translateTimeFloorWithFill(fun, dbType);
                break;
            case MAX_DURATION:
                data = translateDuration(fun, dbType);
                break;
            case NOT_EMPTY:
                data = translateNotEmpty(fun, dbType);
                break;
            case EMPTY:
                data = translateEmpty(fun, dbType);
                break;
            case MEDIAN_HDR:
            case QUANTILE_HDR:
            case PERCENTILES_HDR:
            case APPROX_COUNT_DISTINCT_HLLD:
                data = translateHdrHistogram(fun, dbType);
                break;
            case COUNT_DISTINCT:
                data = translateCountDistinct(fun, dbType);
                break;
            case IP_TO_GEO:
            case IP_TO_CITY:
            case IP_TO_COUNTRY:
            case IP_TO_ISP:
            case IP_TO_ASN:
            case IP_TO_ASN_DETAIL:
            case IP_TO_ASN_ORG:
                data = translateIpLocation(fun);
                break;
            default:
                data = parseFunDefaultValue(fun);
        }
        return data;
    }
    /**
     * Desc: 转义notEmpty
     *
     * @param fun
     * @param dbType
     * @return {@link String}
     * @created by wWei
     * @date 2022/3/21 4:31 下午
     */
    private static String translateNotEmpty(Function fun, String dbType) {
        String data;
        if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
            String expr = fun.getParamsList().get(0);
            data = expr + " IS NOT NUll ";
        } else {
            data = parseFunDefaultValue(fun);
        }
        return data;
    }

    private static String parseFunDefaultValue(Function fun) {
        return String.format("%s#[%s#]",
                fun.getName(),
                StringUtils.join(fun.getParamsList().toArray(), ","));
    }

    /**
     * Desc: 转义empty
     *
     * @param fun
     * @param dbType
     * @return {@link String}
     * @created by wWei
     * @date 2022/3/21 4:31 下午
     */
    private static String translateEmpty(Function fun, String dbType) {
        String data;
        if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
            String expr = fun.getParamsList().get(0);
            data = expr + " IS NUll ";
        } else {
            data = parseFunDefaultValue(fun);
        }
        return data;
    }

    /**
     * 转义IP_TO_GEO | IP_TO_CITY | IP_TO_COUNTRY | IP_TO_ISP | IP_TO_ASN | IP_TO_ASN_DETAIL | IP_TO_ASN_ORG
     *
     * @param fun
     * @return
     */
    private static String translateIpLocation(Function fun) {
        return fun.paramsList.get(0);
    }


    /**
     * 转义time_floor_with_fill
     *
     * @param function
     * @param dbType
     * @return
     */
    private static String translateTimeFloorWithFill(Function function, String dbType) {
        String timestamp = function.getParamsList().get(0);
        String period = function.getParamsList().get(1);

        Matcher matcherPT = periodOfPT.matcher(period);
        if (matcherPT.find()) {
            String num = matcherPT.group(1);
            String unit = matcherPT.group(2);
            if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
                String format = null;
                if ("S".equalsIgnoreCase(unit)) {
                    format = "toUnixTimestamp(toDateTime(toStartOfInterval(toDateTime(%s),INTERVAL %s SECOND)))";
                } else if ("M".equalsIgnoreCase(unit)) {
                    format = "toUnixTimestamp(toDateTime(toStartOfInterval(toDateTime(%s),INTERVAL %s MINUTE)))";
                } else if ("H".equalsIgnoreCase(unit)) {
                    format = "toUnixTimestamp(toDateTime(toStartOfInterval(toDateTime(%s),INTERVAL %s HOUR)))";
                }
                return String.format(format, timestamp, num);
            } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
                String format = "TIMESTAMP_TO_MILLIS(time_floor(MILLIS_TO_TIMESTAMP(%s * 1000),%s))/1000";
                return String.format(format, timestamp, period);
            }
        }

        Matcher matcherP = periodOfP.matcher(period);
        if (matcherP.find()) {
            String num = matcherP.group(1);
            String unit = matcherP.group(2);
            if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
                String format = null;
                if ("D".equalsIgnoreCase(unit)) {
                    format = "toUnixTimestamp(toDateTime(toStartOfInterval(toDateTime(%s),INTERVAL %s DAY)))";
                } else if ("W".equalsIgnoreCase(unit)) {
                    format = "toUnixTimestamp(toDateTime(toStartOfInterval(toDateTime(%s),INTERVAL %s WEEK)))";
                } else if ("M".equalsIgnoreCase(unit)) {
                    format = "toUnixTimestamp(toDateTime(toStartOfInterval(toDateTime(%s),INTERVAL %s MONTH)))";
                } else if ("Y".equalsIgnoreCase(unit)) {
                    format = "toUnixTimestamp(toDateTime(toStartOfInterval(toDateTime(%s),INTERVAL %s YEAR)))";
                }
                return String.format(format, timestamp, num);
            } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
                String format = "TIMESTAMP_TO_MILLIS(time_floor(MILLIS_TO_TIMESTAMP(%s * 1000),%s))/1000";
                return String.format(format, timestamp, period);
            }
        }
        return function.toString();
    }

    /**
     * Desc: 转义MAX_DURATION
     *
     * @param fun
     * @param dbType
     * @return {@link String}
     * @created by wWei
     * @date 2022/3/7 5:47 下午
     */
    private static String translateDuration(Function fun, String dbType) {
        int factor = 60;
        String timestampExpr = fun.getParamsList().get(0);
        int interval = Integer.parseInt(fun.getParamsList().get(1));
        if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
            return String.format("length(arrayReduce('max', alphaTokens(replaceAll(replaceAll(toString(arrayMap(x-> x<(%s+%s), arrayDifference(arraySort(groupUniqArray(%s))))),'1','a'),',','') ) )) * %s "
                    , interval, factor, timestampExpr, interval);
        }
        return fun.toString();
    }

    /**
     * Desc: 转义自定义函数 MEDIAN_HDR/QUANTILE_HDR/PERCENTILES_HDR/APPROX_COUNT_DISTINCT_HLLD
     *
     * @param fun
     * @param dbType
     * @return
     */

    private static String translateHdrHistogram(Function fun, String dbType) {
        String hdrSketchExpr = fun.getParamsList().get(0);
        if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
            switch (fun.getName()) {
                case MEDIAN_HDR:
                    return String.format("APPROX_QUANTILE_HDR(%s,0.50)", hdrSketchExpr);
                case QUANTILE_HDR:
                    double probability = Double.parseDouble(fun.getParamsList().get(1));
                    return String.format("APPROX_QUANTILE_HDR(%s,%s)", hdrSketchExpr, probability);
                case PERCENTILES_HDR:
                    return String.format("HDR_GET_PERCENTILES(HDR_HISTOGRAM(%s), 2)", hdrSketchExpr);
                case APPROX_COUNT_DISTINCT_HLLD:
                    return String.format("APPROX_COUNT_DISTINCT_HLLD#[%s#]", hdrSketchExpr);
            }
        }
        return fun.toString();
    }

    /**
     * Desc 转义 COUNT_DISTINCT
     * @param fun
     * @param dbType
     * @return
     */
    private static String translateCountDistinct(Function fun, String dbType) {
        String field = fun.getParamsList().get(0);
        if (DBTypeEnum.DRUID.getValue().equals(dbType) || DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)){
            return String.format("count(distinct %s)", field);
        }
        return fun.toString();
    }

    /**
     * Desc: 转义concat
     *
     * @param function
     * @param dbType
     * @return
     */
    private static String translateConcat(Function function, String dbType) {
        StringBuffer sb = new StringBuffer();
        if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
            for (String param : function.getParamsList()) {
                sb.append("toString("+param+"), ");
            }
            return String.format("concat(%s'')", sb);
        }
        return parseFunDefaultValue(function);
    }

    @Data
    static class Function {
        private String name;
        private List<String> paramsList = new ArrayList<>();
    }

    @Data
    static class DruidQueryParam {
        private String query;
        private Map<String, String> context = Maps.newHashMap();
        private String resultFormat;

    }
}

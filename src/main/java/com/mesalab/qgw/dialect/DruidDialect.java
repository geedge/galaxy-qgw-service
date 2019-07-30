package com.mesalab.qgw.dialect;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.common.utils.SQLHelper;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.DruidIoHttpSource;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.select.Join;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class DruidDialect implements  Dialect,SQLDateFunction {

    public static Pattern pTimeStamp = Pattern.compile("\\s+TIMESTAMP\\s+([^'])", Pattern.CASE_INSENSITIVE);
    private DruidIoHttpSource druidIoHttpSource = (DruidIoHttpSource) SpringContextUtil.getBean("druidIoHttpSource");
    private HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");


    private ApiParam param;

    private int pageOffset;

    public DruidDialect(ApiParam param) {
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
        stringBuilder.append(limit);
        return stringBuilder.toString();
    }


    private String generatePageSql(String sql, SQLQuerySource sqlQuerySource) {
        if (StringUtil.isBlank(sqlQuerySource.getLimit())) {
            return getLimitString(sql, 0, 200);
        } else {

            if (sqlQuerySource.getLimit().split(",").length >= 2 ) {
                String[] limits = sqlQuerySource.getLimit().trim().split(",");
                int offset = Integer.valueOf(limits[0].trim());
                int rowNum = Integer.valueOf(limits[1].trim());
                pageOffset = offset;
               return  SQLHelper.removeLimit(sql) + " limit " + (offset + rowNum);
            }
            return sql;
        }

    }

    @Override
    public Dialect sqlDialectTransform() {
        param.setQuery(generatePageSql(param.getQuery(), param.getSqlQuerySource()));
        param.setQuery(generateDateFunction(param.getQuery()));
        return this;
    }

    @Override
    public BaseResult executeHttpQuery() {
        BaseResult baseResult = null;

        StringBuilder urlBuilder = new StringBuilder("http://") .append(druidIoHttpSource.getUrl());
        DruidDialect.DruidQueryParam druidQueryParam = new DruidDialect.DruidQueryParam();

        druidQueryParam.setQuery(param.getQuery());
        druidQueryParam.getContext().put("sqlTimeZone", druidIoHttpSource.getSqlTimeZone());
        druidQueryParam.getContext().put("skipEmptyBuckets", druidIoHttpSource.getSkipEmptyBuckets());

        log.info("TSDB Query ：{}, SQL: {}", urlBuilder.toString() , param.getQuery());

        if (param.getFormat().equals(QueryFormatEnum.JSON.getValue())) {
            druidQueryParam.setResultFormat("object");
        } else if (param.getFormat().equals(QueryFormatEnum.CSV.getValue())) {
            druidQueryParam.setResultFormat("csv");
        }

        long beginTime = System.currentTimeMillis();
        Map<String, String> resultMap = httpClientService.httpPost(urlBuilder.toString(), JsonMapper.toJsonString(druidQueryParam));
        double executeTime = (System.currentTimeMillis()-beginTime) / 1000.0;

        if (StringUtil.isEmpty(resultMap)) {
            baseResult =  BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, "服务繁忙，请联系数据平台方！");
        } else {
            if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {

                if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.CSV.getValue())) {
                    String result = resultMap.get("result");

                    String[] results = result.split("\n");

                    if (pageOffset > 0 && StringUtil.isNotEmpty(results)) {
                        if (pageOffset >= results.length) {
                            result = "";
                        } else {
                           String[] subResult = ArrayUtils.subarray(results, pageOffset, results.length);
                            result = Joiner.on("\n").join(subResult).toString();


                        }
                    }


                    baseResult = BaseResultGenerator.success("ok", result , null);


                } else if (param.getFormat().equalsIgnoreCase(QueryFormatEnum.JSON.getValue())) {
                    List<Object> allResults = (List<Object>) JsonMapper.fromJsonString(resultMap.get("result"), Object.class);
                    if (pageOffset > 0 && StringUtil.isNotEmpty(allResults)) {
                        if (pageOffset >= allResults.size()) {
                            allResults = new ArrayList<>();
                        } else {
                            allResults = allResults.subList(pageOffset, allResults.size());
                        }
                    }

                    Map<String, Object> statistics = Maps.newHashMap();
                    statistics.put("elapsed", executeTime);
                    statistics.put("rows_read", allResults.size());

                    int resultSize = StringUtil.isEmpty(allResults) ? 0 : JsonMapper.toJsonString(allResults).getBytes().length;
                    statistics.put("result_size", resultSize);

                    baseResult = BaseResultGenerator.success("ok", allResults , statistics);
                }



            } else if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_NOT_FOUND))) {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_NOT_FOUND,  JsonMapper.fromJsonString(resultMap.get("result"), Object.class).toString());
            } else if (resultMap.get("status").equals(String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR))) {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, JsonMapper.fromJsonString(resultMap.get("result"), Object.class).toString());
            } else {
                baseResult = BaseResultGenerator.failure(HttpStatus.SC_INTERNAL_SERVER_ERROR, JsonMapper.fromJsonString(resultMap.get("result"), Object.class).toString());
            }
        }

        return baseResult;
    }



    @Data
    class DruidQueryParam {
        private String query;
        private Map<String, String> context = Maps.newHashMap();
        private String resultFormat;

    }

    @Override
    public String convertValueExprFormat(String innerValue) {
        String[] split = StringUtil.split(innerValue, ",\\s*'%");
        if (split.length == 2) {
            String format = split[1].replaceAll("\\'|\\\"", "").trim();
            switch (format) {
                case "Y-%m-%d %H:%i:%s": innerValue = " TIMESTAMP " + split[0]; break;
                case "Y-%m-%d %H:%i:00": innerValue = "FLOOR#[ TIMESTAMP " + split[0] + " to minute]"; break;
                case "Y-%m-%d %H:00:00": innerValue = "FLOOR#[ TIMESTAMP " + split[0] + " to hour]"; break;
                case "Y-%m-%d 00:00:00": innerValue = "FLOOR#[ TIMESTAMP " + split[0] + " to day]"; break;
                case "Y-%m-01 00:00:00": innerValue = "FLOOR#[ TIMESTAMP " + split[0] + " to month]"; break;
                case "Y-01-01 00:00:00": innerValue = "FLOOR#[ TIMESTAMP " + split[0] + " to year]"; break;
                case "Y-%m-%d": innerValue = "TIME_FLOOR#[ TIMESTAMP " + split[0] + ",'P1D']"; break;
                case "Y-%m-01": innerValue = "TIME_FLOOR#[ TIMESTAMP " + split[0] + ",'P1M']"; break;
                case "Y-01-01": innerValue = "TIME_FLOOR#[ TIMESTAMP " + split[0] + ",'P1Y']"; break;
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
    @Override
    public String convertValueDateAdd(String date, String expr, boolean shift) {
        expr = expr.trim().toUpperCase();
        Matcher matcher = SQLHelper.pDateUnit.matcher(expr);
        if (matcher.find()) {
            if (matcher.groupCount() == 2) {
                String numExpr = matcher.group(1).trim();
                Matcher mNumExpr = SQLHelper.pNum.matcher(numExpr);
                if (mNumExpr.matches()) {
                    numExpr = numExpr.replaceAll("\\s*", "");
                    int exprNum = Integer.parseInt(numExpr);
                    if (!shift) {
                        numExpr = String.valueOf(-exprNum);
                    }
                }
                switch (matcher.group(2)){
                    case "SECOND": date = "TIME_SHIFT#[ TIMESTAMP " + date + ",'PT1S',"+ numExpr +"]"; break;
                    case "MINUTE": date = "TIME_SHIFT#[ TIMESTAMP " + date + ",'PT1M',"+ numExpr +"]"; break;
                    case "HOUR": date = "TIME_SHIFT#[ TIMESTAMP " + date + ",'PT1H',"+ numExpr +"]"; break;
                    case "DAY": date = "TIME_SHIFT#[ TIMESTAMP " + date + ",'P1D',"+ numExpr +"]"; break;
                    case "WEEK": date = "TIME_SHIFT#[ TIMESTAMP " + date + ",'P1W',"+ numExpr +"]"; break;
                    case "MONTH": date = "TIME_SHIFT#[ TIMESTAMP " + date + ",'P1M',"+ numExpr +"]"; break;
                    case "QUARTER": date = "TIME_SHIFT#[ TIMESTAMP " + date + ",'P1M',"+ numExpr +"]"; break;
                    case "YEAR": date = "TIME_SHIFT#[ TIMESTAMP " + date + ",'P1Y',"+ numExpr +"]"; break;
                    default:date = date + expr; break;
                }
            }
        }
        return date;
    }

    @Override
    public String specialDisposeEnd(String sql) {
        if (sql == null) {
            return sql;
        }
        StringBuffer sb = new StringBuffer();
        Matcher matcher = pTimeStamp.matcher(sql);
        while (matcher.find()){
            if(matcher.groupCount() == 1){
                matcher.appendReplacement(sb, matcher.group(1));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @Override
    public StringBuffer replaceGranularityTime(StringBuffer sb) {
        Matcher mGranularity = SQLHelper.pDateGranularity.matcher(sb.toString());
        sb = new StringBuffer();
        while (mGranularity.find()) {
            String replaceValue = "";
            if (mGranularity.groupCount() == 4) {
                String expr = mGranularity.group(1) != null ? mGranularity.group(1).toUpperCase() : mGranularity.group(3).toUpperCase();
                String param = mGranularity.group(2) != null ? mGranularity.group(2) : mGranularity.group(4);
                if (expr.contains("SECOND")) {
                    replaceValue = "TIME_FLOOR#[ TIMESTAMP "+ param +",'PT30S']";
                } else if (expr.contains("FLOOR")) {
                    replaceValue = "TIME_FLOOR#[ TIMESTAMP "+ param +",'PT5M']";
                } else if (expr.contains("MINUTE")) {
                    replaceValue = "TIME_FLOOR#[ TIMESTAMP "+ param +",'PT1M']";
                } else if (expr.contains("HOUR")) {
                    replaceValue = "TIME_FLOOR#[ TIMESTAMP "+ param +",'PT1H']";
                } else {
                    replaceValue = mGranularity.group(0);
                }
            }
            mGranularity.appendReplacement(sb, replaceValue);
        }
        mGranularity.appendTail(sb);
        return sb;
    }

    @Override
    public StringBuffer replaceGranularityQuarter(StringBuffer sb) {
        Matcher mQuarter = SQLHelper.pConcatQuarter.matcher(sb.toString());
        sb = new StringBuffer();
        while (mQuarter.find()) {
            String replaceValue = "";
            if (mQuarter.groupCount() == 1) {
                replaceValue = "FLOOR#[ TIMESTAMP " + mQuarter.group(1) + " to Quarter]";
            }
            mQuarter.appendReplacement(sb, replaceValue);
        }
        mQuarter.appendTail(sb);
        return sb;
    }

    @Override
    public StringBuffer replaceGranularityWeek(StringBuffer sb) {
        String regexp = "\\'|\\\"|\\s*";
        Matcher mThisWeek = SQLHelper.pWeek.matcher(sb);
        sb = new StringBuffer();
        while (mThisWeek.find()) {
            String replaceValue = "";
            if (mThisWeek.groupCount() == 3) {
                switch (mThisWeek.group(2).replaceAll(regexp, "")) {
                    case "%X%VSunday": replaceValue = "TIME_SHIFT#[FLOOR#[ TIMESTAMP " + mThisWeek.group(1) + " to Week],'P1D',-1]"; break;
                    default : replaceValue=mThisWeek.group(0); break;
                }
            }
            mThisWeek.appendReplacement(sb, replaceValue);
        }
        mThisWeek.appendTail(sb);
        return sb;
    }

    @Override
    public StringBuffer replaceAboutGetOnlyNum(StringBuffer sqlBuffer) {
        StringBuffer sb = new StringBuffer();
        String replaceValue = "";
        Matcher mYear = SQLHelper.pYear.matcher(sqlBuffer);
        while (mYear.find()) {
            if (mYear.groupCount() == 6) {
                if (mYear.group(5) != null) {
                    replaceValue = "TIME_FLOOR#[ TIMESTAMP " + mYear.group(5).trim() + ",'P1D']";
                }
                mYear.appendReplacement(sb, replaceValue);
            }
        }
        mYear.appendTail(sb);
        return sb;
    }

    @Override
    public StringBuffer replaceAboutUnixTime(StringBuffer sqlBuffer) {
        int indexFromUnix = sqlBuffer.indexOf("FROM_UNIXTIME");
        String str = null;
        while (indexFromUnix > -1) {
            int count = 0;
            String substring = sqlBuffer.substring(indexFromUnix);
            int i = substring.indexOf("]");
            substring = substring.substring(0,i);
            for (int a = 0 ; a < substring.length() ; a++){
                if(substring.indexOf("[") == a){
                    substring = substring.substring(a+1);
                    count++;
                }
            }
            --count;
            Pattern pFromUnixTime = Pattern.compile("FROM_UNIXTIME\\s*\\[((.*?\\].*?){" + count + "}.*?)\\]", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pFromUnixTime.matcher(sqlBuffer);
            sqlBuffer = new StringBuffer();
            if (matcher.find()){
                if(matcher.groupCount() == 2){
                    str = "MILLIS_TO_TIMESTAMP#[ 1000 * [ "+ matcher.group(1) + "]]";
                }
                matcher.appendReplacement(sqlBuffer, str);
            }
            matcher.appendTail(sqlBuffer);
            indexFromUnix = sqlBuffer.indexOf("FROM_UNIXTIME");
        }

        Matcher mUnixTimeStamp = SQLHelper.pUnixTime.matcher(sqlBuffer);
        str = null;
        sqlBuffer = new StringBuffer();
        while (mUnixTimeStamp.find()) {
            if (mUnixTimeStamp.group(1) != null) {
                str = "0.001 * TIMESTAMP_TO_MILLIS#[ TIMESTAMP ";
            }
            mUnixTimeStamp.appendReplacement(sqlBuffer, str);
        }
        mUnixTimeStamp.appendTail(sqlBuffer);
        return sqlBuffer;
    }

    @Override
    public String convertValueAddDate(String expr, String date, boolean shift) {
        date = date.replaceAll("\\s*", "");
        int num = Integer.parseInt(date);
        if (shift) {
            expr = "TIME_SHIFT#[ TIMESTAMP " + expr + ",'P1D',"+ num +"]";
        } else {
            expr = "TIME_SHIFT#[ TIMESTAMP " + expr + ",'P1D',"+ (-num) +"]";
        }
        return expr;
    }

}

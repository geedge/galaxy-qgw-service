package com.mesalab.common.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author GouGe
 * @data 2019/8/1 10:13
 */
public class SQLDateFunctionUtil {
    public static Pattern pDateFormat = Pattern.compile("(FROM_UNIXTIME|DATE_FORMAT|STR_TO_DATE)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static Pattern pDateRelative = Pattern.compile("(ADDDATE|DATE_ADD|DATE_SUB|SUBDATE)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static Pattern pIntervalNumUnit = Pattern.compile("INTERVAL(.*?)(YEAR|QUARTER|MONTH|DAY|HOUR|MINUTE|SECOND)$", Pattern.CASE_INSENSITIVE);
    public static Pattern pUnixTime = Pattern.compile("UNIX_TIMESTAMP\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static Pattern pDate = Pattern.compile("(DATE)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static Pattern pDateCalculateNum = Pattern.compile("\\(\\s*(DATE)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static Pattern pUnitFuncGetNum = Pattern.compile("(YEAR|QUARTER|MONTH|DAY|HOUR|MINUTE|SECOND|DAYOFYEAR|DAYOFMONTH|DAYOFWEEK)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static Pattern pMarkDate = Pattern.compile("(MAKEDATE)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static Pattern pLastDateFunc = Pattern.compile("(LAST_DAY)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static Pattern pCKTimeStamp = Pattern.compile("\\s+TIMESTAMP\\s+([^'])", Pattern.CASE_INSENSITIVE);
    public static Pattern pNow = Pattern.compile("NOW\\(\\s*\\)", Pattern.CASE_INSENSITIVE);
    public static Pattern pStrDateTime = Pattern.compile("([<|>|=])\\s*('\\d{4}-\\d{2}-\\d{2}\\s*\\d{2}:\\d{2}:\\d{2}')", Pattern.CASE_INSENSITIVE);

    /**
     * 匹配：FROM_UNIXTIME(、DATE_FORMAT(、STR_TO_DATE(
     * @param sql 需要解析的sql
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String parseDateFormat(String sql, int type) {
        int count = 0;
        while (true) {
            Matcher matcher = pDateFormat.matcher(sql);
            if (!matcher.find() || count++ >= 15) {
                return sql;
            }
            String sqlParse = sql.substring(matcher.start());
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = dateFormatHavaMark(sqlParse, bracketsMatch[0], type);
            sql = sql.substring(0, matcher.start()) + str;
        }
    }

    /**
     * 替换SUBDATE(expr,days)、SUBDATE(expr,days)、SUBDATE(date,INTERVAL expr type)、ADDDATE(date,INTERVAL expr type)、DATE_SUB(date,INTERVAL expr type)、DATE_ADD(date,INTERVAL expr type)
     * @param sql
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String parseDateRelative(String sql, int type) {
        int count = 0;
        while (true) {
            Matcher matcher = pDateRelative.matcher(sql);
            if (!matcher.find() || count++ >= 15) {
                return sql;
            }
            String sqlParse = sql.substring(matcher.start());
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, null, false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = dateRelativeHaveMark(sqlParse, bracketsMatch[0], type);
            sql = sql.substring(0, matcher.start()) + str;
        }
    }

    /**
     * 匹配 UNIX_TIMESTAMP(
     * @param sql
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String parseUnixTime(String sql, int type) {
        int count = 0;
        while (true) {
            Matcher matcher = pUnixTime.matcher(sql);
            if (!matcher.find() || count++ == 15) {
                return sql;
            }
            String sqlParse = sql.substring(matcher.start());
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, null, false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = parseUnixTimeHavaMark(sqlParse, bracketsMatch[0], type);
            sql = sql.substring(0, matcher.start()) + str;
        }
    }

    /**
     * 匹配 DATE(
     * @param sql
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String parseDate(String sql, int type) {
        int count = 0;
        while (true) {
            Matcher matcher = pDate.matcher(sql);
            if (!matcher.find() || count++ >= 15) {
                return sql;
            }
            String sqlParse = sql.substring(matcher.start());
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = parseDateHavaMark(sqlParse, bracketsMatch[0], type);
            sql = sql.substring(0, matcher.start()) + str;
        }
    }

    /**
     * 匹配 YEAR(、QUARTER(、MONTH(、DAY(、HOUR(、MINUTE(、SECOND、DAYOFYEAR(、DAYOFMONTH(、DAYOFWEEK(
     * @param sql
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String parseUnitFuncGetNum(String sql, int type){
        int count = 0;
        while (true){
            Matcher matcher = pUnitFuncGetNum.matcher(sql);
            if(!matcher.find() || count++ >= 15){
                return sql;
            }
            String sqlParse = sql.substring(matcher.start());
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = parseUnitFuncGetNumHaveMark(sqlParse, bracketsMatch[0], type);
            sql = sql.substring(0, matcher.start()) + str;
        }
    }

    /**
     * 匹配 pMarkDate(
     * @param sql
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String parseMarkDate(String sql, int type) {
        int count = 0;
        while (true) {
            Matcher matcher = pMarkDate.matcher(sql);
            if (!matcher.find() || count++ >= 15) {
                return sql;
            }
            String sqlParse = sql.substring(matcher.start());
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = parseMarkDateHaveMark(sqlParse, bracketsMatch[0], type);
            sql = sql.substring(0, matcher.start()) + str;
        }
    }

    /**
     * 匹配 LAST_DAY(
     * @param sql
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String parseLastDayFunc(String sql, int type){
        int count = 0;
        while (true) {
            Matcher matcher = pLastDateFunc.matcher(sql);
            if (!matcher.find() || count++ >= 15) {
                return sql;
            }
            String sqlParse = sql.substring(matcher.start());
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = parseLastDayFuncHaveMark(sqlParse, bracketsMatch[0], type);
            sql = sql.substring(0, matcher.start()) + str;
        }
    }

    /**
     * 涉及DATE()-、DATE()+
     * @param sql
     * @param type
     * @return
     */
    public static String parseDateCalculateNum(String sql, int type){
        int count = 0;
        while (true) {
            Matcher matcher = pDateCalculateNum.matcher(sql);
            if (!matcher.find() || count++ >= 15) {
                return sql;
            }
            String sqlParse = sql.substring(matcher.start());
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String sqlParse2 = sql.substring(matcher.start()+1);
            int[] bracketsMatch2 = StringUtil.getBracketsMatch(sqlParse2, "(", false);
            if (bracketsMatch2[0] >= 1) {
                --bracketsMatch2[0];
            }
            String str = parseDateCalculateNumHaveMark(sqlParse, bracketsMatch[0],bracketsMatch2[0], type);
            sql = sql.substring(0, matcher.start()) + str;
        }
    }

    /**
     * 涉及DATE()-、DATE()+
     * @param sqlParse
     * @param num
     * @param num2
     * @param type
     * @return
     */
    private static String parseDateCalculateNumHaveMark(String sqlParse, int num, int num2, int type) {
        Pattern pDateCalculateNumParse = Pattern.compile("(DATE)\\s*\\((.*?(.*?\\).*?){" + num2 + "})\\)\\s*(([+|-]).*?(.*?\\).*?){" + (num - num2 - 1) + "})\\)", Pattern.CASE_INSENSITIVE);
        StringBuffer sb = new StringBuffer();
        Matcher mDateCalculateNumParse = pDateCalculateNumParse.matcher(sqlParse);
        if (mDateCalculateNumParse.find()) {
            String group0 = mDateCalculateNumParse.group(0);
            String group2 = mDateCalculateNumParse.group(2);
            String group4 = mDateCalculateNumParse.group(4);
            if (StringUtil.getBracketsMatch(group0.substring(0, group0.length() - 1), "(", true)[0] == num) {
                String[] split = group4.split(",");
                String replaceValue = "";
                if (split.length == 1) {
                    replaceValue = "TIME_SHIFT#[FLOOR#[ TIMESTAMP " + group2 + " to day],'P1D'," + group4 + "])";
                } else if (split.length == 2) {
                    replaceValue = "TIME_SHIFT#[FLOOR#[ TIMESTAMP " + group2 + "to day],'P1D'," + split[0] + "]," + split[1] + ")";
                }
                mDateCalculateNumParse.appendReplacement(sb, replaceValue);
            }
        }
        mDateCalculateNumParse.appendTail(sb);
        return sb.toString();
    }


    /**
     * 替换 LAST_DAY(date)
     * @param sqlParse
     * @param num 包含括号个数
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String parseLastDayFuncHaveMark(String sqlParse, int num, int type) {
        Pattern pLastDayParse = Pattern.compile("(LAST_DAY)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)", Pattern.CASE_INSENSITIVE);
        StringBuffer sb = new StringBuffer();
        Matcher mLastDayParse = pLastDayParse.matcher(sqlParse);
        if (mLastDayParse.find()) {
            String replaceValue = lastDayRealize(mLastDayParse.group(1), mLastDayParse.group(2), type);
            mLastDayParse.appendReplacement(sb, replaceValue);
        }
        mLastDayParse.appendTail(sb);
        return sb.toString();
    }

    /**
     * 实现替换：LAST_DAY(date)
     * @param unit
     * @param param
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    private static String lastDayRealize(String unit, String param, int type) {
        String replaceValue = null;
        if("LAST_DAY".equals(unit.toUpperCase())){
            if(type ==  1){
                replaceValue = "addDays#[addMonths#[toStartOfMonth#[toDateTime#[" + param + "]],1],-1]";
            }else if(type == 2){
                replaceValue = "TIME_SHIFT#[FLOOR#[TIME_SHIFT#[ TIMESTAMP " + param + ", 'P1M', 1] to month], 'P1D', -1]";
            }
        }
        return replaceValue;
    }

    /**
     * 替换：MAKEDATE(year,dayofyear)
      * @param sqlParse
     * @param num 函数参数包含括号的个数
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String parseMarkDateHaveMark(String sqlParse, int num, int type) {
        Pattern pMarkDateParse = Pattern.compile("(MAKEDATE)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)", Pattern.CASE_INSENSITIVE);
        StringBuffer sb = new StringBuffer();
        Matcher mMarkDateParse = pMarkDateParse.matcher(sqlParse);
        if (mMarkDateParse.find()) {
            String replaceValue = markDateRealize(mMarkDateParse.group(1), mMarkDateParse.group(2), type);
            mMarkDateParse.appendReplacement(sb, replaceValue);
        }
        mMarkDateParse.appendTail(sb);
        return sb.toString();
    }

    /**
     * 实现替换：MAKEDATE(year,dayofyear)
     * @param func
     * @param expr
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    private static String markDateRealize(String func, String expr, int type) {
        String replaceValue = null;
        List<String> params = diviParam(expr, ",");
        if ("MAKEDATE".equals(func.toUpperCase()) && params.size() == 2) {
            Matcher matcher = pUnitFuncGetNum.matcher(expr);
            if (matcher.find() && "YEAR".equals(matcher.group(1).toUpperCase())) {
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
                    if (type == 1) {
                        if (group1 != null && !"".equals(group1.trim()) && group5 != null && !"".equals(group5.trim())) {
                            replaceValue = "toDateTime#[addDays#[addYears#[toStartOfYear#[toDateTime#[" + mYear.group(3) + "]]," + group1 + " " + group5 + "]," + params.get(1) + " -1 ]]";
                        } else if (group5 != null && !"".equals(group5.trim())) {
                            replaceValue = "toDateTime#[addDays#[addYears#[toStartOfYear#[toDateTime#[" + mYear.group(3) + "]]," + group5 + "]," + params.get(1) + " -1 ]]";
                        } else if (group1 != null && !"".equals(group1.trim())) {
                            replaceValue = "toDateTime#[addDays#[addYears#[toStartOfYear#[toDateTime#[" + mYear.group(3) + "]]," + group1 + "0 ]," + params.get(1) + " -1 ]]";
                        } else {
                            replaceValue = "toDateTime#[addDays#[toStartOfYear#[toDateTime#[" + mYear.group(3) + "]]," + params.get(1) + " -1 ]]";
                        }
                    } else if (type == 2) {
                        if (group1 != null && !"".equals(group1.trim()) && group5 != null && !"".equals(group5.trim())) {
                            replaceValue = "TIME_SHIFT#[TIME_SHIFT#[FLOOR#[ TIMESTAMP " + mYear.group(3) + " to year],'P1Y'," + group1 + " " + group5 + " ],'P1D'," + params.get(1) + " -1 ]";
                        } else if (group5 != null && !"".equals(group5.trim())) {
                            replaceValue = "TIME_SHIFT#[TIME_SHIFT#[FLOOR#[ TIMESTAMP " + mYear.group(3) + " to year],'P1Y'," + group5 + "],'P1D'," + params.get(1) + " -1 ]";
                        } else if (group1 != null && !"".equals(group1.trim())) {
                            replaceValue = "TIME_SHIFT#[TIME_SHIFT#[FLOOR#[ TIMESTAMP " + mYear.group(3) + " to year],'P1Y'," + group1 + "0 ],'P1D'," + params.get(1) + " -1 ]";
                        } else {
                            replaceValue = "TIME_SHIFT#[FLOOR#[ TIMESTAMP " + mYear.group(3) + " to year],'P1D'," + params.get(1) + "-1 ]";
                        }
                    }
                }
            }
        }
        return replaceValue;
    }

    /**
     * 替换 YEAR(expr)、QUARTER(expr)、MONTH(expr)、DAY(expr)、HOUR(expr)、MINUTE(expr)、SECONDexpr)、DAYOFYEAR(expr)、DAYOFMONTH(expr)、DAYOFWEEK(expr)
     * @param sqlParse
     * @param num
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    private static String parseUnitFuncGetNumHaveMark(String sqlParse, int num, int type) {
        Pattern pUnitFuncParse = Pattern.compile("(YEAR|QUARTER|MONTH|DAY|HOUR|MINUTE|SECOND|DAYOFYEAR|DAYOFMONTH|DAYOFWEEK)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)", Pattern.CASE_INSENSITIVE);
        StringBuffer sb = new StringBuffer();
        Matcher mUnitFuncParse = pUnitFuncParse.matcher(sqlParse);
        if (mUnitFuncParse.find()) {
            String replaceValue = unitFuncGetNumRealize(mUnitFuncParse.group(1), mUnitFuncParse.group(2), type);
            mUnitFuncParse.appendReplacement(sb, replaceValue);
        }
        mUnitFuncParse.appendTail(sb);
        return sb.toString();
    }

    /**
     * 实现替换：YEAR(expr)、QUARTER(expr)、MONTH(expr)、DAY(expr)、HOUR(expr)、MINUTE(expr)、SECONDexpr)、DAYOFYEAR(expr)、DAYOFMONTH(expr)、DAYOFWEEK(expr)
     * @param unit
     * @param expr
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    private static String unitFuncGetNumRealize(String unit,String expr, int type) {
        String replaceValue = null;
        unit = unit.toUpperCase();
        if(type == 1){
            switch (unit){
                case "YEAR": replaceValue = "toYear#[toDateTime#[" + expr + "]]"; break;
                case "QUARTER": replaceValue = "toQuarter#[toDateTime#[" + expr + "]]"; break;
                case "MONTH": replaceValue = "toMonth#[toDateTime#[" + expr + "]]"; break;
                case "DAY": replaceValue = "toDayOfMonth#[toDateTime#[" + expr + "]]"; break;
                case "HOUR": replaceValue = "toHour#[toDateTime#[" + expr + "]]"; break;
                case "MINUTE": replaceValue = "toMinute#[toDateTime#[" + expr + "]]"; break;
                case "SECOND": replaceValue = "toSecond#[toDateTime#[" + expr + "]]"; break;
                case "DAYOFYEAR": replaceValue = "toDayOfYear#[toDateTime#["+ expr + "]]";break;
                case "DAYOFMONTH": replaceValue = "toDayOfMonth#[toDateTime#["+ expr + "]]";break;
                case "DAYOFWEEK": replaceValue = "toDayOfWeek#[addDays#[toDateTime#["+ expr + "],1]]";break;
                default: replaceValue = unit+"#[" + expr + "]";
            }
        }else if(type == 2){
            switch (unit){
                case "YEAR": replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0], 'YEAR' ]"; break;
                case "QUARTER": replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0], 'QUARTER' ]"; break;
                case "MONTH": replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0], 'MONTH' ]"; break;
                case "DAY": replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0], 'DAY' ]"; break;
                case "HOUR": replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0], 'HOUR' ]"; break;
                case "MINUTE": replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0], 'MINUTE' ]"; break;
                case "SECOND": replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0], 'SECOND' ]"; break;
                case "DAYOFYEAR": replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0], 'DOY' ]"; break;
                case "DAYOFMONTH": replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',0], 'DAY' ]"; break;
                case "DAYOFWEEK": replaceValue = "TIME_EXTRACT#[TIME_SHIFT#[ TIMESTAMP " + expr + ",'P1D',1], 'DOW' ]"; break;
                default: replaceValue = unit+"#[" + expr + "]";
            }
        }
        return replaceValue;
    }

    /**
     * 实现相对时间相关替换：
     * @param params
     * @param sign ADD相关为true,SUB相关为false;
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    private static String dateRelative(List<String> params, Boolean sign, int type) {
        String replaceValue = "";
        if (params.size() == 2 && params.get(1).toUpperCase().contains("INTERVAL")) {
            String param1 = params.get(0);
            String param2 = params.get(1);
            Matcher matcher = pIntervalNumUnit.matcher(param2);
            if (matcher.find()) {
                param2 = matcher.group(1);
                if(!sign){
                    param2 = "-  #[" + param2 + "]";
                }
                if(type == 1){
                    switch (matcher.group(2)){
                        case "SECOND": replaceValue = "toDateTime#[" + param1 + "]" + param2; break;
                        case "MINUTE": replaceValue = "toDateTime#[" + param1 + "]" + param2 + "*60"; break;
                        case "HOUR": replaceValue = "toDateTime#[" + param1 + "]" + param2 + "*3600"; break;
                        case "DAY": replaceValue = "toDateTime#[" + param1 + "]" + param2+ "*86400"; break;
                        case "WEEK": replaceValue = "toDateTime#[" + param1 + "]" + param2 + "*604800"; break;
                        case "MONTH": replaceValue = "addMonths#[toDateTime#[" + param1 + "] ," + param2 + "]"; break;
                        case "QUARTER": replaceValue = "addQuarter#[toDateTime#[" + param1 + "]," + param2 + "]"; break;
                        case "YEAR": replaceValue = "addYears#[toDateTime#[" + param1 + "]," + param2 + "]"; break;
                        default:replaceValue = param1 + param2; break;
                    }
                }else if(type ==2){
                    switch (matcher.group(2)) {
                        case "SECOND": replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'PT1S',"+ param2 +"]"; break;
                        case "MINUTE": replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'PT1M',"+ param2 +"]"; break;
                        case "HOUR": replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'PT1H',"+ param2 +"]"; break;
                        case "DAY": replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'P1D',"+ param2 +"]"; break;
                        case "WEEK": replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'P1W',"+ param2 +"]"; break;
                        case "MONTH": replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'P1M',"+ param2 +"]"; break;
                        case "QUARTER": replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'P1M',"+ param2 +"]"; break;
                        case "YEAR": replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'P1Y',"+ param2 +"]"; break;
                        default:replaceValue = param1 + param2; break;
                    }
                }
                return replaceValue;
            }
        } else if (params.size() == 2) {
            String param1 = params.get(0);
            String param2 = params.get(1);
            if(!sign){
                param2 = "-  #[" + param2 + "]";
            }
            if(type == 1){
                replaceValue = "addDays#[toDateTime#[" + param1 + "]," + param2 + "]";
            }else if(type ==2){
                replaceValue = "TIME_SHIFT#[ TIMESTAMP " + param1 + ",'P1D'," + param2 + "]";
            }
        }
        return replaceValue;
    }

    /**
     * 实现替换：FROM_UNIXTIME(unix_timestamp)
     * @param func
     * @param param
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    private static String dateFormatRealize(String func,String param, int type) {
        List<String> params = diviParam(param, ",");
        String param1 = params.get(0);
        String replaceValue = null;
        if ("FROM_UNIXTIME".equals(func.toUpperCase()) && params.size() == 1) {
            if (type == 1) {
                replaceValue = "toDateTime#[" + param + "]";
            } else if (type == 2) {
                replaceValue = "MILLIS_TO_TIMESTAMP#[ 1000 * [" + param + "]]";
            }
        } else if (func.contains("FROM_UNIXTIME") && type == 2 && "%Y-%m-%d %H:%i:%s".equals(params.get(1).replaceAll("\\'|\\\"", "").trim())) {
            replaceValue = "MILLIS_TO_TIMESTAMP#[ 1000*[" + param1 + "]]";
        } else {
            String param2 = params.get(1).replaceAll("\\'|\\\"", "").trim();
            if (type == 1) {
                switch (param2) {
                    case "%Y-%m-%d %H:%i:%s": replaceValue = "toDateTime#[formatDateTime#[toDateTime#["+ param1 +"], '%Y-%m-%d %H:%M:%S' ]]"; break;
                    case "%Y-%m-%d %H:%i:00": replaceValue = "toDateTime#[formatDateTime#[toDateTime#["+ param1 +"], '%Y-%m-%d %H:%M:00' ]]"; break;
                    case "%Y-%m-%d %H:00:00": replaceValue = "toDateTime#[formatDateTime#[toDateTime#["+ param1 +"], '%Y-%m-%d %H:00:00' ]]"; break;
                    case "%Y-%m-%d 00:00:00": replaceValue = "toDateTime#[formatDateTime#[toDateTime#["+ param1 +"], '%Y-%m-%d 00:00:00' ]]"; break;
                    case "%Y-%m-01 00:00:00": replaceValue = "toDateTime#[formatDateTime#[toDateTime#["+ param1 +"], '%Y-%m-01 00:00:00' ]]"; break;
                    case "%Y-01-01 00:00:00": replaceValue = "toDateTime#[formatDateTime#[toDateTime#["+ param1 +"], '%Y-01-01 00:00:00' ]]"; break;
                    case "%Y-%m-%d": replaceValue = "toDateTime#[formatDateTime#[toDateTime#["+ param1 +"], '%Y-%m-%d]]"; break;
                    case "%Y-%m-01": replaceValue = "toDateTime#[formatDateTime#[toDateTime#["+ param1 +"], '%Y-%m-01]]"; break;
                    case "%Y-01-01": replaceValue = "toDateTime#[formatDateTime#[toDateTime#["+ param1 +"], '%Y-01-01]]"; break;
                    case "": replaceValue = "toDateTime#[" + param1 + "]"; break;
                    default: replaceValue =  "toDateTime#[formatDateTime#[toDateTime#["+ param1 +"], '%Y-%m-%d %H:%M:%S' ]]";
                }
            } else if (type == 2) {
                switch (param2) {
                    case "%Y-%m-%d %H:%i:%s": replaceValue = "TIME_FORMAT#[ TIMESTAMP "+ param1 +",'YYYY-MM-dd HH:mm:ss']"; break;
                    case "%Y-%m-%d %H:%i:00": replaceValue = "TIME_FORMAT#[ TIMESTAMP "+ param1 +",'YYYY-MM-dd HH:mm:00']"; break;
                    case "%Y-%m-%d %H:00:00": replaceValue = "TIME_FORMAT#[ TIMESTAMP "+ param1 +",'YYYY-MM-dd HH:00:00']"; break;
                    case "%Y-%m-%d 00:00:00": replaceValue = "TIME_FORMAT#[ TIMESTAMP "+ param1 +",'YYYY-MM-dd 00:00:00']"; break;
                    case "%Y-%m-01 00:00:00": replaceValue = "TIME_FORMAT#[ TIMESTAMP "+ param1 +",'YYYY-MM-01 00:00:00']"; break;
                    case "%Y-01-01 00:00:00": replaceValue = "TIME_FORMAT#[ TIMESTAMP "+ param1 +",'YYYY-01-01 00:00:00']"; break;
                    case "%Y-%m-%d": replaceValue = "TIME_FORMAT#[ TIMESTAMP "+ param1 +",'YYYY-MM-dd']"; break;
                    case "%Y-%m-01": replaceValue = "TIME_FORMAT#[ TIMESTAMP "+ param1 +",'YYYY-MM-01']"; break;
                    case "%Y-01-01": replaceValue = "TIME_FORMAT#[ TIMESTAMP "+ param1 +",'YYYY-01-01']"; break;
                    case "": replaceValue = " TIMESTAMP " + param1; break;
                    default: replaceValue = "TIME_FORMAT#[ TIMESTAMP "+ param1 +",'YYYY-MM-dd HH:mm:ss']";
                }
            }
        }
        return replaceValue;
    }

    /**
     * 解析之后的sql处理
     * @param sql
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String specialDisposeEnd(String sql, int type) {
        if (sql == null) {
            return sql;
        }
        StringBuffer sb = new StringBuffer();
        if (type == 1) {
            Matcher mStrDateTime = pStrDateTime.matcher(sql);
            while (mStrDateTime.find()) {
                String innerValue = mStrDateTime.group(1) + " toDateTime#[" + mStrDateTime.group(2) + "]";
                mStrDateTime.appendReplacement(sb, innerValue);
            }
            mStrDateTime.appendTail(sb);
            sql = sb.toString();
        } else if (type == 2) {
            Matcher matcher = pCKTimeStamp.matcher(sql);
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
     * @param sql
     * @return
     */
    public static String replaceMark(String sql) {
        sql = sql.replace("[", "(");
        sql = sql.replace("]", ")");
        sql = sql.replace("#", "");
        return sql;
    }
    /**
     * 替换：FROM_UNIXTIME(unix_timestamp)、FROM_UNIXTIME(date,format)、DATE_FORMAT(date,format)、STR_TO_DATE(date,format)
     * @param sqlParse
     * @param num 包含括号的个数
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String dateFormatHavaMark(String sqlParse, int num, int type) {
        Pattern pDateFormatMark = Pattern.compile("(FROM_UNIXTIME|DATE_FORMAT|STR_TO_DATE)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)", Pattern.CASE_INSENSITIVE);
        Matcher mDateFormatMark = pDateFormatMark.matcher(sqlParse);
        StringBuffer sb = new StringBuffer();
        if (mDateFormatMark.find()) {
            String group2 = mDateFormatMark.group(2);
            String replaceValue = group2;
            if (StringUtil.getBracketsMatch(group2, "(", false)[0] >= 0) {
                replaceValue = dateFormatRealize(mDateFormatMark.group(1),group2, type);
            }
            mDateFormatMark.appendReplacement(sb, replaceValue);
        }
        mDateFormatMark.appendTail(sb);
        return sb.toString();
    }

    /**
     * 替换SUBDATE(expr,days)、SUBDATE(expr,days)、SUBDATE(date,INTERVAL expr type)、ADDDATE(date,INTERVAL expr type)、DATE_SUB(date,INTERVAL expr type)、DATE_ADD(date,INTERVAL expr type)
     * @param sqlParse
     * @param num 包含的括号个数
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String dateRelativeHaveMark(String sqlParse,int num,int type){
        Pattern pDateRelativeParse = Pattern.compile("(ADDDATE|DATE_ADD|SUBDATE|DATE_SUB)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcherParse = pDateRelativeParse.matcher(sqlParse);
        String innerValue = "";
        StringBuffer sb = new StringBuffer();
        if (matcherParse.find()) {
            Boolean sign;
            if(matcherParse.group(1).toUpperCase().contains("ADD")){
                sign = true;
            }else {
                sign = false;
            }
            if (StringUtil.getBracketsMatch(matcherParse.group(2), null,false)[0] >= 0) {
                List<String> params = diviParam(matcherParse.group(2), ",");
                innerValue = dateRelative(params, sign, type);
            }
            matcherParse.appendReplacement(sb, innerValue);
        }
        matcherParse.appendTail(sb);
        return sb.toString();
    }

    /**
     * 实现替换：UNIX_TIMESTAMP()、UNIX_TIMESTAMP(expr)
     * @param sqlParse
     * @param num 包含的括号个数
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String parseUnixTimeHavaMark(String sqlParse, int num, int type) {
        Pattern pUnixTimeParse = Pattern.compile("(UNIX_TIMESTAMP)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcherParse = pUnixTimeParse.matcher(sqlParse);
        StringBuffer sb = new StringBuffer();
        String innerValue = null;
        if (matcherParse.find()) {
            if (type == 1) {
                Matcher mNow = pNow.matcher(matcherParse.group(2));
                if (mNow.find() || matcherParse.group(2) == null || "".equals(matcherParse.group(2).trim())) {
                    innerValue = "toUnixTimestamp#[now#[]]";
                } else {
                    innerValue = "toUnixTimestamp#[ " + matcherParse.group(2) + "]";
                }
            } else if (type == 2) {
                Matcher mNow = pNow.matcher(matcherParse.group(2));
                if (mNow.find() || matcherParse.group(2) == null || "".equals(matcherParse.group(2).trim())) {
                    innerValue = "0.001 * TIMESTAMP_TO_MILLIS[CURRENT_TIMESTAMP]";
                } else {
                    innerValue = "0.001 * TIMESTAMP_TO_MILLIS#[ TIMESTAMP " + matcherParse.group(2) + "]";
                }
            }
            matcherParse.appendReplacement(sb, innerValue);
        }
        matcherParse.appendTail(sb);
        return sb.toString();
    }

    /**
     * 实现替换：DATE(expr)
     * @param sqlParse
     * @param num 包含的括号个数
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String parseDateHavaMark(String sqlParse, int num, int type) {
        Pattern pDateParse = Pattern.compile("(DATE)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcherParse = pDateParse.matcher(sqlParse);
        StringBuffer sb = new StringBuffer();
        String innerValue = null;
        if (matcherParse.find()) {
            if (type == 1) {
                innerValue = "toDate#[toDateTime#[" + matcherParse.group(2) + "]]";
            } else if (type == 2) {
                innerValue = "TIME_FLOOR#[ TIMESTAMP " + matcherParse.group(2) + ",'P1D']";
            }
            matcherParse.appendReplacement(sb, innerValue);
        }
        matcherParse.appendTail(sb);
        return sb.toString();
    }

    /**
     * 获取函数参数
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
}

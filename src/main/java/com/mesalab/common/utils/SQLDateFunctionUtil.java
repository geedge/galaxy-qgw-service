package com.mesalab.common.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author GouGe
 * @data 2019/8/1 10:13
 */
public class SQLDateFunctionUtil {
    public static Pattern pDateFormat = Pattern.compile("(STR_TO_DATE|DATE_FORMAT|FROM_UNIXTIME)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static Pattern pDateRelative = Pattern.compile("(ADDDATE|DATE_ADD|DATE_SUB|SUBDATE)\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static Pattern pIntervalNumUnit = Pattern.compile("INTERVAL(.*?)([A-Z]{3,7}$)", Pattern.CASE_INSENSITIVE);
    public static Pattern pUnixTime = Pattern.compile("UNIX_TIMESTAMP\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static Pattern pDate = Pattern.compile("DATE\\s*\\(", Pattern.CASE_INSENSITIVE);
    public static Pattern pCKTimeStamp = Pattern.compile("\\s+TIMESTAMP\\s+([^'])", Pattern.CASE_INSENSITIVE);
    public static Pattern pMod = Pattern.compile("(\\))\\s*%\\s*(\\()", Pattern.CASE_INSENSITIVE);
    public static Pattern pConcatQuarter = Pattern.compile("CONCAT\\s*\\(\\s*YEAR\\s*\\((.*?)\\)\\s*,\\s*'-'\\s*,\\s*ELT.*?QUARTER.*?'-'\\s*,\\s*1\\s*\\)", Pattern.CASE_INSENSITIVE);
    public static Pattern pNow = Pattern.compile("NOW\\(\\s*\\)", Pattern.CASE_INSENSITIVE);

    /**
     * 匹配STR_TO_DATE(、DATE_FORMAT(、FROM_UNIXTIME(、FROM_UNIXTIME(
     * @param sql 需要解析的sql
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String parseDateFormat(String sql, int type) {
        int count = 0;
        while (true) {
            count++;
            Matcher matcher = pDateFormat.matcher(sql);
            if (!matcher.find() || count >= 15) {
                return sql;
            }
            String sqlParse = sql.substring(matcher.start());
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, null, false);
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
            count++;
            Matcher matcher = pDateRelative.matcher(sql);
            if (!matcher.find() || count >= 15) {
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
     * 匹配UNIX_TIMESTAMP(、UNIX_TIMESTAMP(
     * @param sql
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String parseUnixTime(String sql, int type) {
        int count = 0;
        while (true) {
            count++;
            Matcher matcher = pUnixTime.matcher(sql);
            if (!matcher.find() || count == 15) {
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
     * 匹配DATE(
     * @param sql
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String parseDate(String sql, int type) {
        int count = 0;
        while (true) {
            count++;
            Matcher matcher = pDate.matcher(sql);
            if (!matcher.find() || count >= 15) {
                return sql;
            }
            String sqlParse = sql.substring(matcher.start());
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, null, false);
            if (bracketsMatch[0] >= 1) {
                --bracketsMatch[0];
            }
            String str = parseDateHavaMark(sqlParse, bracketsMatch[0], type);
            sql = sql.substring(0, matcher.start()) + str;
        }
    }

    /**
     * 实现相对时间相关替换
     * @param expr
     * @param date
     * @param sign ADD相关为true,SUB相关为false;
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    private static String dateRelative(String expr, String date, Boolean sign, int type) {
        date = date.trim();
        String value = "";
        if (date.toUpperCase().contains("INTERVAL")) {
            Matcher matcher = pIntervalNumUnit.matcher(date);
            if (matcher.find()) {
                String numStr = matcher.group(1).replaceAll("\\s*", "");
                int num = Integer.parseInt(numStr);
                if(num > 0){
                    numStr = "+" + numStr;
                }
                if (!sign) {
                    numStr = String.valueOf(-num);
                }
                if(type == 1){
                    switch (matcher.group(2)){
                        case "SECOND": value = "toDateTime#[" + expr + "]" + numStr; break;
                        case "MINUTE": value = "toDateTime#[" + expr + "]" + numStr + "*60"; break;
                        case "HOUR": value = "toDateTime#[" + expr + "]" + numStr + "*3600"; break;
                        case "DAY": value = "toDateTime#[" + expr + "]" + numStr+ "*86400"; break;//24*3600
                        case "WEEK": value = "toDateTime#[" + expr + "]" + numStr + "*604800"; break;//7*24*3600
                        case "MONTH": value = "toDateTime#[" + expr + "]" + numStr + "*2678400"; break;//31*24*3600
                        case "QUARTER": value = "toDateTime#[" + expr + "]" + numStr + "*8035200"; break;//3*31*24*3600
                        case "YEAR": value = "toDateTime#[" + expr + "]" + numStr + "*31536000"; break;//365*24*3600
                        default:value = expr + numStr; break;
                    }
                }else if(type ==2){
                    switch (matcher.group(2)) {
                        case "SECOND": value = "TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1S',"+ numStr +"]"; break;
                        case "MINUTE": value = "TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1M',"+ numStr +"]"; break;
                        case "HOUR": value = "TIME_SHIFT#[ TIMESTAMP " + expr + ",'PT1H',"+ numStr +"]"; break;
                        case "DAY": value = "TIME_SHIFT#[ TIMESTAMP " + expr + ",'P1D',"+ numStr +"]"; break;
                        case "WEEK": value = "TIME_SHIFT#[ TIMESTAMP " + expr + ",'P1W',"+ numStr +"]"; break;
                        case "MONTH": value = "TIME_SHIFT#[ TIMESTAMP " + expr + ",'P1M',"+ numStr +"]"; break;
                        case "QUARTER": value = "TIME_SHIFT#[ TIMESTAMP " + expr + ",'P1M',"+ numStr +"]"; break;
                        case "YEAR": value = "TIME_SHIFT#[ TIMESTAMP " + expr + ",'P1Y',"+ numStr +"]"; break;
                        default:value = date + expr; break;
                    }
                }
                return value;
            }
        } else {
            int num = Integer.parseInt(date);
            String numStr = date;
            if(num > 0){
                numStr = "+" + numStr;
            }
            if (!sign) {
                numStr = String.valueOf(-num);
            }
            if(type == 1){
                value = "toDateTime#[" + expr + "]" + numStr + "* 86400";
            }else if(type ==2){
                value = "TIME_SHIFT#[ TIMESTAMP " + expr + ",'P1D'," + numStr + "]";
            }
        }
        return value;
    }


    /**
     * 实现时间格式相关替换:
     * @param expr
     * @param format
     * @param type
     * @return
     */
    private static String dateFormat(String expr, String format, int type) {
        String value = null;
        if (type == 1) {
            switch (format) {
                case "%Y-%m-%d %H:%i:%s": value = "toDateTime#[" + expr + "]"; break;
                case "%Y-%m-%d %H:%i:00": value = "toStartOfMinute#[toDateTime#[" + expr + "]]"; break;
                case "%Y-%m-%d %H:00:00": value = "toStartOfHour#[toDateTime#[" + expr + "]]"; break;
                case "%Y-%m-%d 00:00:00": value = "toStartOfDay#[toDateTime#[" + expr + "]]"; break;
                case "%Y-%m-01 00:00:00": value = "toDateTime#[toStartOfMonth#[toDateTime#[" + expr + "]]]"; break;
                case "%Y-01-01 00:00:00": value = "toDateTime#[toStartOfYear#[toDateTime#[" + expr + "]]]"; break;
                case "%Y-%m-%d": value = "toStartOfMonth#[toDateTime#[" + expr + "]]"; break;
                case "%Y-%m-01": value = "toStartOfMonth#[toDateTime#[" + expr + "]]"; break;
                case "%Y-01-01": value = "toStartOfYear#[toDateTime#[" + expr + "]]"; break;
                case "": value = "toDateTime#[" + expr + "]"; break;
                default: value = expr + "," + format;
            }
        } else if (type == 2) {
            switch (format) {
                case "%Y-%m-%d %H:%i:%s": value = " TIMESTAMP " + expr; break;
                case "%Y-%m-%d %H:%i:00": value = "FLOOR#[ TIMESTAMP " + expr + " to minute]"; break;
                case "%Y-%m-%d %H:00:00": value = "FLOOR#[ TIMESTAMP " + expr + " to hour]"; break;
                case "%Y-%m-%d 00:00:00": value = "FLOOR#[ TIMESTAMP " + expr + " to day]"; break;
                case "%Y-%m-01 00:00:00": value = "FLOOR#[ TIMESTAMP " + expr + " to month]"; break;
                case "%Y-01-01 00:00:00": value = "FLOOR#[ TIMESTAMP " + expr + " to year]"; break;
                case "%Y-%m-%d": value = "TIME_FLOOR#[ TIMESTAMP " + expr + ",'P1D']"; break;
                case "%Y-%m-01": value = "TIME_FLOOR#[ TIMESTAMP " + expr + ",'P1M']"; break;
                case "%Y-01-01": value = "TIME_FLOOR#[ TIMESTAMP " + expr + ",'P1Y']"; break;
                case "": value = " TIMESTAMP " + expr; break;
                default: value = expr + "," + format;
            }
        }
        return value;
    }

    /**
     * 替换()%()取余操作：druid
     * @param sql
     * @return
     */
    public static String parseDruidMod(String sql) {
        int count = 0;
        while (true) {
            Matcher mMod = pMod.matcher(sql);
            count++;
            if (!mMod.find() || count >= 15) {
                return sql;
            }
            int start = mMod.start();
            int end = mMod.end();
            String sqlParseL = sql.substring(0, start + 1);
            String sqlParseR = sql.substring(end - 1);
            int[] numL = StringUtil.getBracketsMatchReverse(sqlParseL, null, false);
            int[] numR = StringUtil.getBracketsMatch(sqlParseR, null, false);
            if (numL[0] >= 1) {
                --numL[0];
            }
            if (numR[0] >= 1) {
                --numR[0];
            }
            sql = parseDruidModHavaMark(sql, numL[0], numR[0]);
        }
    }

    /**
     * 解析之后的sql处理
     * @param sql
     * @param type
     * @return
     */
    public static String specialDisposeEnd(String sql, int type) {
        if (sql == null) {
            return sql;
        }
        StringBuffer sb = new StringBuffer();
        if (type == 1) {
            return sql;
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
     * 替换粒度：季度
     * @param sql
     * @param type
     * @return
     */
    public static String parseConcatQuarter(String sql, int type) {
        Matcher mQuarter = pConcatQuarter.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (mQuarter.find()) {
            String replaceValue = "";
            if (mQuarter.groupCount() == 1) {
                if (type == 1) {
                    replaceValue = "toStartOfQuarter#[toDateTime#[toDateTime#[" + mQuarter.group(1) + "]]]";
                } else if (type == 2) {
                    replaceValue = "FLOOR#[ TIMESTAMP " + mQuarter.group(1) + " to Quarter]";
                }
            }
            mQuarter.appendReplacement(sb, replaceValue);
        }
        mQuarter.appendTail(sb);
        return sb.toString();
    }

    /**
     * 替换STR_TO_DATE(expr,format)、DATE_FORMAT(expr,format)、FROM_UNIXTIME(expr,format)、FROM_UNIXTIME(expr)
     * @param sqlParse
     * @param num 包含的括号个数
     * @param type 1:ClickHouse;    2:Druid
     * @return
     */
    public static String dateFormatHavaMark(String sqlParse, int num, int type) {
        Pattern pDateFormatParse = Pattern.compile("(STR_TO_DATE|DATE_FORMAT|FROM_UNIXTIME)\\s*\\((.*?(.*?\\).*?){" + num + "})\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcherParse = pDateFormatParse.matcher(sqlParse);
        String expr = "", format = "";
        StringBuffer sb = new StringBuffer();
        String innerValue = null;
        if (matcherParse.find()) {
            if (StringUtil.getBracketsMatch(matcherParse.group(2), null, false)[0] >= 0) {
                String group2 = matcherParse.group(2);
                String[] split = group2.split(",");
                label:
                for (int i = 0; i < split.length; i++) {
                    expr += split[i];
                    if (StringUtil.getBracketsMatch(expr, null, true)[0] >= 0) {
                        for (int j = i + 1; j < split.length; j++) {
                            format += split[j];
                            if (j < split.length - 1) {
                                format += ",";
                            }
                        }
                        break label;
                    }
                    expr += ",";
                }
                format = format.replaceAll("\\'|\\\"", "").trim();
                //对druid 的特殊处理
                if (matcherParse.group(1).contains("FROM_UNIXTIME") && type == 2 && ("%Y-%m-%d %H:%i:%s".equals(format) || "".equals(format))) {
                    innerValue = "MILLIS_TO_TIMESTAMP#[ 1000*[" + expr + "]]";
                } else {
                    innerValue = dateFormat(expr, format, type);
                }
            }
            matcherParse.appendReplacement(sb, innerValue);
        }
        matcherParse.appendTail(sb);
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
        String expr = "", date = "",innerValue = "";
        StringBuffer sb = new StringBuffer();
        if (matcherParse.find()) {
            Boolean sign;
            if(matcherParse.group(1).toUpperCase().contains("ADD")){
                sign = true;
            }else {
                sign = false;
            }
            if (StringUtil.getBracketsMatch(matcherParse.group(2), null,false)[0] >= 0) {
                String group2 = matcherParse.group(2);
                String[] split = group2.split(",");
                label:
                for (int i = 0; i < split.length; i++) {
                    expr += split[i];
                    if (StringUtil.getBracketsMatch(expr, null,true)[0] >= 0) {
                        for (int j = i + 1; j < split.length; j++) {
                            date += split[j];
                            if (j < split.length - 1) {
                                date += ",";
                            }
                        }
                        break label;
                    }
                    expr += ",";
                }
                innerValue = dateRelative(expr, date, sign, type);
            }
            matcherParse.appendReplacement(sb, innerValue);
        }
        matcherParse.appendTail(sb);
        return sb.toString();
    }

    /**
     * 替换UNIX_TIMESTAMP()、UNIX_TIMESTAMP(expr)
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
     * 替换DATE(expr)
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
                innerValue = "toStartOfDay#[toDateTime#[" + matcherParse.group(2) + "]]";
            } else if (type == 2) {
                innerValue = "TIME_FLOOR#[ TIMESTAMP " + matcherParse.group(2) + ",'P1D']";
            }
            matcherParse.appendReplacement(sb, innerValue);
        }
        matcherParse.appendTail(sb);
        return sb.toString();
    }

    /**
     * 匹配%两边()中的字符
     * @param sql
     * @param numL 左边括号个数
     * @param numR 右边括号个数
     * @return
     */
    public static String parseDruidModHavaMark(String sql, int numL, int numR) {
        Pattern pModParse = Pattern.compile("\\((.*?(.*?\\(.*?){" + numL + "})\\)\\s*%\\s*\\((.*?(.*?\\).*?){" + numR + "})\\)", Pattern.CASE_INSENSITIVE);
        StringBuffer sb = new StringBuffer();
        Matcher mModParse = pModParse.matcher(sql);
        String value;
        if (mModParse.find()) {
            value = "mod#[" + mModParse.group(1) + "," + mModParse.group(3) + "]";
            mModParse.appendReplacement(sb, value);
        }
        mModParse.appendTail(sb);
        return sb.toString();
    }
}

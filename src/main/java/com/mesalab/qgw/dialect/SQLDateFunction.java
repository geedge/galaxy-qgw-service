package com.mesalab.qgw.dialect;

import com.mesalab.common.utils.SQLHelper;

import java.util.regex.Matcher;

/**
 * @author GouGe
 * @data 2019/7/25 14:13
 */
public interface SQLDateFunction {

     default String generateDateFunction(String sql) {
         sql = dateFunWholeReplace(sql);
         StringBuffer sqlResult = new StringBuffer("(" + sql + ")");
         while (true) {
             if (sqlResult.indexOf("(") == -1) {
                 sql = replaceMark(sqlResult.toString());
                 sql = specialDisposeEnd(sql);
                 return sql;
             }
             StringBuffer sqlBuffer = getNeedMatchStr(sqlResult);
             if (!SQLHelper.pKeyWord.matcher(sqlBuffer).find()) {
                 sqlResult = getResultReplaceMark(sqlResult);
                 continue;
             }
             StringBuffer sb;
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
             sb = replaceAboutUnixTime(sqlBuffer);
             sqlResult = getResultAfterReplace(sqlResult, sb.toString());

             sqlBuffer = getNeedMatchStr(sqlResult);
             sb = replaceAboutGetOnlyNum(sqlBuffer);

             sqlResult = sqlResult.replace(sqlResult.lastIndexOf("("), sqlResult.indexOf(")", sqlResult.lastIndexOf("(")) + 1, "[" + sb + "]");
         }
    }

    /**
     *  特殊处理(处理结果)：目前包括(druid的TIMESTAMP去重)
     * @param sql
     * @return
     */
    String specialDisposeEnd(String sql);

    /**
     * 全局替换部分内容：目前为粒度(周、季、5min、30s)
     * @param sql
     * @return
     */
    default String dateFunWholeReplace(String sql){
        StringBuffer sb = new StringBuffer(sql);
        sb = replaceGranularityWeek(sb);
        sb = replaceGranularityQuarter(sb);
        sb = replaceGranularityTime(sb);
        return sb.toString();
    }

    StringBuffer replaceGranularityTime(StringBuffer sb);

    StringBuffer replaceGranularityQuarter(StringBuffer sb);

    StringBuffer replaceGranularityWeek(StringBuffer sb);

    StringBuffer replaceAboutGetOnlyNum(StringBuffer sqlBuffer);

    /**
     * 替换有关时间戳函数：FROM_UNIXTIME、UNIX_TIMESTAMP
     * @param sqlBuffer
     * @return
     */
    StringBuffer replaceAboutUnixTime(StringBuffer sqlBuffer);

    /**
     * 取出字符串中最后一个括号中的值
     * @param str
     * @return
     */
    default StringBuffer getNeedMatchStr(StringBuffer str) {
        int start = str.lastIndexOf("(");
        int end = str.indexOf(")", start);
        return new StringBuffer(str.substring(start + 1, end));
    }

    /**
     * 获取更新sql标记[]后的串
     * @param sqlResult
     * @return
     */
    default StringBuffer getResultReplaceMark(StringBuffer sqlResult) {
        int start = sqlResult.lastIndexOf("(");
        int end = sqlResult.indexOf(")", start);
        sqlResult = sqlResult.replace(start, start + 1, "[");
        sqlResult = sqlResult.replace(end, end + 1, "]");
        return sqlResult;
    }

    /**
     * 获取替换后的串
     * @param resultStr 原始值
     * @param str 需替换值
     * @return
     */
    default StringBuffer getResultAfterReplace(StringBuffer resultStr, String str) {
        int start = resultStr.lastIndexOf("(");
        int end = resultStr.indexOf(")", start);
        return resultStr.replace(start + 1, end, str);
    }

    /**
     * 单纯替换字符
     * @param sql
     * @return
     */
    default String replaceMark(String sql) {
        sql = sql.replace("[", "(");
        sql = sql.replace("]", ")");
        sql = sql.replace("#", "");
        sql = sql.substring(1, sql.length() - 1);
        return sql;
    }

    /**
     * STR_TO_DATE(str,format)、DATE_FORMAT(str,format)、FROM_UNIXTIME(unix_timestamp,format)替换
     * @param sqlBuffer
     * @return
     */
     default StringBuffer replaceExprFormat(StringBuffer sqlBuffer) {
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

    /**
     * DATE_ADD(date,INTERVAL expr type)、ADDDATE(date,INTERVAL expr type)替换
     * @param sqlBuffer
     * @return
     */
    default StringBuffer replaceAboutAddInterval(StringBuffer sqlBuffer) {
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
     * DATE_SUB(date,INTERVAL expr type)、SUBDATE(date,INTERVAL expr type)替换
     * @param sqlBuffer
     * @return
     */
    default StringBuffer replaceAboutSubInterval(StringBuffer sqlBuffer) {
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
     * ADDDATE(expr,days)、SUBDATE(expr,days)替换
     * @param sqlBuffer
     * @return
     */
    default StringBuffer replaceAboutExprDays(StringBuffer sqlBuffer) {
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
     * ADDDATE(expr,days)、SUBDATE(expr,days)替换
     * @param date
     * @param expr
     * @param shift 判断 ADDDATE:true    SUBDATE:false
     * @return
     */
    String convertValueAddDate(String expr, String date, boolean shift);

    /**
     *  INVERTAL
     * @param date
     * @param expr
     * @param shift 判断 DATE_ADD:true    DATE_SUB:false
     * @return
     */
    String convertValueDateAdd(String date, String expr, boolean shift);

    /**
     * FORMAT
     * @param innerValue
     * @return
     */
    String convertValueExprFormat(String innerValue);
}

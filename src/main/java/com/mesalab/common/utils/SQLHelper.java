package com.mesalab.common.utils;

import com.alibaba.druid.util.JdbcConstants;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.qgw.dialect.Dialect;
import com.mesalab.qgw.model.api.SQLQuerySource;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLHelper {

    public static Pattern pExprFormat = Pattern.compile("STR_TO_DATE\\s*\\[(.*?,\\s*'\\s*%.*?)\\]|DATE_FORMAT\\s*\\[(.*?,\\s*'\\s*%.*?)\\]|FROM_UNIXTIME\\s*\\[(.*?,\\s*'\\s*%.*?)\\]", Pattern.CASE_INSENSITIVE);
    public static Pattern pAddInterval = Pattern.compile("DATE_ADD\\[(.*?)\\s*,\\s*INTERVAL(.*?)\\]|ADDDATE\\[(.*?)\\s*,\\s*INTERVAL(.*?)\\]", Pattern.CASE_INSENSITIVE);
    public static Pattern pSubInterval = Pattern.compile("DATE_SUB\\[(.*?)\\s*,\\s*INTERVAL(.*?)\\]|SUBDATE\\[(.*?)\\s*,\\s*INTERVAL(.*?)\\]", Pattern.CASE_INSENSITIVE);
    public static Pattern pAddDate = Pattern.compile("ADDDATE\\[(.*?)\\s*,\\s*([-+]?\\s*\\d)\\]|SUBDATE\\[(.*?)\\s*,\\s*([-+]?\\s*\\d)\\]", Pattern.CASE_INSENSITIVE);
    public static Pattern pYear = Pattern.compile("YEAR\\s*\\[(.*?)\\]|QUARTER\\s*\\[(.*?)\\]|MONTH\\s*\\[(.*?)\\]|WEEK\\s*\\[(.*?)\\]|DATE\\s*\\[(.*?)\\]|HOUR\\s*\\[(.*?)\\]", Pattern.CASE_INSENSITIVE);
    public static Pattern pUnixTime = Pattern.compile("(UNIX_TIMESTAMP\\s*\\[)|(FROM_UNIXTIME\\s*\\[)", Pattern.CASE_INSENSITIVE);
    public static Pattern pDateUnit = Pattern.compile("(.*?)([A-Z]{3,7}$)", Pattern.CASE_INSENSITIVE);
    public static Pattern pNum = Pattern.compile("^-?\\s*\\d+$", Pattern.CASE_INSENSITIVE);
    public static Pattern pWeek = Pattern.compile("STR_TO_DATE\\s*\\(\\s*DATE_FORMAT\\s*\\((.*?),\\s*('\\s*%.*?)\\)\\s*,(.*?)\\s*\\)", Pattern.CASE_INSENSITIVE);
    public static Pattern pDateGranularity = Pattern.compile("STR_TO_DATE\\s*\\(\\s*CONCAT\\s*\\((\\s*DATE.*?HOUR\\s*\\((.*?)\\)\\s*,\\s*':'.*?%.*?\\s*\\))|DATE_FORMAT\\s*\\(\\s*CONCAT\\s*\\((\\s*DATE.*?HOUR\\s*\\((.*?)\\)\\s*,\\s*':'.*?%.*?\\s*\\))", Pattern.CASE_INSENSITIVE);
    public static Pattern pConcatQuarter = Pattern.compile("CONCAT\\s*\\(\\s*YEAR\\s*\\((.*?)\\)\\s*,\\s*'-'\\s*,\\s*ELT.*?QUARTER.*?'-'\\s*,\\s*1\\s*\\)", Pattern.CASE_INSENSITIVE);
    public static Pattern pKeyWord = Pattern.compile("STR_TO_DATE|DATE_FORMAT|FROM_UNIXTIME|DATE_ADD|DATE_SUB|SUBDATE|ADDDATE|YEAR|QUARTER|MONTH|WEEK|DATE|HOUR|UNIX_TIMESTAMP", Pattern.CASE_INSENSITIVE);

    public static Pattern pOrder = Pattern.compile("order\\s*by[\\w|\\W|\\s|\\S]*", Pattern.CASE_INSENSITIVE);
    public static Pattern pLimit = Pattern.compile("limit\\s*[\\w|\\W|\\s|\\S]*", Pattern.CASE_INSENSITIVE);

    /**
     * 去除qlString的select子句。
     *
     * @param qlString
     * @return
     */
    public static String removeSelect(String qlString) {
        int beginPos = qlString.toLowerCase().indexOf("from");
        return qlString.substring(beginPos);
    }
    /**
     * 去除hql的orderBy子句。
     *
     * @param qlString
     * @return
     */
    public static String removeOrders(String qlString) {
        Matcher m = pOrder.matcher(qlString);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String removeLimit(String qlString) {
        Matcher m = pLimit.matcher(qlString);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);
        return sb.toString();
    }


    public static String getDialectDBType(String dbType) {
        if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
            return JdbcConstants.MYSQL;
        } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
           return JdbcConstants.HIVE;
        } else if (DBTypeEnum.HBASE.getValue().equals(dbType)) {
           return JdbcConstants.MYSQL;
        } else {
            return null;
        }

    }





}

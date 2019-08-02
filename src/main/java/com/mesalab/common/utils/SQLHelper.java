package com.mesalab.common.utils;

import com.alibaba.druid.util.JdbcConstants;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.qgw.dialect.Dialect;
import com.mesalab.qgw.model.api.SQLQuerySource;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLHelper {

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

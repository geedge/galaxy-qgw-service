package com.mesalab.common.utils;

import com.alibaba.druid.util.JdbcConstants;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.qgw.dialect.ClickHouseDialect;
import com.mesalab.qgw.dialect.Dialect;
import com.mesalab.qgw.dialect.DruidDialect;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.zdjizhi.utils.StringUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLHelper {

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
        Pattern p = Pattern.compile("order\\s*by[\\w|\\W|\\s|\\S]*", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(qlString);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String removeLimit(String qlString) {
        Pattern p = Pattern.compile("limit\\s*[\\w|\\W|\\s|\\S]*", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(qlString);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);
        return sb.toString();
    }



    public static Dialect getDBDialect(String dbType) {
        Dialect dialect = null;
        if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
            dialect = new ClickHouseDialect(JdbcConstants.CLICKHOUSE);
        } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
            dialect = new DruidDialect(JdbcConstants.HIVE);
        }

        if (dialect == null) {
            throw new RuntimeException("get dbType dialect error.");
        }

        return dialect;

    }


    /**
     * 生成分页SQL
     * @param sql
     * @param dialect
     * @return
     */
    public static String generatePageSql(String sql, Dialect dialect, SQLQuerySource sqlQuerySource) {

        if (dialect.supportsLimit()) {
            return  dialect.getLimitString(sql, sqlQuerySource);
        } else {
            return sql;
        }
    }


}

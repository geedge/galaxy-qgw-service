package com.mesalab.common.utils;

import com.alibaba.druid.util.JdbcConstants;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.qgw.dialect.Dialect;
import com.mesalab.qgw.model.api.SQLQuerySource;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
@Slf4j
public class SQLHelper {

    public static Pattern pOrder = Pattern.compile("order\\s*by[\\w|\\W|\\s|\\S]*", Pattern.CASE_INSENSITIVE);


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
     * 去除orderBy子句。
     *
     * @param sql
     * @return
     */
    public static String removeOrders(String sql) {
        Matcher m = pOrder.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);
        return sb.toString();
    }



    /**
     * 为当前SQL最外层增加Limit限制
     * @param sql 原始SQL语句
     * @param offset 开始条数
     * @param rowCount 每页显示多少纪录条数
     * @return limit限制后SQL语句
     */
    public static String getLimitString(String sql, int offset, int rowCount) {
        try {
            Select selectStatement = (Select) CCJSqlParserUtil.parse(sql);
            PlainSelect select = (PlainSelect) selectStatement.getSelectBody();
            Limit limit = new Limit();
            if (offset > 0) {
                limit.setOffset(new LongValue(offset));
            }
            limit.setRowCount(new LongValue(rowCount));
            select.setLimit(limit);
            sql = String.valueOf(selectStatement.getSelectBody());
        } catch (Exception e) {
            log.error("Set limit error, SQL is:{} , Error Message is :{} ", sql, e);
        }

        return sql;
    }


    /**
     * 获取当前数据库预定义的方言
     * @param dbType
     * @return
     */
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

package com.mesalab.common.utils.sqlparser;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.druid.util.JdbcConstants;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SQLHelper {

    public static final Pattern pOrder = Pattern.compile("order\\s*by[\\w|\\W|\\s|\\S]*", Pattern.CASE_INSENSITIVE);
    public static final String INVALID_LIMIT_DESC = "NO LIMIT";

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
     *
     * @param sql      原始SQL语句
     * @param offset   开始条数
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
        } catch (JSQLParserException e) {
            log.error("Set limit error, SQL is:{} , Error Message is :{} ", sql, e);
        }

        return sql;
    }


    /**
     * 获取当前数据库预定义的方言
     *
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

    /**
     * Desc: 获取当前sql的表名
     *
     * @param sql
     * @return {@link String}
     * @created by wWei
     * @date 2021/2/24 4:39 下午
     */
    public static String getTableName(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (statement instanceof Select) {
                Select selectStatement = (Select) statement;
                TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
                List<String> tableList = tablesNamesFinder.getTableList(selectStatement);
                if (StringUtil.isNotEmpty(tableList)) {
                    String tableName = tableList.get(0);
                    int i = tableName.lastIndexOf(".");
                    return i < 0 ? tableName : tableName.substring(i + 1);
                } else {
                    log.error("SQL Syntax Error: Get Table Name Error, SQL is:{}", sql);
                    throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "SQL Syntax Error: Get Table Name Error.", null);
                }
            } else {
                log.warn("Not support DML Parser");
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "SQL Syntax Error, Not support DML Parser.", null);
            }
        } catch (JSQLParserException e) {
            log.error("SQL Syntax Error: Get Table Name Error, SQL is:{}, Error is:{}", sql, e);
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "SQL Syntax Error.", e.getCause());
        }
    }

    /**
     * 构建查询唯一标识：Query ID，由resultID+query 组成。
     *
     * @param resultId
     * @param query
     * @return {@link String}
     * @created by wWei
     * @date 2021/2/24 7:23 下午
     */
    public static String getQueryId(String resultId, String query) {
        return  DigestUtil.md5Hex(resultId + query.trim());
    }

}

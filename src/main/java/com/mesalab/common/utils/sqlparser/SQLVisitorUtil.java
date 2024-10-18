package com.mesalab.common.utils.sqlparser;

import cn.hutool.core.util.StrUtil;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.service.DatabaseService;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import net.sf.jsqlparser.util.deparser.SelectDeParser;

/**
 * @Classname ColumnVisitor
 * @Date 2021/10/22 2:26 下午
 * @Author wWei
 */
public class SQLVisitorUtil {

    private static DatabaseService databaseService = (DatabaseService) SpringContextUtil.getBean("databaseService");

    /**
     * Desc: 将SQL列名、别名、表名添加双引号转义
     *
     * @param
     * @return {@link SelectVisitor}
     * @created by wWei
     * @date 2022/8/24 5:10 下午
     */
    public static SelectVisitor getVisitorOfEscapeMetadataWithDoubleQuote() {
        ExpressionDeParser expressionDeParser = new ExpressionDeParser() {
            @Override
            public StringBuilder visit(Column column, Object context) {
                Table table = column.getTable();
                if (!StrUtil.isBlankIfStr(table)) {
                    table.setName(addDoubleQuote(table.getName()));
                }
                String columnName = column.getColumnName();
                column.setColumnName(addDoubleQuote(columnName));
                return null;
            }
        };

        return new SelectDeParser(expressionDeParser, new StringBuilder()) {
            @Override
            public StringBuilder visit(Table table, Object context) {
                String name = table.getName();
                if (!StrUtil.isBlankIfStr(name)) {
                    table.setName(addDoubleQuote(name));
                }
                table.setSchemaName(addDoubleQuote(StrUtil.isBlank(table.getSchemaName()) ? databaseService.getDBNameByTableName(name) : table.getSchemaName()));
                Alias alias = table.getAlias();
                if (!StrUtil.isBlankIfStr(alias)) {
                    alias.setName(addDoubleQuote(alias.getName()));
                }
                return null;
            }
        };
    }

    /**
     * Desc: 替换列名
     *
     * @param newColumnName
     * @return {@link ExpressionVisitor}
     * @created by wWei
     * @date 2022/8/25 6:18 下午
     */
    public static ExpressionVisitor getVisitorOfRenameColumn(String newColumnName) {
        return new ExpressionVisitorAdapter() {
            @Override
            public Object visit(Column column, Object context) {
                column.setColumnName(newColumnName);
                return null;
            }
        };
    }

    private static String addDoubleQuote(String str) {
        return StrUtil.isSurround(str, "\"", "\"") ? str : "\"" + str + "\"";
    }
}

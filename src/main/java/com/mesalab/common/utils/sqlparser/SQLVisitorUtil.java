package com.mesalab.common.utils.sqlparser;

import cn.hutool.core.util.StrUtil;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.service.MetadataService;
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

    private static MetadataService metadataService = (MetadataService) SpringContextUtil.getBean("metadataService");

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
            public void visit(Column column) {
                Table table = column.getTable();
                if (!StrUtil.isBlankIfStr(table)) {
                    table.setName(addDoubleQuote(table.getName()));
                }
                String columnName = column.getColumnName();
                column.setColumnName(addDoubleQuote(columnName));
            }
        };

        return new SelectDeParser(expressionDeParser, new StringBuilder()) {
            @Override
            public void visit(Table table) {
                String name = table.getName();
                if (!StrUtil.isBlankIfStr(name)) {
                    table.setName(addDoubleQuote(name));
                }
                table.setSchemaName(addDoubleQuote(StrUtil.isBlank(table.getSchemaName()) ? metadataService.getDBNameByTableName(name) : table.getSchemaName()));
                Alias alias = table.getAlias();
                if (!StrUtil.isBlankIfStr(alias)) {
                    alias.setName(addDoubleQuote(alias.getName()));
                }
            }

            @Override
            public void visit(AllTableColumns allTableColumns) {
                Table table = allTableColumns.getTable();
                if (!StrUtil.isBlankIfStr(table)) {
                    table.setName(addDoubleQuote(table.getName()));
                }
            }

            @Override
            public void visit(SelectExpressionItem selectExpressionItem) {
                selectExpressionItem.getExpression().accept(expressionDeParser);
                Alias alias = selectExpressionItem.getAlias();
                if (!StrUtil.isBlankIfStr(alias) && !StrUtil.isBlank(alias.getName())) {
                    alias.setName(addDoubleQuote(alias.getName()));
                }
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
            public void visit(Column column) {
                column.setColumnName(newColumnName);
            }
        };
    }

    private static String addDoubleQuote(String str) {
        return StrUtil.isSurround(str, "\"", "\"") ? str : "\"" + str + "\"";
    }
}

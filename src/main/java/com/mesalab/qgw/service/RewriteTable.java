package com.mesalab.qgw.service;

import com.mesalab.common.utils.SpringContextUtil;
import com.geedgenetworks.utils.StringUtil;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.ExpressionVisitor;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.Pivot;
import net.sf.jsqlparser.util.deparser.SelectDeParser;

/**
 * TODO
 *
 * @Classname TablesNamesFinder
 * @Date 2022/8/18 4:42 下午
 * @Author wWei
 */
public class RewriteTable extends SelectDeParser {

    private static DatabaseService databaseService = (DatabaseService) SpringContextUtil.getBean("databaseService");

    public RewriteTable(ExpressionVisitor expressionVisitor, StringBuilder buffer) {
        super(expressionVisitor, buffer);
    }

    @Override
    public StringBuilder visit(Table table, Object context) {
        if (StringUtil.isBlank(table.getSchemaName())) {
            table.setSchemaName(databaseService.getDBNameByTableName(table.getName()));
        }
        if (((table.getAlias() == null)) || (StringUtil.isBlank(table.getAlias().getName()))) {
            Alias alias = new Alias(table.getName(), true);
            table.setAlias(alias);
        }
        StringBuilder buffer = getBuffer();
        buffer.append(table.getFullyQualifiedName());
        Pivot pivot = table.getPivot();
        if (pivot != null) {
            pivot.accept(this, null);
        }
        Alias alias = table.getAlias();
        if (alias != null) {
            buffer.append(alias);
        }
        return null;
    }
}
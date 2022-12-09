package com.mesalab.qgw.service;

import com.mesalab.qgw.utils.SpringContextUtil;
import com.zdjizhi.utils.StringUtil;
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

    private static MetadataService metadataService = (MetadataService) SpringContextUtil.getBean("metadataService");

    public RewriteTable(ExpressionVisitor expressionVisitor, StringBuilder buffer) {
        super(expressionVisitor, buffer);
    }

    @Override
    public void visit(Table table) {
        if (StringUtil.isBlank(table.getSchemaName())) {
            table.setSchemaName(metadataService.getDBNameByTableName(table.getName()));
        }
        if (((table.getAlias() == null)) || (StringUtil.isBlank(table.getAlias().getName()))) {
            Alias alias = new Alias(table.getName(), true);
            table.setAlias(alias);
        }
        StringBuilder buffer = getBuffer();
        buffer.append(table.getFullyQualifiedName());
        Pivot pivot = table.getPivot();
        if (pivot != null) {
            pivot.accept(this);
        }
        Alias alias = table.getAlias();
        if (alias != null) {
            buffer.append(alias);
        }
    }
}
package com.mesalab.common.utils.sqlparser;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.Data;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.schema.Column;

import java.util.List;
import java.util.Set;


/**
 * @Classname ExpressionColumnCollectAdapter
 * @Date 2021/7/20 3:39 下午
 * @Author wWei
 */
@Data
public class ExpressionColumnCollectAdapter extends ExpressionVisitorAdapter {

    public Set<String> columns = Sets.newHashSet();

    @Override
    public void visit(Column column) {
        if (column != null) {
            this.columns.add(column.getColumnName());
        }
    }
}

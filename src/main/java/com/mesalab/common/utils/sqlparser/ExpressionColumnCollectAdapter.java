package com.mesalab.common.utils.sqlparser;

import com.google.common.collect.Sets;
import lombok.Data;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.schema.Column;

import java.util.Set;


/**
 * @Classname ExpressionColumnCollectAdapter
 * @Date 2021/7/20 3:39 下午
 * @Author wWei
 */
@Data
public class ExpressionColumnCollectAdapter<T> extends ExpressionVisitorAdapter<T> {

    public Set<String> columns = Sets.newHashSet();

    @Override
    public <S> T visit(Column column, S context) {
        if (column != null) {
            this.columns.add(column.getColumnName());
        }
        return null;
    }
}

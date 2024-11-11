package com.mesalab.common.utils.sqlparser;

import lombok.SneakyThrows;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.statement.select.*;


/**
 * TODO
 *
 * @Classname DerivedTableHelper
 * @Date 2024/11/5 10:07
 * @Author wWei
 */
public class DerivedTableHelper {

    public static void build(Select select) {
        select.accept(new SelectAdapter(), null);
    }

    private static class FromItemAdapter extends FromItemVisitorAdapter<Object> {

        @Override
        public Object visit(ParenthesedSelect parenthesedSelect, Object context) {
            if (parenthesedSelect.getAlias() == null) {
                Alias subQuery = new Alias("sub_query", true);
                parenthesedSelect.setAlias(subQuery);
            }
            Select select = parenthesedSelect.getSelect();
            select.accept(new SelectAdapter(), null);
            return null;
        }
    }

    private static class SelectAdapter extends SelectVisitorAdapter<Object> {
        @SneakyThrows
        @Override
        public Object visit(PlainSelect plainSelect, Object context) {
            FromItem fromItem = plainSelect.getFromItem();
            if (fromItem != null) {
                fromItem.accept(new FromItemAdapter(), null);
            }
            return null;
        }

        @Override
        public Object visit(SetOperationList setOpList, Object context) {
            for (Select select : setOpList.getSelects()) {
                if (select instanceof PlainSelect) {
                    select.accept(this, null);
                }
            }
            return null;
        }
    }
}

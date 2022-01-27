package com.mesalab.calcite;

import com.mesalab.calcite.storage.Storage;
import com.zdjizhi.calcite.DataContext;
import com.zdjizhi.calcite.linq4j.AbstractEnumerable;
import com.zdjizhi.calcite.linq4j.Enumerable;
import com.zdjizhi.calcite.linq4j.Enumerator;
import com.zdjizhi.calcite.rel.type.RelDataType;
import com.zdjizhi.calcite.rel.type.RelDataTypeFactory;
import com.zdjizhi.calcite.schema.*;
import com.zdjizhi.calcite.schema.impl.AbstractTable;
import com.zdjizhi.calcite.sql.type.SqlTypeUtil;


public class InMemoryTable extends AbstractTable implements ScannableTable {
    private String name;
    private Storage.DummyTable _table;
    private RelDataType dataType;

    InMemoryTable(String name) {
        this.name = name;
    }

    public InMemoryTable(String name, Storage.DummyTable it) {
        this.name = name;
        this._table = it;
    }


    @Override
    public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        if (dataType == null) {
            RelDataTypeFactory.FieldInfoBuilder fieldInfo = typeFactory.builder();
            for (Storage.DummyColumn column : this._table.getColumns()) {
                RelDataType sqlType = typeFactory.createJavaType(column.getJavaClass());
                sqlType = SqlTypeUtil.addCharsetAndCollation(sqlType, typeFactory);
                fieldInfo.add(column.getName(), sqlType);
            }
            this.dataType = typeFactory.createStructType(fieldInfo);
        }
        return this.dataType;
    }


    @Override
    public Enumerable<Object[]> scan(DataContext root) {
        return new AbstractEnumerable<Object[]>() {
            @Override
            public Enumerator<Object[]> enumerator() {
                return new Enumerator<Object[]>() {
                    private int cur = 0;
                    private int count = 0;

                    @Override
                    public Object[] current() {
                        Object[] data = _table.getData(cur);
                        return data;
                    }

                    @Override
                    public boolean moveNext() {
                        if (cur != 0 || count >= 1) {
                            ++cur;
                        }
                        if (count++ >= 1 && cur == 0) {
                            return false;
                        }
                        return cur < _table.getRowCount();
                    }

                    @Override
                    public void reset() {
                    }

                    @Override
                    public void close() {
                    }
                };
            }
        };
    }
}

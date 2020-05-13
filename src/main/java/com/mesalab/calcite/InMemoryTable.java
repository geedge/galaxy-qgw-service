package com.mesalab.calcite;

import com.mesalab.calcite.storage.Storage;
import org.apache.calcite.DataContext;
import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.*;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeUtil;


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
                        Object[] data = _table.getData(cur++);
                        return data;
                    }

                    @Override
                    public boolean moveNext() {
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

package com.mesalab.qgw.calcite.storage;

import com.zdjizhi.calcite.sql.type.SqlTypeName;

import java.util.*;

/**
 * 用于模拟数据库结构及数据
 */
public class Storage {

    private static final Hashtable<String, DummyTable> _bag = new Hashtable<>();

    public static Collection<DummyTable> getTables() {
        return _bag.values();
    }

    public static DummyTable getTable(String tableName) {
        return _bag.get(tableName);
    }

    public static class DummyTable {
        private String name;
        private List<DummyColumn> columns;
        private List<List<Object>> datas = new ArrayList<>();

        public DummyTable(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public List<DummyColumn> getColumns() {
            return columns;
        }

        public DummyTable addColumn(DummyColumn dc) {
            if (this.columns == null) {
                this.columns = new ArrayList<>();
            }
            this.columns.add(dc);
            return this;
        }

        public void setColumns(List<DummyColumn> columns) {
            this.columns = columns;
        }

        public Object[] getData(int index) {
            return this.datas.get(index).toArray();
        }

        public int getRowCount() {
            return this.datas.size();
        }

        public void addRow(Object... objects) {
            this.datas.add(Arrays.asList(objects));
        }


    }

    public static class DummyColumn {
        private String name;
        private String type;

        public DummyColumn(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Class getJavaClass() {
            return DataTypeMapping.getJavaClassByName(this.type);
        }

        public SqlTypeName getSqlTypeName() {
            return DataTypeMapping.getSqlTypeByName(this.type);
        }
    }

}

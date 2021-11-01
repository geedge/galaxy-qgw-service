package com.mesalab.calcite;

import com.mesalab.calcite.storage.Storage;
import com.zdjizhi.calcite.schema.Table;
import com.zdjizhi.calcite.schema.impl.AbstractSchema;

import java.util.*;

public class InMemorySchema extends AbstractSchema {

    private String dbName;
    private Map<String, Object> operand;

    public InMemorySchema(String name, Map<String, Object> operand) {
        this.operand = operand;
        this.dbName = name;
    }

    @Override
    public Map<String, Table> getTableMap() {

        Map<String, Table> tables = new HashMap<String, Table>();

        Storage.getTables().forEach(it -> {
            tables.put(it.getName(), new InMemoryTable(it.getName(), it));
        });

        return tables;
    }

}

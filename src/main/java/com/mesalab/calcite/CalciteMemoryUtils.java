package com.mesalab.calcite;

import com.google.common.base.Splitter;
import com.mesalab.calcite.storage.DataTypeMapping;
import com.mesalab.calcite.storage.Storage;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.jdbc.CalciteConnection;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;

@Slf4j
public class CalciteMemoryUtils {


    public static Map calciteQueryMemoryData(String tableName, List<Map<String, String>> meta, List<Map<String, Object>> data, String sql) throws Exception {
        Class.forName("org.apache.calcite.jdbc.Driver");
        Properties info = new Properties();
        info.setProperty("lex", "MYSQL_ANSI");
        String jsonModel = new File("").getCanonicalPath() + "/config/memorySchema.json";
        Connection connection = DriverManager.getConnection("jdbc:calcite:model=" + jsonModel, info);
        CalciteConnection calciteConn = connection.unwrap(CalciteConnection.class);
        ResultSet executeQueryResult = null;

        log.info("执行导入内存数据库data.size()为: " + data.size());
        //将数据存入缓存表
        Storage.DummyTable table = addDummyTableMeta(meta, tableName);
        table = addDummyTableRow(data, table);
        InMemoryTable inMemoryTable = new InMemoryTable(tableName, table);
        //将缓存表植入rootSchema
        calciteConn.getRootSchema().add(tableName, inMemoryTable);
        log.info("内存数据库 数据导入成功");

        java.sql.Statement st = connection.createStatement();
        executeQueryResult = st.executeQuery(sql);
        ResultSetMetaData metaData = executeQueryResult.getMetaData();
        int columnCount = metaData.getColumnCount();

        meta.clear();
        data.clear();
        HashMap<String, String> metaMap;
        for (int i = 1; i <= columnCount; i++) {
            metaMap = new HashMap<>();
            metaMap.put("name", metaData.getColumnLabel(i));
            metaMap.put("type", metaData.getColumnTypeName(i));
            meta.add(metaMap);
        }

        while (executeQueryResult.next()) {
            Map<String, Object> rowData = new HashMap(metaData.getColumnCount());
            for (int i = 1; i <= columnCount; i++) {
                rowData.put(metaData.getColumnLabel(i), executeQueryResult.getObject(i));
            }
            data.add(rowData);
        }

        HashMap result = new HashMap<String, Object>();
        result.put("meta", meta);
        result.put("data", data);
        return result;
    }

    /**
     * 给虚拟表插入数据
     *
     * @param data
     * @param table
     * @return
     */
    private static Storage.DummyTable addDummyTableRow(List<Map<String, Object>> data, Storage.DummyTable table) {
        List<Storage.DummyColumn> columns = table.getColumns();
        for (int i = 0; i < data.size(); i++) {
            List list = new ArrayList<>();
            for (int j = 0; j < columns.size(); j++) {
                list.add(null);
            }
            for (Map.Entry entry : data.get(i).entrySet()) {
                int countColumn = 0;
                Object key = entry.getKey();
                for (Storage.DummyColumn column : columns) {
                    if (column.getName().equals(String.valueOf(key))) {
                        Storage.DummyColumn dummyColumn = columns.get(countColumn);
                        String type = dummyColumn.getType();
                        if (DataTypeMapping.BIGINT.equals(type)) {
                            list.set(countColumn, Long.valueOf(String.valueOf(entry.getValue())));
                        } else {
                            list.set(countColumn, String.valueOf(entry.getValue()));
                        }
                    }
                    ++countColumn;
                }
            }
            //若可以最好直接以列的形式插入数据
            table.addRow(list.toArray());
        }
        return table;
    }

    /**
     * 生成虚拟表元数据
     *
     * @param meta
     * @param tableName
     * @return
     */
    private static Storage.DummyTable addDummyTableMeta(List<Map<String, String>> meta, String tableName) {
        Storage.DummyTable dummyTable = new Storage.DummyTable(tableName);
        for (Map<String, String> columnInfo : meta) {
            String originalName = columnInfo.get("name");
            String originalType = columnInfo.get("type");
            String dummyColumnType;
            if (Splitter.on(",").trimResults().omitEmptyStrings().splitToList(intUnits).contains(originalType)) {
                dummyColumnType = DataTypeMapping.BIGINT;
            } else if (Splitter.on(",").trimResults().omitEmptyStrings().splitToList(doubleUnits).contains(originalType)) {
                dummyColumnType = DataTypeMapping.DOUBLE;
            } else {
                dummyColumnType = DataTypeMapping.VARCHAR;
            }
            Storage.DummyColumn name = new Storage.DummyColumn(originalName, dummyColumnType);
            dummyTable.addColumn(name);
        }
        return dummyTable;
    }

    private static String intUnits = "Int8,Int16,Int32,Int64,UInt8,UInt16,UInt32,UInt64,bigint";
    private static String doubleUnits = "Float32,Float64,float,double";

}

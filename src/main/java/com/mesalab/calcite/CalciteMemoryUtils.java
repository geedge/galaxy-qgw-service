package com.mesalab.calcite;

import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.mesalab.calcite.storage.DataTypeMapping;
import com.mesalab.calcite.storage.Storage;
import com.mesalab.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.ScannableTable;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CalciteMemoryUtils {

    public static Map<String, Object> calciteQueryMemoryData(String tableName, List<Map<String, String>> meta,
                                                                            List<Map<String, Object>> data, String sql) {
        Map<String, Object> result = Maps.newHashMap();
        ResultSet resultSet = null;
        java.sql.Statement statement = null;
        Connection connection = null;
        try {
           Class.forName("org.apache.calcite.jdbc.Driver");
           Properties info = new Properties();
           info.setProperty("lex", "MYSQL_ANSI");
           String jsonModel = new File("").getCanonicalPath() + "/config/memorySchema.json";
           connection = DriverManager.getConnection("jdbc:calcite:model=" + jsonModel, info);
           CalciteConnection calciteConn = connection.unwrap(CalciteConnection.class);


           log.info("Calcite: 加载数据的数量: " + data.size());
           //将数据存入缓存表
           Storage.DummyTable table = addDummyTableMeta(meta, tableName);
           table = addDummyTableRow(data, table);
           InMemoryTable inMemoryTable = new InMemoryTable(tableName, table);

           //将缓存表植入rootSchema
           calciteConn.getRootSchema().add(tableName, inMemoryTable);
           statement = connection.createStatement();
           resultSet = statement.executeQuery(sql);
           ResultSetMetaData metaData = resultSet.getMetaData();
           meta.clear();
           data.clear();

           for (int i = 1; i <= metaData.getColumnCount(); i++) {
               Map<String, String> metaMap = Maps.newLinkedHashMap();
               metaMap.put("name", metaData.getColumnLabel(i));
               metaMap.put("type", metaData.getColumnTypeName(i));
               meta.add(metaMap);
           }

           while (resultSet.next()) {
               Map<String, Object> rowData = Maps.newLinkedHashMap();
               for (int i = 1; i <= metaData.getColumnCount(); i++) {
                   rowData.put(metaData.getColumnLabel(i), resultSet.getObject(i));
               }
               data.add(rowData);
           }

           result.put("meta", meta);
           result.put("data", data);

       } catch (Exception e) {
           e.printStackTrace();
           throw new BusinessException("Engine execution SQL Error.", e);

       } finally {
           if (resultSet != null) {
               try {
                   resultSet.close();
               } catch (SQLException throwables) {
                   throwables.printStackTrace();
               }
           }

            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }
            }

           if (connection != null) {
               try {
                   connection.close();
               } catch (SQLException throwables) {
                   throwables.printStackTrace();
               }
           }
       }


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

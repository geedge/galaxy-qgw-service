package com.mesalab.calcite;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.calcite.storage.DataTypeMapping;
import com.mesalab.calcite.storage.Storage;
import com.mesalab.common.exception.BusinessException;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.zdjizhi.utils.StringUtil.setDefaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

@Slf4j
public class CalciteMemoryUtils {

    public static Map<String, Object> executeMemoryQuery(String tableName, List<Map<String, String>> meta,
                                                                            List<Map<String, Object>> data, String sql) {
        Map<String, Object> result = Maps.newHashMap();
        if (meta.isEmpty() || data.isEmpty()) {
            data.clear();
            meta.clear();
            result.put("meta", meta);
            result.put("data", data);
            return result;
        }
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

           log.debug("Calcite: 加载数据的数量: " + data.size());
           //将数据存入缓存表
           Storage.DummyTable table = buildDummyTable(meta, tableName);
           table = loadData(table, data);
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
           throw new BusinessException("Engine execution SQL Error - ", e);

       } finally {
           if (resultSet != null) {
               try {
                   resultSet.close();
               } catch (SQLException sqlException) {
                   sqlException.printStackTrace();
               }
           }

            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException sqlException) {
                    sqlException.printStackTrace();
                }
            }

           if (connection != null) {
               try {
                   connection.close();
               } catch (SQLException sqlException) {
                   sqlException.printStackTrace();
               }
           }
       }


        return result;
    }


    /**
     * 数据加载 ： MAP To List
     * @param table 转换为table
     * @param data 原始结果集合
     * @return
     */
    private static Storage.DummyTable loadData(Storage.DummyTable table, List<Map<String, Object>> data) {
        List<Storage.DummyColumn> columns = table.getColumns();
        data.stream().forEach(item -> {
            List list = Lists.newArrayList();
            for (Storage.DummyColumn column : columns) {
                if (DataTypeMapping.BIGINT.equals(column.getType())) {
                    list.add(Long.valueOf(setDefaultIfEmpty(item.get(column.getName()), "0").toString()));
                } else if (DataTypeMapping.DOUBLE.equals(column.getType())) {
                    list.add(Double.valueOf(setDefaultIfEmpty(item.get(column.getName()), "0").toString()));
                } else {
                    list.add(setDefaultIfEmpty(item.get(column.getName()), "").toString());
                }


            }

            table.addRow(list.toArray());

        });
        return table;
    }

    /**
     * 生成虚拟表
     * @param meta schema 表结构字段
     * @param tableName 表名
     * @return
     */
    private static Storage.DummyTable buildDummyTable(List<Map<String, String>> meta, String tableName) {
        Storage.DummyTable dummyTable = new Storage.DummyTable(tableName);
        for (Map<String, String> column : meta) {
            String fieldName = column.get("name");
            String fieldType = column.get("type");
            String dummyColumnType;
            if (Splitter.on(",").trimResults().omitEmptyStrings().splitToList(intUnits).contains(fieldType)) {
                dummyColumnType = DataTypeMapping.BIGINT;
            } else if (Splitter.on(",").trimResults().omitEmptyStrings().splitToList(doubleUnits).contains(fieldType)) {
                dummyColumnType = DataTypeMapping.DOUBLE;
            } else {
                dummyColumnType = DataTypeMapping.VARCHAR;
            }
            Storage.DummyColumn name = new Storage.DummyColumn(fieldName, dummyColumnType);
            dummyTable.addColumn(name);
        }
        return dummyTable;
    }

    private static String intUnits = "Int8,Int16,Int32,Int64,UInt8,UInt16,UInt32,UInt64,bigint";
    private static String doubleUnits = "Float32,Float64,float,double";

}

package com.mesalab.qgw.calcite;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.qgw.calcite.storage.DataTypeMapping;
import com.mesalab.qgw.calcite.storage.Storage;
import com.mesalab.common.exception.BusinessException;
import com.zdjizhi.calcite.jdbc.CalciteConnection;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;

import static com.zdjizhi.utils.StringUtil.setDefaultIfEmpty;


public class CalciteMemoryUtils {
    private static final Log log = LogFactory.get();
    private static final String URL_PREFIX  = "jdbc:calcite:model=";
    private static final String URL_SUFFIX  = "config" + File.separator + "memorySchema.json";


    static {
        try {
            Class.forName("com.zdjizhi.calcite.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            log.error("Calcite load driver error : {}", e);
        }
    }

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
        PreparedStatement statement = null;
        CalciteConnection connection = null;
        try {
           connection = getConnection().unwrap(CalciteConnection.class);
           Storage.DummyTable table = buildDummyTable(meta, tableName);
           table = loadData(table, data);
           InMemoryTable inMemoryTable = new InMemoryTable(tableName, table);

           //将缓存表植入rootSchema
           connection.getRootSchema().add(tableName, inMemoryTable);
           statement = connection.prepareStatement(sql);
           resultSet = statement.executeQuery();
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

       } catch (SQLException | IOException e ) {
            throw new BusinessException("Engine create connection Error - ", e);
        } finally {
            close(resultSet, statement, connection);
        }



        return result;
    }


    private static  Connection getConnection() throws IOException, SQLException {
        String url = URL_PREFIX + new File("").getCanonicalPath() + File.separator + URL_SUFFIX;
        Properties info = new Properties();
        info.setProperty("lex", "MYSQL_ANSI");
        Connection dbConnection = DriverManager.getConnection(url, info);
        return dbConnection;
    }





    private static void close(ResultSet rs, PreparedStatement pst, Connection conn) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                log.error("ResultSet close error : " , e.getMessage());
            }

        }
        if (pst != null) {
            try {
                pst.close();
            } catch (SQLException e) {
                log.error("Statement close error : " , e.getMessage());
            }

        }
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                log.error("Connection close error : " , e.getMessage());
            }

        }
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

    private static final String intUnits = "Int8,Int16,Int32,Int64,UInt8,UInt16,UInt32,UInt64,bigint,int,long";
    private static final String doubleUnits = "Float32,Float64,float,double";

}

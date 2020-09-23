package com.mesalab.common.utils;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.model.api.HBaseHttpSource;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.Map;

/**
 * @author wangwei
 * @version 1.0
 * @date 2020/6/5 2:04 下午
 */
@Slf4j
public class ReportCacheUtils {

    private static HBaseHttpSource hBaseHttpSource = (HBaseHttpSource) SpringContextUtil.getBean("hBaseHttpSource");
    private static Configuration hbaseConf;

    static {
        hbaseConf = HBaseConfiguration.create();
        hbaseConf.set("hbase.zookeeper.quorum", hBaseHttpSource.getZookeeperQuorum());
        hbaseConf.set("hbase.zookeeper.property.clientPort", hBaseHttpSource.getZookeeperPropertyClientPort());
        hbaseConf.set("hbase.rpc.timeout", hBaseHttpSource.getRpcTimeout());
        hbaseConf.set("hbase.client.retries.number", hBaseHttpSource.getClientRetriesNumber());
    }

    public static BaseResult<Object> get(String id, String query) {
        Result result = query(id, query);
        byte[] value = result.getValue(Bytes.toBytes(hBaseHttpSource.getColumnFamily()), Bytes.toBytes(hBaseHttpSource.getColumnName()));
        Map map = (Map) JsonMapper.fromJsonString(Bytes.toString(value), Map.class);
        BaseResult baseResult;
        if (StringUtil.isEmpty(map)) {
            baseResult = BaseResultGenerator.failure("Results not exist");
        } else {
            baseResult = BaseResultGenerator.generate(Integer.parseInt(String.valueOf(map.get("status"))), ResultCodeEnum.EXECUTE_SUCCESS.getCode(), String.valueOf(map.get("message")),
                    map.get("data"), map.get("meta"), (Map<String, Object>) (map.get("statistics")), QueryFormatEnum.JSON.getValue());
        }
        return baseResult;
    }

    private static Result query(String id, String query) {
        Result result;
        Table table = null;
        Connection connection = null;
        try {
            connection = createConnection();
            table = connection.getTable(TableName.valueOf((hBaseHttpSource.getDbName() + ":" + hBaseHttpSource.getTableName())));
            Get get = new Get(Bytes.toBytes(getQueryId(id, query)));
            get.addColumn(Bytes.toBytes(hBaseHttpSource.getColumnFamily()), Bytes.toBytes(hBaseHttpSource.getColumnName()));
            result = table.get(get);
        } catch (Exception e) {
            log.error("get hbase error: ", e);
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "Get Hbase Error：", e);
        } finally {
            closeTable(table);
            closeConnection(connection);
        }
        return result;
    }

    /**
     * 通过SQL生成查询唯一kEY
     *
     * @param sql
     * @param resultId
     * @return
     */
    public static String getQueryId(String resultId, String sql) {
        return DigestUtils.md5Hex(resultId + sql.trim());
    }

    public static Connection createConnection() {
        try {
            Connection connection = ConnectionFactory.createConnection(hbaseConf);
            return connection;
        } catch (IOException e) {
            log.error("hbase connected error: ", e);
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.SQL_SYNTAX_ERROR.getCode(), "Hbase Connected Error：", e);
        }
    }

    private static void closeConnection(Connection connection) {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (IOException e) {
            log.error("hbase connection close error: ", e);
        }
    }

    private static void closeTable(Table table) {
        try {
            if (table != null) {
                table.close();
            }
        } catch (IOException e) {
            log.error("hbase table close error: ", e);
        }
    }
}

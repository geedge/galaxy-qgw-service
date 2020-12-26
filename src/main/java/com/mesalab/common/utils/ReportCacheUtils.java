package com.mesalab.common.utils;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.model.api.HBaseAPISource;
import com.zdjizhi.utils.StringUtil;
import org.apache.commons.codec.digest.DigestUtils;
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
public class ReportCacheUtils {
    private static final Log log = LogFactory.get();
    private static HBaseAPISource hBaseAPISource = (HBaseAPISource) SpringContextUtil.getBean("hBaseAPISource");
    private static Connection hbaseConnection = (Connection) SpringContextUtil.getBean("hbaseConnection");

    public static BaseResult<Object> get(String id, String query) {
        Result result = query(id, query);
        byte[] value = result.getValue(Bytes.toBytes(hBaseAPISource.getColumnFamily()), Bytes.toBytes(hBaseAPISource.getColumnName()));
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
        Table table = null;
        Result result;
        try {
            table = hbaseConnection.getTable(TableName.valueOf((hBaseAPISource.getDbName() + ":" + hBaseAPISource.getTableName())));
            Get get = new Get(Bytes.toBytes(getQueryId(id, query)));
            get.addColumn(Bytes.toBytes(hBaseAPISource.getColumnFamily()), Bytes.toBytes(hBaseAPISource.getColumnName()));
            result = table.get(get);
        } catch (Exception e) {
            log.error("Query Hbase error: {}", e);
            throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOW_ERROR.getCode(), "Get Hbase Error: ", e);
        } finally {
            closeTable(table);
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


    private static void closeTable(Table table) {
        try {
            if (table != null) {
                table.close();
            }
        } catch (IOException e) {
            log.error("Table close error: ", e);
        }
    }
}

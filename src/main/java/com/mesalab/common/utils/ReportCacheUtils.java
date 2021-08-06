package com.mesalab.common.utils;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.qgw.model.api.HBaseAPISource;
import com.mesalab.qgw.service.SystemService;
import com.zdjizhi.utils.StringUtil;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Map;

/**
 * @author wangwei
 * @version 1.0
 * @date 2020/6/5 2:04 下午
 */
@Component
public class ReportCacheUtils {
    private static final Log log = LogFactory.get();
    @Autowired
    private HBaseAPISource hBaseAPISourceCache;
    @Autowired
    private Connection hbaseConnectionCache;
    @Autowired
    private SystemService systemServiceCache;

    private static HBaseAPISource hBaseAPISource;
    private static Connection hbaseConnection;
    private static SystemService systemService;

    @PostConstruct
    public void init() {
        hBaseAPISource = this.hBaseAPISourceCache;
        hbaseConnection = this.hbaseConnectionCache;
        systemService = this.systemServiceCache;
    }


    public static BaseResult<Object> get (String resultId, String query) {
        BaseResult baseResult;
        Result result = query(resultId, query);
        byte[] value = result.getValue(Bytes.toBytes(hBaseAPISource.getColumnFamily()), Bytes.toBytes(hBaseAPISource.getColumnName()));
        Map map = (Map) JsonMapper.fromJsonString(Bytes.toString(value), Map.class);

        if (StringUtil.isEmpty(map)) {
            log.warn("The report result [resultId:{}] is unavailable. It Has been deleted or does not exist.", resultId);
            baseResult = BaseResultGenerator.failure(ResultStatusEnum.NOT_FOUND.getCode(), "The report result [resultId:" + resultId + "] is unavailable. It Has been deleted or does not exist.");
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
            Get get = new Get(Bytes.toBytes(SQLHelper.getQueryId(id, query)));
            get.addColumn(Bytes.toBytes(hBaseAPISource.getColumnFamily()), Bytes.toBytes(hBaseAPISource.getColumnName()));
            result = table.get(get);

        } catch (IOException e) {
            log.error("Query Hbase error: {}", e);
            throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(), ResultCodeEnum.UNKNOW_ERROR.getCode(), "Get Hbase Error: ", e);

        } finally {
            closeTable(table);
        }
        return result;
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

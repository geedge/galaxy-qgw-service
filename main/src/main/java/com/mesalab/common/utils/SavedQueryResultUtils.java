package com.mesalab.common.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.qgw.model.basic.SQLQueryContext;
import com.mesalab.qgw.model.basic.HBaseAPISource;
import com.mesalab.qgw.service.SQLSyncQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @author wangwei
 * @version 1.0
 * @date 2020/6/5 2:04 下午
 */
@Component
public class SavedQueryResultUtils {
    private static final Log log = LogFactory.get();

    private static HBaseAPISource hBaseAPISource;
    private static SQLSyncQueryService sqlSyncQueryService;

    public static BaseResult<Object> getByRowKey(List<String> rowKeys) {
        List<Map<String, Object>> result = query(rowKeys);
        log.info("The saved query job result [jobIds:{}] size is {}.", rowKeys, result.size());
        return BaseResultGenerator.success(result);
    }

    private static List<Map<String, Object>> query(List<String> rowKeys) {
        SQLQueryContext param = new SQLQueryContext();
        StringBuffer sb = new StringBuffer();
        sb.append("select ROWKEY, ").append(hBaseAPISource.getColumnName());
        sb.append(" from ").append(hBaseAPISource.getDbName()).append(".").append(hBaseAPISource.getTableName());
        sb.append(" where ").append("ROWKEY IN ('").append(StrUtil.join("', '", rowKeys)).append("')");
        param.setRawSQL(sb.toString());
        BaseResult baseResult = sqlSyncQueryService.executeQuery(param);
        return (List<Map<String, Object>>) baseResult.getData();
    }

    @Autowired
    public void setJobExecuteService(HBaseAPISource hBaseAPISource) {
        SavedQueryResultUtils.hBaseAPISource = hBaseAPISource;
    }

    @Autowired
    public void sqlSyncQueryService(SQLSyncQueryService sqlSyncQueryService) {
        SavedQueryResultUtils.sqlSyncQueryService = sqlSyncQueryService;
    }
}

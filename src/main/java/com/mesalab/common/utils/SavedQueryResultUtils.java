package com.mesalab.common.utils;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.qgw.model.basic.QueryProfile;
import com.mesalab.qgw.model.basic.HBaseAPISource;
import com.mesalab.qgw.service.impl.QueryServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
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
    @Autowired
    private HBaseAPISource hBaseAPISourceCache;

    private static HBaseAPISource hBaseAPISource;

    @PostConstruct
    public void init() {
        hBaseAPISource = this.hBaseAPISourceCache;
    }


    public static BaseResult<Object> getByRowKey(List<String> rowKeys) {
        BaseResult baseResult;
        List<Map<String, Object>> result = query(rowKeys);
        if (CollectionUtil.isEmpty(result)) {
            log.warn("The saved query job result [jobId:{}] is unavailable. It Has been deleted or does not exist.", rowKeys);
            baseResult = BaseResultGenerator.failure(ResultStatusEnum.NOT_FOUND.getCode(), "The saved query job result [jobId:" + rowKeys + "] is unavailable. It Has been deleted or does not exist.");
        } else {
            baseResult = BaseResultGenerator.success(result);
        }
        return baseResult;
    }

    private static List<Map<String, Object>> query(List<String> rowKeys) {
        QueryServiceImpl queryService = new QueryServiceImpl();
        QueryProfile param = new QueryProfile();
        StringBuffer sb = new StringBuffer();
        sb.append("select ROWKEY, ").append(hBaseAPISource.getColumnName());
        sb.append(" from ").append(hBaseAPISource.getDbName()).append(".").append(hBaseAPISource.getTableName());
        sb.append(" where ").append("ROWKEY IN ('").append(StrUtil.join("', '", rowKeys)).append("')");
        param.setQuery(sb.toString());
        BaseResult baseResult = queryService.executeQuery(param);
        return (List<Map<String, Object>>) baseResult.getData();
    }
}

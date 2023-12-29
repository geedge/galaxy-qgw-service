package com.mesalab.common.utils;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.enums.OutputMode;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.qgw.model.basic.HBaseAPISource;
import com.mesalab.qgw.model.basic.QueryContext;
import com.mesalab.qgw.service.impl.QueryServiceImpl;
import com.geedgenetworks.utils.StringUtil;
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
public class ReportCacheUtils {
    private static final Log log = LogFactory.get();
    @Autowired
    private HBaseAPISource hBaseAPISourceCache;

    private static HBaseAPISource hBaseAPISource;

    @PostConstruct
    public void init() {
        hBaseAPISource = this.hBaseAPISourceCache;
    }


    public static BaseResult<Object> getByRowKey(String rowKey) {
        BaseResult baseResult;
        Map<String, Object> result = query(rowKey);
        Map map = JSON.parseObject((String)result.get("result"), Map.class);
        if (StringUtil.isEmpty(map)) {
            log.warn("The saved query job result [jobId:{}] is unavailable. It Has been deleted or does not exist.", rowKey);
            baseResult = BaseResultGenerator.failure(HttpStatusCodeEnum.NOT_FOUND.getCode(), "The saved query job result [jobId:" + rowKey + "] is unavailable. It Has been deleted or does not exist.");
        } else {
            baseResult = BaseResultGenerator.generate(Integer.parseInt(String.valueOf(map.get("status"))), ResultCodeEnum.SUCCESS.getCode(), String.valueOf(map.get("message")),
                    map.get("data"), map.get("meta"), (Map<String, Object>) (map.get("statistics")), OutputMode.JSON.getValue());
        }
        return baseResult;
    }

    private static Map<String, Object> query(String rowKey) {
        QueryServiceImpl queryService = new QueryServiceImpl();
        QueryContext param = new QueryContext();
        StringBuffer sb = new StringBuffer();
        sb.append("select ").append(hBaseAPISource.getColumnName());
        sb.append(" from ").append(hBaseAPISource.getDbName()).append(".").append(hBaseAPISource.getTableName());
        sb.append(" where ").append("ROWKEY=").append("\'" + rowKey + "\'");
        param.setQuery(sb.toString());
        BaseResult result = queryService.executeQuery(param);
        Map<String, Object> resultMap = Maps.newHashMap();
        if (StringUtil.isNotEmpty(result.getData())) {
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) result.getData();
            for (Map data : dataList) {
                resultMap.putAll(data);
            }
        } else {
            return resultMap;
        }
        return resultMap;
    }
}

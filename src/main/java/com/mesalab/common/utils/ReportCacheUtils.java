package com.mesalab.common.utils;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.HBaseAPISource;
import com.mesalab.qgw.service.impl.ApiServiceImpl;
import com.zdjizhi.utils.StringUtil;
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


    public static BaseResult<Object> get(String resultId, String query) {
        BaseResult baseResult;
        Map<String, Object> result = query(resultId, query);
        Map map = (Map) JsonMapper.fromJsonString((String) result.get("result"), Map.class);
        if (StringUtil.isEmpty(map)) {
            log.warn("The report result [resultId:{}] is unavailable. It Has been deleted or does not exist.", resultId);
            baseResult = BaseResultGenerator.failure(ResultStatusEnum.NOT_FOUND.getCode(), "The report result [resultId:" + resultId + "] is unavailable. It Has been deleted or does not exist.");
        } else {
            baseResult = BaseResultGenerator.generate(Integer.parseInt(String.valueOf(map.get("status"))), ResultCodeEnum.SUCCESS.getCode(), String.valueOf(map.get("message")),
                    map.get("data"), map.get("meta"), (Map<String, Object>) (map.get("statistics")), QueryFormatEnum.JSON.getValue());
        }
        return baseResult;
    }

    private static Map<String, Object> query(String id, String query) {
        ApiServiceImpl apiService = new ApiServiceImpl();
        ApiParam param = new ApiParam();
        StringBuffer sb = new StringBuffer();
        String rowKey = DigestUtil.md5Hex(id + query.trim());
        sb.append("select ").append(hBaseAPISource.getColumnName());
        sb.append(" from ").append(hBaseAPISource.getDbName()).append(".").append(hBaseAPISource.getTableName());
        sb.append(" where ").append("ROWKEY=").append("\'" + rowKey + "\'");
        param.setQuery(sb.toString());
        BaseResult result = apiService.executeQuery(param);
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

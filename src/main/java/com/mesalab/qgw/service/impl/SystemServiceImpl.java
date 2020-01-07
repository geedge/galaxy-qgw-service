package com.mesalab.qgw.service.impl;

import com.mesalab.common.base.BaseResult;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.service.IAPIService;
import com.mesalab.qgw.service.SystemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SystemServiceImpl implements SystemService {

    @Autowired
    IAPIService apiService;

    @Override
    public BaseResult getStorageQuota() {

        //封装sql进行查询：Analytic Logs、Files、Traffic Logs
        ApiParam param = new ApiParam();
        param.setQuery("(select log_type, used_size, max_size, TIME_FORMAT(MILLIS_TO_TIMESTAMP(1000 * first_storage),'YYYY-MM-dd') as first_storage from sys_storage_log where log_type = 'Analytic Logs' ORDER BY __time desc limit 1)\n" +
                "UNION ALL\n" +
                "(select log_type, used_size, max_size, TIME_FORMAT(MILLIS_TO_TIMESTAMP(1000 * first_storage),'YYYY-MM-dd') as first_storage from sys_storage_log where log_type = 'Files' ORDER BY __time desc limit 1)\n" +
                "UNION ALL\n" +
                "(select log_type, used_size, max_size,TIME_FORMAT(MILLIS_TO_TIMESTAMP(1000 * first_storage),'YYYY-MM-dd') as first_storage from sys_storage_log where log_type = 'Traffic Logs' ORDER BY __time desc limit 1)");
        BaseResult result = apiService.executeQuery(param);
        return result;
    }

    @Override
    public BaseResult dailyTrendOfStorage(String start_time, String end_time) {
        ApiParam param = new ApiParam();
        String sql  = String.format("select TIME_FORMAT(__time,'YYYY-MM-dd') as stat_time,log_type as type, sum(aggregate_size) as used_size from sys_storage_log where __time > '%s' and __time <= '%s' group by TIME_FORMAT(__time,'YYYY-MM-dd'), log_type", start_time, end_time);
        param.setQuery(sql);
        return apiService.executeQuery(param);
    }
}

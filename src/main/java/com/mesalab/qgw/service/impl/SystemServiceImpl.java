package com.mesalab.qgw.service.impl;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.BaseResultEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.service.IAPIService;
import com.mesalab.qgw.service.SystemService;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
public class SystemServiceImpl implements SystemService {

    @Autowired
    IAPIService apiService;

    @Override
    public BaseResult getStorageQuota() {

        //封装sql进行查询：Analytic Logs、Files、Traffic Logs
        ApiParam param = new ApiParam();
        param.setQuery("(select log_type, used_size, max_size, TIME_FORMAT(MILLIS_TO_TIMESTAMP(1000 * first_storage),'YYYY-MM-dd') as first_storage from sys_storage_log where log_type = 'Report and Metrics' ORDER BY __time desc limit 1)\n" +
                "UNION ALL\n" +
                "(select log_type, used_size, max_size, TIME_FORMAT(MILLIS_TO_TIMESTAMP(1000 * first_storage),'YYYY-MM-dd') as first_storage from sys_storage_log where log_type = 'Files' ORDER BY __time desc limit 1)\n" +
                "UNION ALL\n" +
                "(select log_type, used_size, max_size,TIME_FORMAT(MILLIS_TO_TIMESTAMP(1000 * first_storage),'YYYY-MM-dd') as first_storage from sys_storage_log where log_type = 'Traffic Logs' ORDER BY __time desc limit 1)");
        BaseResult result = apiService.executeQuery(param);
        return result;
    }

    @Override
    public BaseResult dailyTrendOfStorage(String searchStartTime, String searchEndTime) {

        Date currentDate = DateUtils.convertStringToDate(DateUtils.getCurrentDate(), DateUtils.YYYY_MM_DD);

        if (StringUtil.isBlank(searchStartTime) && StringUtil.isBlank(searchEndTime)) {
            searchStartTime = DateUtils.getFormatDate(DateUtils.getSomeDate(currentDate, -6), DateUtils.YYYY_MM_DD_HH24_MM_SS);
            searchEndTime = DateUtils.getFormatDate(DateUtils.getSomeDate(currentDate, 1), DateUtils.YYYY_MM_DD_HH24_MM_SS);
        }

        if (StringUtil.isNotBlank(searchStartTime) && StringUtil.isNotBlank(searchEndTime)) {
            ApiParam param = new ApiParam();
            String sql  = String.format("select TIME_FORMAT(__time,'YYYY-MM-dd') as stat_time,log_type as type, sum(aggregate_size) as used_size from sys_storage_log where __time >= '%s' and __time < '%s' group by TIME_FORMAT(__time,'YYYY-MM-dd'), log_type", searchStartTime, searchEndTime);
            param.setQuery(sql);
            return apiService.executeQuery(param);
        } else {
            throw  new BusinessException(BaseResultEnum.FAIL.getCode(), "参数校验：start_time 与 end_time 需同时填写", null);
        }




    }
}

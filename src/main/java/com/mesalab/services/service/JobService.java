package com.mesalab.services.service;
import com.mesalab.common.base.BaseResult;

import java.util.HashMap;

/**
 * TODO
 *
 * @Classname JobService
 * @Date 2021/11/24 5:11 下午
 * @Author wWei
 */
public interface JobService {
    /**
     * Desc: 获取jobId
     *
     * @param map
     * @return {@link Object}
     * @created by wWei
     * @date 2021/11/24 5:15 下午
     */
    BaseResult commitRealTimeJob(HashMap<String, Object> map);

    /**
     * Desc: 获取FieldDiscovery结果
     *
     * @param jobId
     * @return {@link Object}
     * @created by wWei
     * @date 2021/11/24 5:40 下午
     */
    BaseResult getResultFieldDiscovery(String jobId);

    /**
     * Desc: 离线任务
     *
     * @param reqBody
     * @return {@link Object}
     * @created by wWei
     * @date 2022/3/22 2:57 下午
     */
    BaseResult commitSavedQuery(HashMap<String, Object> reqBody);

    /**
     * Desc: 获取离线任务状态
     *
     * @param jobId
     * @return {@link Object}
     * @created by wWei
     * @date 2022/3/22 5:04 下午
     */
    BaseResult getReportStatus(String jobId);

    /**
     * Desc: 获取离线任务结果
     *
     * @param jobId
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2022/3/24 3:39 下午
     */
    BaseResult getResultReport(String jobId);
}

package com.mesalab.services.service;
import com.mesalab.common.entity.BaseResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TODO
 *
 * @Classname JobService
 * @Date 2021/11/24 5:11 下午
 * @Author wWei
 */
public interface JobService {
    /**
     * Desc: 提交实时任务
     *
     * @param map
     * @return {@link Object}
     * @created by wWei
     * @date 2021/11/24 5:15 下午
     */
    BaseResult commitAdHocQuery(HashMap<String, Object> map);

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
     * Desc: 获取Statistics结果
     *
     * @param jobId
     * @return {@link Object}
     * @created by wWei
     * @date 2021/11/24 5:40 下午
     */
    BaseResult longTermResult(String jobId);


    /**
     * Desc: 提交离线任务
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
    BaseResult getSavedQueryStatus(String jobId);

    /**
     * Desc: 获取Report结果
     *
     * @param jobId
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2022/3/24 3:39 下午
     */
    BaseResult getSavedQueryResult(String jobId);

    /**
     * Desc: 取消Report任务
     *
     * @param jobId
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2022/3/28 5:29 下午
     */
    BaseResult cancelSavedQuery(String jobId);

    /**
     * Desc: 批量获取 saved query job状态
     *
     * @param ids
     * @return
     * @created by wWei
     * @date 2023/11/29 10:04
     */
    List<Map<String, Object>> batchSavedQueryStatus(List<String> ids);

    /**
     * Desc: 批量获取 saved query job 结果
     *
     * @param ids
     * @return
     * @created by wWei
     * @date 2023/11/29 10:04
     */
    List<Map<String, Object>> batchSavedQueryResult(List<String> ids);
}

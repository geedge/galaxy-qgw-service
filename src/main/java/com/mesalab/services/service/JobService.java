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
    BaseResult getJobId(HashMap<String, Object> map);

    /**
     * Desc: 获取FieldDiscovery结果
     *
     * @param jobId
     * @return {@link Object}
     * @created by wWei
     * @date 2021/11/24 5:40 下午
     */
    BaseResult getResult(String jobId);
}

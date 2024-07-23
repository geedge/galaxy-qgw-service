package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.basic.DSLQueryRequestParam;
import org.springframework.core.env.Environment;

/**
 * TODO
 *
 * @Classname customizedStatisticsService
 * @Date 2024/7/12 13:40
 * @Author wWei
 */
public interface CustomizedStatisticsService {

    BaseResult run(DSLQueryRequestParam param);

    void validate(DSLQueryRequestParam param);

}

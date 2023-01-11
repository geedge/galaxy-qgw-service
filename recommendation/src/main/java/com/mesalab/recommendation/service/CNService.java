package com.mesalab.recommendation.service;

import com.mesalab.common.entity.DSLObject;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.exception.BusinessException;

/**
 * @Date: 2020-07-20 10:02
 * @Author : wWei
 * @ClassName : CNService
 * @Description : 违规接入行为查询
 */
public interface CNService {

    /**
     * 自建共享网络类
     *
     * @param dslObject
     * @return
     * @throws BusinessException
     */
    BaseResult shareNetQuery(DSLObject dslObject) throws BusinessException;


    /**
     * 公共使用查询:
     * 违规app类、隧道协议类、远程桌面控制类、Key外借
     *
     * @param dslObject
     * @return BaseResult
     * @throws BusinessException
     */
    BaseResult bannedAppQuery(DSLObject dslObject) throws BusinessException;

}

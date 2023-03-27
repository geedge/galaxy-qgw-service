package com.mesalab.cn.service;

import com.mesalab.cn.entity.pojo.DSLObject;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.exception.BusinessException;

/**
 * @description: 查询Arangodb
 * 1.查询条件 app_id ,domain, client_ip ,server_ip
 * 深度 1..1 ，1..2
 * 排序 desc 默认, acs
 * limit 10，最大10
 * 2.查询表  R_USE_APP2DOMAIN，R_USE_DOMAIN2IP，R_VISIT_IP2IP
 * 3.分三次查询，每次限制10条，按时间排序，默认降序
 * @create: 2021-07-01
 **/
public interface EntityDetailService {


    BaseResult entityDetailQuery(DSLObject dslObject) throws BusinessException;

}

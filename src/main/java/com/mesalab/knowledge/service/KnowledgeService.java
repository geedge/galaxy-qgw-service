package com.mesalab.knowledge.service;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.knowledge.entity.DSLObject;
import com.mesalab.knowledge.enums.DataSourceRouteEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @description: for IP learning
 * @author: zhq
 * @create: 2020-07-06
 **/
@Slf4j
@Service
public class KnowledgeService extends BaseService{


	public BaseResult query(DSLObject dSLObject) {
		DSLObject.DSLQuery dslQeruy = dSLObject.getQuery();
		//1.登录
		arangoConfig.setJwt(getJwt());
		//2.查询
		Object result = DataSourceRouteEnum.match(dslQeruy.getDataSource(), null).getRouter().query(dslQeruy, arangoConfig);

		return BaseResultGenerator.success(result);

	}



}

package com.mesalab.knowledge.service;

import com.mesalab.knowledge.entity.DSLObject;
import com.mesalab.knowledge.graph.DatasourceRouteEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mesalab.knowledge.common.config.ArangoConfig;

/**
 * @description: for IP learning
 * @author: zhq
 * @create: 2020-07-06
 **/
@Service
public class KnowledgeService extends BaseService{

	@Autowired
	ArangoConfig arangoConfig;


	public Object query(DSLObject.DSLQuery dslQeruy) {
		//1.登录
		arangoConfig.setJwt(getJwt());
		//2.查询
		DatasourceRouteEnum.match(dslQeruy.getDatasource(), null).getRouter().query(dslQeruy, arangoConfig);
		//3.过滤
		return null;

	}



}

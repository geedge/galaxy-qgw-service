package com.mesalab.services.service.impl;


import cn.hutool.core.util.ObjectUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.services.common.dsl.ComDSLObject;
import com.mesalab.services.common.dsl.ComDSLParse;
import com.mesalab.services.common.enums.EntityQueryType;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.service.RelationService;
import com.zdjizhi.utils.DateUtils;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class RelationServiceImpl implements RelationService , EnvironmentAware {


    @Value("${arango.maxrows}")
    private String defLimit;//查询最大条数
    @Autowired
    private ApiService apiService;
    private Environment env;

    private static final Log log = LogFactory.get();

    @Override
    public BaseResult getRealRelation(String param, ComDSLObject comDSLObject) {
        BaseResult baseResult;
        if (param.equals(EntityQueryType.SUBSCRIBERIDPOOL.getType())) {
            baseResult = formatResult(apiService.executeQuery(queryBuild(comDSLObject.getQuery())));
        } else {
            baseResult = BaseResultGenerator.failure(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "No match queryType");
        }
        if (!baseResult.isSuccess()) {
            throw new BusinessException(baseResult.getStatus(), baseResult.getCode(), baseResult.getMessage(), null);
        }
        return baseResult;
    }

    private ApiParam queryBuild(ComDSLObject.Query query) {
        String fieldWhere = ComDSLParse.parseMath(query, "ROWKEY");
        String timeWhere = ComDSLParse.parseInterval(query, "last_update_time", true);
        String sql = String.format(env.getProperty("RELATION_SUBSCRIBER_ID"), fieldWhere.concat(timeWhere), ObjectUtil.isEmpty(query.getLimit()) ? defLimit : query.getLimit());
        log.info("real time relation subscribe id query sql: {}", sql);
        return ApiParam.builder().option("real-time").query(sql).build();
    }

    private BaseResult formatResult(BaseResult baseResult) {

        if (String.valueOf(HttpStatus.SC_OK).equals(String.valueOf(baseResult.getStatus()))) {
            baseResult.setMeta(null);
            List<Map> resultList = new ObjectMapper().convertValue(baseResult.getData(), new TypeReference<List<Map>>() {
            });
            resultList.forEach(r -> {
                if (ObjectUtil.isNotEmpty(r)) {
                    r.put("FIRST_FOUND_TIME", DateUtils.convertTimestampToString(Long.parseLong(String.valueOf(r.get("FIRST_FOUND_TIME"))), DateUtils.YYYY_MM_DD_HH24_MM_SS));
                    r.put("LAST_FOUND_TIME", DateUtils.convertTimestampToString(Long.parseLong(String.valueOf(r.get("LAST_FOUND_TIME"))), DateUtils.YYYY_MM_DD_HH24_MM_SS));
                }
            });
            return BaseResultGenerator.success(resultList);
        } else {
            baseResult = BaseResultGenerator.error(baseResult.getStatus(),baseResult.getMessage());
        }
        return baseResult;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}

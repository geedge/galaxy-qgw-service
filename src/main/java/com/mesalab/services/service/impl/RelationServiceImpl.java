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
import com.mesalab.knowledge.enums.MatchEnum;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.EntityConfigSource;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.services.common.dsl.ComDSLObject;
import com.mesalab.services.common.dsl.ComDSLParse;
import com.mesalab.services.common.enums.EntityQueryType;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.service.RelationService;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

@Service
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class RelationServiceImpl implements RelationService , EnvironmentAware {


    @Value("${arango.maxrows}")
    private String defLimit;//查询最大条数
    @Autowired
    private ApiService apiService;
    @Autowired
    private EntityConfigSource entityConfigSource;

    private Environment env;
    private static final Log log = LogFactory.get();

    @Override
    public BaseResult getRealRelation(String param, ComDSLObject comDSLObject) {
        BaseResult baseResult;
        if (param.equals(EntityQueryType.SUBSCRIBERIDPOOL.getType())) {
            baseResult = formatResult(apiService.executeQuery(queryBuild(comDSLObject.getQuery())));
        } else if (param.equals(EntityQueryType.GTPC.getType())) {
            String querySql = generateQuerySql(comDSLObject.getQuery());
            ApiParam apiParam = ApiParam.builder().option("real-time").query(querySql).build();
            baseResult = deduplicationData(apiService.executeQuery(apiParam));
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
        String sql = String.format(Objects.requireNonNull(env.getProperty("RELATION_SUBSCRIBER_ID")), fieldWhere.concat(timeWhere), ObjectUtil.isEmpty(query.getLimit()) ? defLimit : query.getLimit());
        log.info("real time relation subscribe id query sql: {}", sql);
        return  ApiParam.builder().option("real-time").query(sql).build();
    }

    private String generateQuerySql(ComDSLObject.Query query) {

        String fieldWhere = parseMath(generateRowKey(query));
        String sql = String.format(Objects.requireNonNull(env.getProperty("GTPC_KNOWLEDGE_BASE")), fieldWhere, ObjectUtil.isEmpty(query.getLimit()) ? entityConfigSource.getTopGTPCDefaultSize() : query.getLimit());
        log.info("real time relation gtpc query sql: {}", sql);
        return sql;
    }

    private ComDSLObject.Query generateRowKey(ComDSLObject.Query query) {

        List<ComDSLObject.Query.FilterBean> match = query.getParameters().getMatch();

        for (ComDSLObject.Query.FilterBean filterBean : match) {
            //Phone Number,APN 倒排
            String fieldKey = filterBean.getFieldKey();
            List<String> fieldValues = filterBean.getFieldValues();
            //imei,imsi前缀匹配查询时，应使用Row Key进行筛选
            if (StringUtil.equals(filterBean.getType(), MatchEnum.PREFIX.getType())
                    && (StringUtil.equals(fieldKey, "imei") || StringUtil.equals(fieldKey, "imsi"))) {

                if (StringUtil.equals(fieldKey, "imei")) {
                    for (int i = 0; i < fieldValues.size(); i++) {
                        fieldValues.set(i,"0".concat(fieldValues.get(i)));
                    }
                    //phone_number,apn 后缀匹配查询时，应使用Row Key进行筛选
                } else if (StringUtil.equals(fieldKey, "imsi")) {
                    for (int i = 0; i < fieldValues.size(); i++) {
                        fieldValues.set(i,"1".concat(fieldValues.get(i)));
                    }
                    //phone_number,apn 后缀匹配查询时，应使用Row Key进行筛选
                }
                filterBean.setFieldKey("ROWKEY");
                //phone_number,apn 后缀匹配查询时，应使用Row Key进行筛选
            } else if (StringUtil.equals(filterBean.getType(), MatchEnum.SUFFIX.getType())
                    && (StringUtil.equals(fieldKey, "phone_number") || StringUtil.equals(fieldKey, "apn"))) {
                for (int i = 0; i < fieldValues.size(); i++) {
                    fieldValues.set(i, new StringBuffer(fieldValues.get(i)).reverse().toString());
                }
                if (StringUtil.equals(fieldKey, "phone_number")) {
                    for (int i = 0; i < fieldValues.size(); i++) {
                        fieldValues.set(i,"2".concat(fieldValues.get(i)));
                    }
                    //phone_number,apn 后缀匹配查询时，应使用Row Key进行筛选
                } else if (StringUtil.equals(fieldKey, "apn")) {
                    for (int i = 0; i < fieldValues.size(); i++) {
                        fieldValues.set(i,"3".concat(fieldValues.get(i)));
                    }
                    //phone_number,apn 后缀匹配查询时，应使用Row Key进行筛选
                }
                filterBean.setFieldKey("ROWKEY");
                filterBean.setType(MatchEnum.PREFIX.getType());
            }
        }

        return query;
    }

    public static String parseMath(ComDSLObject.Query query) {
        if (ObjectUtil.isEmpty(query) || ObjectUtil.isEmpty(query.getParameters()) || ObjectUtil.isEmpty(query.getParameters().getMatch())) {
            return StringUtil.EMPTY;
        }
        List<ComDSLObject.Query.FilterBean> matches = query.getParameters().getMatch();
        return matches.stream().map(m -> {
            String fieldKey = m.getFieldKey();
            String type = m.getType();
            List<String> fieldValues = m.getFieldValues();
            return fieldValues.stream().filter(v -> ObjectUtil.isNotEmpty(v)).map(v -> {
                return ComDSLParse.parseLike(ComDSLParse.encode(v), type);
            }).collect(joining(" or ".concat(fieldKey).concat(" LIKE "), fieldKey.concat(" LIKE "), " "));

        }).collect(joining(" AND ", " AND( ", ")"));
    }

    private BaseResult formatResult(BaseResult baseResult) {

        if (String.valueOf(ResultStatusEnum.SUCCESS.getCode()).equals(String.valueOf(baseResult.getStatus()))) {
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

    private BaseResult deduplicationData(BaseResult baseResult) {
        Object data = baseResult.getData();
        if (ObjectUtil.isNotEmpty(data)) {
            List<Map<String, Object>> mapList = (List<Map<String, Object>>) data;
            ArrayList<Map<String, Object>> collect = mapList.stream().collect(Collectors.collectingAndThen(
                    Collectors.toCollection(() -> new TreeSet<>(
                            Comparator.comparing(e -> e.get("phone_number") + ";" + e.get("imsi") + ";" + e.get("imei")))), ArrayList::new));
            baseResult.setData(collect);
        }
        return baseResult;
    }
    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}

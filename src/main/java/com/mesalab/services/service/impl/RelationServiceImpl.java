package com.mesalab.services.service.impl;


import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.utils.JsonMapper;
import com.mesalab.knowledge.enums.MatchEnum;
import com.mesalab.qgw.exception.QGWBusinessException;
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

import static java.util.stream.Collectors.groupingBy;

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
            baseResult = deduplicationData(apiService.executeQuery(apiParam),comDSLObject.getQuery());
        } else {
            baseResult = BaseResultGenerator.failure(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(), "No match queryType");
        }
        if (!baseResult.isSuccess()) {
            BaseResult result = JsonMapper.fromJsonString(baseResult.getMessage(), BaseResult.class);
            throw new QGWBusinessException(baseResult.getStatus(), baseResult.getCode(), result.getMessage());
        }
        return baseResult;
    }


    private ApiParam queryBuild(ComDSLObject.Query query) {
        String range = ComDSLParse.parseRange(query.getParameters().getRange());
        List<ComDSLObject.Query.FilterBean> match = getMatchAfterTranslation(query.getParameters().getMatch());
        String fieldWhere = ComDSLParse.parseMath(match);
        String timeWhere = ComDSLParse.parseInterval(query, "last_update_time", true);
        String sql = String.format(Objects.requireNonNull(env.getProperty("RELATION_SUBSCRIBER_ID")), timeWhere.concat(range).concat(fieldWhere), ObjectUtil.isEmpty(query.getLimit()) ? defLimit : query.getLimit());
        log.info("real time relation subscribe id query sql: {}", sql);
        return ApiParam.builder().option("real-time").query(sql).build();
    }

    private List<ComDSLObject.Query.FilterBean> getMatchAfterTranslation(List<ComDSLObject.Query.FilterBean> match) {
        if (ObjectUtil.isEmpty(match)) {
            return Lists.newArrayList();
        }
        for (ComDSLObject.Query.FilterBean filterBean : match) {
            if (StringUtil.equals(MatchEnum.EXACTLY.getType(), filterBean.getType())) {
                filterBean.setType(MatchEnum.PREFIX.getType());
                filterBean.setFieldKey("ROWKEY");
                List<String> fieldValues = filterBean.getFieldValues();
                fieldValues.replaceAll(s -> DigestUtil.md5Hex(s).concat("|"));
            } else {
                filterBean.setFieldKey("account");
            }
        }
        return match;
    }

    private String generateQuerySql(ComDSLObject.Query query) {

        List<ComDSLObject.Query.FilterBean> match = query.getParameters().getMatch();
        StringBuffer fieldWhere = new StringBuffer();
        if (ObjectUtil.isNotEmpty(match)){
            StringBuffer filter = new StringBuffer();
            for (ComDSLObject.Query.FilterBean filterBean : match) {
                match = new ArrayList<>();
                if (StringUtil.equals(MatchEnum.REGEX.getType(), filterBean.getType())) {
                    Map<String, ComDSLObject.Query.FilterBean> map = transformData(filterBean);
                    for (Map.Entry<String, ComDSLObject.Query.FilterBean> entry : map.entrySet()) {
                        ComDSLObject.Query.FilterBean bean = entry.getValue();
                        match.add(generateRowKey(bean));
                    }
                    filter.append(ComDSLParse.parseMath(match));
                } else {
                    match.add(generateRowKey(filterBean));
                    filter.append(ComDSLParse.parseMath(match));
                }

            }
            StringBuffer sb = new StringBuffer(" AND (");
            List<String> list = match.stream().filter(e -> !StringUtil.equals(e.getFieldKey(), "ROWKEY")).map(e -> e.getFieldKey()).distinct().collect(Collectors.toList());
            for (String str : list) {
                if (StringUtil.equals(str,"imei")){
                    sb.append("ROWKEY like '0%'");
                } else if (StringUtil.equals(str,"imsi")){
                    sb.append("ROWKEY like '1%'");
                } else if (StringUtil.equals(str,"phone_number")){
                    sb.append("ROWKEY like '2%'");
                } else if (StringUtil.equals(str,"apn")){
                    sb.append("ROWKEY like '3%'");
                }
                sb.append(" OR ");
            }

            if (CollectionUtil.isNotEmpty(list)) {
                fieldWhere = sb.delete(sb.length() - 3, sb.length()).append(")").append(filter);
            } else {
                fieldWhere = filter;
            }
        }
        String range = ComDSLParse.parseRange(query.getParameters().getRange());

        String sql = String.format(Objects.requireNonNull(env.getProperty("GTPC_KNOWLEDGE_BASE")), range.concat(fieldWhere.toString()), entityConfigSource.getTopGTPCDefaultSize());
        log.info("real time relation gtpc query sql: {}", sql);
        return sql;
    }

    private ComDSLObject.Query.FilterBean generateRowKey(ComDSLObject.Query.FilterBean filterBean) {


            //Phone Number,APN 倒排
            String fieldKey = filterBean.getFieldKey();
            List<String> fieldValues = filterBean.getFieldValues();
            String type = filterBean.getType();
        //imei,imsi前缀匹配查询时，应使用Row Key进行筛选
            if (StringUtil.equals(type, MatchEnum.PREFIX.getType())
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
            } else if (StringUtil.equals(type, MatchEnum.SUFFIX.getType())
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

        return filterBean;
    }

    private Map<String, ComDSLObject.Query.FilterBean> transformData(ComDSLObject.Query.FilterBean filterBean) {

        Map<String, ComDSLObject.Query.FilterBean> map = new HashMap<>();
        List<String> fieldValues = filterBean.getFieldValues();
        String fieldKey = filterBean.getFieldKey();
        for (String value : fieldValues) {
            String type = getTypeByValue(value);
            String val =  value.replaceAll("\\$", "").replaceAll("\\*", "");
            ComDSLObject.Query.FilterBean filter = map.get(type);
            if (ObjectUtil.isEmpty(filter)) {
                filter = new ComDSLObject.Query.FilterBean();
                List<String> fieldValueList = new ArrayList<>();
                filter.setType(type);
                filter.setFieldKey(fieldKey);
                fieldValueList.add(val);
                filter.setFieldValues(fieldValueList);
                map.put(type, filter);
            } else {
                filter.getFieldValues().add(val);
            }
        }
        return map;
    }

    private String getTypeByValue(String value) {

        String type;
        if (value.startsWith("$")) {
            type = MatchEnum.EXACTLY.getType();
        } else if (value.startsWith("*") && !value.endsWith("*")) {
            type = MatchEnum.SUFFIX.getType();
        } else if (value.endsWith("*") && !value.startsWith("*")) {
            type = MatchEnum.PREFIX.getType();
        } else if (value.startsWith("*") && value.endsWith("*")) {
            type = MatchEnum.SUBSTRING.getType();
        } else {
            type = MatchEnum.SUBSTRING.getType();
        }
        return type;

    }

    /**
     *  排序
     * @param mapList
     * @param key
     * @return
     */
    private List<Map<String,Object>> sortByKey(List<Map<String,Object>> mapList ,String key){
        Collections.sort(mapList, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                String name1 = o1.get(key).toString();
                String name2 = o2.get(key).toString();
                return name2.compareTo(name1);
            }
        });
        return mapList;
    }


    private BaseResult formatResult(BaseResult baseResult) {

        if (String.valueOf(ResultStatusEnum.SUCCESS.getCode()).equals(String.valueOf(baseResult.getStatus()))) {
            baseResult.setMeta(null);
            List<Map<String,Object>> resultList = new ObjectMapper().convertValue(baseResult.getData(), new TypeReference<List<Map<String,Object>>>() {
            });
            LinkedHashMap<Object, List<Map<String,Object>>> listLinkedHashMap = resultList.stream()
                    .collect(groupingBy(e -> e.get("VSYS_ID") + "_" + e.get("IP"), LinkedHashMap::new, Collectors.toList()));
            List<Map<String,Object>> list = new ArrayList<>();
            for (Map.Entry<Object, List<Map<String,Object>>> entry : listLinkedHashMap.entrySet()) {
                List<Map<String, Object>> mapList = sortByKey(entry.getValue(), "LAST_FOUND_TIME");
                Map<String, Object> map = mapList.get(0);
                map.put("FIRST_FOUND_TIME", DateUtils.convertTimestampToString(Long.parseLong(String.valueOf(map.get("FIRST_FOUND_TIME"))), DateUtils.YYYY_MM_DD_HH24_MM_SS));
                map.put("LAST_FOUND_TIME", DateUtils.convertTimestampToString(Long.parseLong(String.valueOf(map.get("LAST_FOUND_TIME"))), DateUtils.YYYY_MM_DD_HH24_MM_SS));
                list.add(map);
            }

            return BaseResultGenerator.success(list);
        } else {
            baseResult = BaseResultGenerator.error(baseResult.getStatus(),baseResult.getMessage());
        }
        return baseResult;
    }

    private BaseResult deduplicationData(BaseResult baseResult, ComDSLObject.Query query) {
        Object data = baseResult.getData();
        if (ObjectUtil.isNotEmpty(data)) {
            List<Map<String, Object>> mapList = (List<Map<String, Object>>) data;
            List<Map<String, Object>> deduplicationPhoneList = removeRepeatMapByKey(mapList, "phone_number");
            List<Map<String, Object>> deduplicationIMSIList = removeRepeatMapByKey(deduplicationPhoneList, "imsi");
            List<Map<String, Object>> list = removeRepeatMapByKey(deduplicationIMSIList, "teid");
            String limit = query.getLimit();
            if (StringUtil.isNotEmpty(limit) && Long.parseLong(limit) < list.size()) {
                baseResult.setData(list.subList(0, Integer.parseInt(query.getLimit())));
            } else {
                baseResult.setData(list);
            }
        }
        return baseResult;
    }

    /**
     * 根据map中的某个key 去除List中重复的map
     * @param resultList
     * @param mapKey
     * @return list
     */

    public List<Map<String, Object>> removeRepeatMapByKey(List<Map<String, Object>> resultList, String mapKey) {

        LinkedHashMap<Object, List<Map<String,Object>>> listLinkedHashMap = resultList.stream()
                .collect(groupingBy(e -> e.get("vsys_id") + "_" + e.get(mapKey), LinkedHashMap::new, Collectors.toList()));
        List<Map<String,Object>> list = new ArrayList<>();
        for (Map.Entry<Object, List<Map<String,Object>>> entry : listLinkedHashMap.entrySet()) {
            List<Map<String, Object>> mapList = sortByKey(entry.getValue(), "last_update_time");
            Map<String, Object> map = mapList.get(0);
            list.add(map);
        }
        return list;

    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}

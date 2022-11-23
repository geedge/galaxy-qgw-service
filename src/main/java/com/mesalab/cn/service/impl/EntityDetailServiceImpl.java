package com.mesalab.cn.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.mesalab.cn.entity.pojo.Condition;
import com.mesalab.cn.entity.pojo.DSLObject;
import com.mesalab.cn.entity.pojo.DSLParser;
import com.mesalab.cn.entity.EntityDetail;
import com.mesalab.cn.exception.CNErrorCode;
import com.mesalab.cn.exception.CNErrorMessage;
import com.mesalab.cn.service.EntityDetailService;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.knowledge.strategy.BaseQueryProvider;
import org.apache.commons.compress.utils.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;


/**
 * @description:
 * @create: 2021-07-05
 **/
@Service
public class EntityDetailServiceImpl implements EntityDetailService {

    private static final Log log = LogFactory.get();

    @Autowired
    BaseQueryProvider baseQueryProvider;

    private static final String APP_VIEW = "entity_detail_app_view";
    private static final String DOMAIN_VIEW = "entity_detail_domain_view";
    private static final String IP_VIEW = "entity_detail_ip_view";
    private static final String DNS_VIEW = "entity_detail_dns_view";
    private static final String APP_NAME = "app_name";
    private static final String DOMAIN = "domain";
    private static final String IP = "ip";
    private static final String DNS = "dns";
    private static final String SERVER_IP = "server_ip";
    private static final String CLIENT_IP = "client_ip";
    private static final String STEP = "step";
    private static final String TABLE = "table";
    private static final String DIRECTION = "direction";
    private static final String FIELD = "field";
    private static final String TYPE = "type";
    private static final String FIRST = "first";
    private static final String SECOND = "second";
    private static final String THIRD = "third";
    private static final int DEF_LIMIT = 10;


    private static final ImmutableMap<String, Map<String, String>> appIdView = ImmutableMap.<String, Map<String, String>>of(
            "first", ImmutableMap.of(
                    "step", "1..2", "table", "R_USE_APP2DOMAIN", "direction", "from/", "field", "domain",
                    "type", "domain"),
            "second", ImmutableMap.of(
                    "step", "1..1", "table", "R_USE_DOMAIN2IP", "direction", "from/", "field", "ip",
                    "type", "ip"),
            "third", ImmutableMap.of(
                    "step", "1..1", "table", "R_VISIT_IP2IP", "direction", "to/", "field", "client_ip",
                    "type", "ip"));
    private static final ImmutableMap<String, Map<String, String>> domainView = ImmutableMap.<String, Map<String, String>>of(
            "first", ImmutableMap.of(
                    "step", "1..2", "table", "R_USE_APP2DOMAIN", "direction", "to/", "field", "domain",
                    "type", "domain"),
            "second", ImmutableMap.of(
                    "step", "1..2", "table", "R_USE_DOMAIN2IP", "direction", "from/", "field", "ip",
                    "type", "ip"),
            "third", ImmutableMap.of(
                    "step", "1..1", "table", "R_VISIT_IP2IP", "direction", "to/", "field", "client_ip",
                    "type", "ip"));
    private static final ImmutableMap<String, Map<String, String>> serverIpView = ImmutableMap.<String, Map<String, String>>of(
            "first", ImmutableMap.of(
                    "step", "1..2", "table", "R_USE_DOMAIN2IP", "direction", "to/", "field", "domain",
                    "type", "domain"),
            "second", ImmutableMap.of(
                    "step", "1..1", "table", "R_USE_APP2DOMAIN", "direction", "to/", "field", "ip",
                    "type", "ip"),
            "third", ImmutableMap.of(
                    "step", "1..1", "table", "R_VISIT_IP2IP", "direction", "to/", "field", "domain",
                    "type", "ip"));
    private static final ImmutableMap<String, Map<String, String>> clientIpView = ImmutableMap.<String, Map<String, String>>of(
            "first", ImmutableMap.of(
                    "step", "1..1", "table", "R_VISIT_IP2IP", "direction", "from/", "field", "server_ip",
                    "type", "ip"),
            "second", ImmutableMap.of(
                    "step", "1..1", "table", "R_USE_DOMAIN2IP", "direction", "to/", "field", "domain", "type",
                    "domain"),
            "third", ImmutableMap.of(
                    "step", "1..1", "table", "R_USE_APP2DOMAIN", "direction", "to/", "field", "app_name",
                    "type", "app_name"));
    private static final ImmutableMap<String, Map<String, String>> dnsView = ImmutableMap.<String, Map<String, String>>of(
            "first", ImmutableMap.of(
                    "step", "1..1", "table", "R_CNAME_DOMAIN2DOMAIN", "direction", "from/", "field", "domain",
                    "type", "domain"),
            "second", ImmutableMap.of(
                    "step", "1..1", "table", "R_NS_DOMAIN2DOMAIN", "direction", "from/", "field", "domain",
                    "type", "domain"),
            "third", ImmutableMap.of(
                    "step", "1..2", "table", "R_USE_DOMAIN2IP", "direction", "from/", "field", "ip",
                    "type", "ip"));

    @Override
    public BaseResult entityDetailQuery(DSLObject dslObject) throws BusinessException {

        return parserQuery(dslObject);
    }

    private BaseResult parserQuery(DSLObject dslObject) throws BusinessException {
        DSLObject.Query.Parameters parameters = dslObject.getQuery().getParameters();
        DSLObject.Query.Parameters.Match match = parameters.getMatch();
        String dataSource = dslObject.getQuery().getDataSource();
        valid(parameters);
        List<Condition> condition = match.getCondition();
        String filter = String.valueOf(condition.get(0).getFieldValues().get(0));
        String sort = DSLParser.parseAQLSort(dslObject, "p");
        String limit = DSLParser.parseAQLLimit(dslObject, DEF_LIMIT);

        Object result = null;
        switch (dataSource) {
            case APP_VIEW:
                result = queryByApp(filter, sort, limit);
                break;
            case DOMAIN_VIEW:
                result = queryByDomain(filter, sort, limit);
                break;
            case DNS_VIEW:
                result = queryByDNS(filter, sort, limit);
                break;
            case IP_VIEW:
                if(existServerIp(filter)){
                    result = queryByServerIp(filter, sort, limit);
                } else {
                    result = queryByClientIp(filter, sort, limit);
                }
                break;
            default:
                break;
        }
        return BaseResultGenerator.success(result == null ? Arrays.asList() : Arrays.asList(result));
    }

    private Object queryByApp(String condition, String sort, String limit) throws BusinessException {

        Map<String, String> firstAql = appIdView.get(FIRST);
        Map<String, String> secondAql = appIdView.get(SECOND);
        Map<String, String> thirdAql = appIdView.get(THIRD);

        List<Map> firstEdges = execute(firstAql.get(STEP), firstAql.get(DIRECTION).concat(condition), firstAql.get(TABLE), sort, limit);
        List<Map> distinctList = Lists.newArrayList();
        List<Map> firstList = Lists.newArrayList();
        List<Map> elseAppList = Lists.newArrayList();

        //拆分2度结果跟1度结果
        for (Map edDomainMap : firstEdges) {
            if(edDomainMap.get(APP_NAME)!=null) {
                if (condition.equals(String.valueOf(edDomainMap.get(APP_NAME)))) {
                    firstList.add(edDomainMap);
                } else {
                    elseAppList.add(edDomainMap);
                }
            }
        }
        firstList.parallelStream().collect(Collectors.groupingBy(e -> Optional.ofNullable(e.get(DOMAIN)), Collectors.toList())).forEach(
                (id, transfer) -> transfer.stream().reduce((a, b) -> a).ifPresent(distinctList::add));

        List<Object> appLeaf = new ArrayList<>();
        for (Map first : distinctList) {
            List<Object> domainLeaf = new ArrayList<>();
            EntityDetail edDomain = toBean(first, firstAql.get(FIELD), firstAql.get(TYPE));
            for (Map elseApp : elseAppList) {
                if (first.get(DOMAIN).equals(elseApp.get(DOMAIN))) {
                    domainLeaf.add(toBean(elseApp, APP_NAME, APP_NAME));
                }
            }

            List<Map> secondEdges = execute(secondAql.get(STEP),
                    secondAql.get(DIRECTION).concat(edDomain.getName()), secondAql.get(TABLE), sort, limit);

            for (Map edServerMap : secondEdges) {
                EntityDetail edServer = toBean(edServerMap, secondAql.get(FIELD), secondAql.get(TYPE));
                List<Map> thirdEdges = execute(thirdAql.get(STEP),
                        thirdAql.get(DIRECTION).concat(edServer.getName()), thirdAql.get(TABLE), sort, limit);
                List<Object> clientList = new ArrayList<>();
                for (Map edClientMap : thirdEdges) {
                    clientList.add(toBean(edClientMap, thirdAql.get(FIELD), thirdAql.get(TYPE)));
                }
                edServer.setLeaf(clientList);
                domainLeaf.add(edServer);
            }
            edDomain.setLeaf(domainLeaf);
            appLeaf.add(edDomain);
        }
        if (ObjectUtil.isNotEmpty(appLeaf)) {
            return EntityDetail.builder().name(condition).type(APP_NAME).leaf(appLeaf).build();
        }
        return null;
    }

    private Object queryByDomain(String condition, String sort, String limit) throws BusinessException {

        Map<String, String> firstAql = domainView.get(FIRST);
        Map<String, String> secondAql = domainView.get(SECOND);
        Map<String, String> thirdAql = domainView.get(THIRD);

        List<Map> firstEdges = execute(firstAql.get(STEP), firstAql.get(DIRECTION).concat(condition), firstAql.get(TABLE), sort, limit);

        EntityDetail searchDomain = EntityDetail.builder().name(condition).type(DOMAIN).build();
        List<Map> distinctList = new ArrayList();
        List<Map> app2DomaintList = new ArrayList();
        List<Object> domainLeaf = new ArrayList<>();
        //拆分2度结果跟1度结果
        List<Map> firstAppList = Lists.newArrayList();
        for (Map edDomainMap : firstEdges) {
            //1度
            if (condition.equals(String.valueOf(edDomainMap.get(DOMAIN)))) {
                app2DomaintList.add(edDomainMap);
            } else {
                //2度domain放在第一级
                firstAppList.add(edDomainMap);
            }
        }
        app2DomaintList.parallelStream()
                .collect(Collectors.groupingBy(e -> Optional.ofNullable(e.get(APP_NAME)), Collectors.toList())).forEach(
                (id, transfer) -> transfer.stream().reduce((a, b) -> a).ifPresent(distinctList::add));
        for (Map appMap : distinctList) {
            EntityDetail ed = toBean(appMap, APP_NAME, APP_NAME);
            for (Map domain : firstAppList) {
                if (domain.get(APP_NAME)!=null&&domain.get(APP_NAME).equals(appMap.get(APP_NAME))) {
                    EntityDetail domainEd = toBean(domain, DOMAIN, DOMAIN);
                    List<Object> edLeaf = Lists.newArrayList();
                    if (ed.getLeaf() != null) {
                        edLeaf.addAll(ed.getLeaf());
                    }
                    edLeaf.add(domainEd);
                    ed.setLeaf(edLeaf);
                }
            }
            domainLeaf.add(BeanUtil.beanToMap(ed));

        }

        List<Map> secondEdges = execute(secondAql.get(STEP), secondAql.get(DIRECTION).concat(condition), secondAql.get(TABLE), sort, limit);
        List<Map> elseDomainList = Lists.newArrayList();
        List<Map> secondIpList = Lists.newArrayList();
        List<Map> secondDistinctList = Lists.newArrayList();
        for (Map secondEdge : secondEdges) {
            if (condition.equals(secondEdge.get(DOMAIN))) {
                secondIpList.add(secondEdge);
            } else {
                elseDomainList.add(secondEdge);
            }
        }

        secondIpList.parallelStream()
                .collect(Collectors.groupingBy(e -> Optional.ofNullable(e.get(IP)), Collectors.toList())).forEach(
                (id, transfer) -> transfer.stream().reduce((a, b) -> a)
                        .ifPresent(secondDistinctList::add));

        //server_IP.leaf ->else domain
        for (Map<String, Object> serverMap : secondDistinctList) {
            for (Map sdomain : elseDomainList) {
                if (serverMap.get(IP).equals(sdomain.get(IP))) {
                    EntityDetail domainEd = toBean(sdomain, DOMAIN, DOMAIN);
                    List<Object> edLeaf = Lists.newArrayList();
                    if (serverMap.get("leaf") != null) {
                        edLeaf.addAll((Collection<?>) serverMap.get("leaf"));
                    }
                    edLeaf.add(domainEd);
                    serverMap.put("leaf", edLeaf);
                }
            }
        }

        for (Map edServerMap : secondDistinctList) {
            EntityDetail edServer = toBean(edServerMap, secondAql.get(FIELD), secondAql.get(TYPE));
            List<Map> thirdEdges = execute(thirdAql.get(STEP), thirdAql.get(DIRECTION).concat(edServer.getName()), thirdAql.get(TABLE), sort, limit);
            List<Object> clientList = new ArrayList<>();
            for (Map edClientMap : thirdEdges) {
                clientList.add(toBean(edClientMap, thirdAql.get(FIELD), thirdAql.get(TYPE)));
            }
            if (edServer.getLeaf() != null) {
                clientList.addAll(edServer.getLeaf());
            }
            edServer.setLeaf(clientList);
            domainLeaf.add(edServer);
        }
        searchDomain.setLeaf(domainLeaf);
        if (ObjectUtil.isNotEmpty(domainLeaf)) {
            return searchDomain;
        }
        return null;
    }

    private Object queryByServerIp(String condition, String sort, String limit) throws BusinessException {

        Map<String, String> firstAql = serverIpView.get(FIRST);
        Map<String, String> thirdAql = serverIpView.get(THIRD);
        Map<String, String> secondAql = serverIpView.get(SECOND);

        List<Map> firstEdges = execute(firstAql.get(STEP), firstAql.get(DIRECTION).concat(condition), firstAql.get(TABLE), sort, limit);
        //一级
        EntityDetail searchServerIp = EntityDetail.builder().name(condition).type(IP).build();
        List<Map> distinctList = new ArrayList();
        List<Map> serverIp2DomainList = new ArrayList();
        List<Object> serverIpLeaf = new ArrayList<>();
        //拆分2度结果跟1度结果
        List<Map> firstServerIpList = Lists.newArrayList();
        for (Map edDomainMap : firstEdges) {
            //1度
            if (condition.equals(String.valueOf(edDomainMap.get(IP)))) {
                serverIp2DomainList.add(edDomainMap);
            } else {
                //2度domain放在第一级
                firstServerIpList.add(edDomainMap);
            }
        }
        serverIp2DomainList.parallelStream().collect(Collectors.groupingBy(e -> Optional.ofNullable(e.get(DOMAIN)), Collectors.toList())).forEach(
                (id, transfer) -> transfer.stream().reduce((a, b) -> a).ifPresent(distinctList::add));

        //2级

        for (Map domainMap : distinctList) {
            EntityDetail domainEd = toBean(domainMap, DOMAIN, DOMAIN);
            List<Object> domainLeaf = Lists.newArrayList();
            List<Map> secondEdges = execute(secondAql.get(STEP), secondAql.get(DIRECTION).concat(domainEd.getName()), secondAql.get(TABLE), sort, limit);
            for (Map serverIp : firstServerIpList) {
                if (domainMap.get(DOMAIN).equals(serverIp.get(DOMAIN))) {
                    EntityDetail serverEd = toBean(serverIp, IP, IP);
                    domainLeaf.add(serverEd);
                }
            }
            for (Map appEdge : secondEdges) {
                if (domainMap.get(DOMAIN).equals(appEdge.get(DOMAIN))) {
                    EntityDetail appEd = toBean(appEdge, APP_NAME, APP_NAME);
                    domainLeaf.add(appEd);
                }
            }
            domainEd.setLeaf(domainLeaf);
            serverIpLeaf.add(domainEd);
        }

        List<Map> thirdEdges = execute(thirdAql.get(STEP), thirdAql.get(DIRECTION).concat(condition),
                thirdAql.get(TABLE), sort, limit);
        for (Map edClientMap : thirdEdges) {
            serverIpLeaf.add(toBean(edClientMap, CLIENT_IP, IP));
        }
        searchServerIp.setLeaf(serverIpLeaf);
        if (ObjectUtil.isNotEmpty(serverIpLeaf)) {
            return searchServerIp;
        }
        return null;
    }
    private Object queryByClientIp(String condition, String sort, String limit) throws BusinessException {

        Map<String, String> firstAql = clientIpView.get(FIRST);
        Map<String, String> secondAql = clientIpView.get(SECOND);
        Map<String, String> thirdAql = clientIpView.get(THIRD);

        List<Map> firstEdges = execute(firstAql.get(STEP), firstAql.get(DIRECTION).concat(condition), firstAql.get(TABLE), sort, limit);
        EntityDetail searchClientIp = EntityDetail.builder().name(condition).type(IP).build();
        List<Object> clientIpLeaf = new ArrayList<>();

        for (Map serverIpMap : firstEdges) {
            EntityDetail serverEd = toBean(serverIpMap, SERVER_IP, IP);
            List<Object> serverLeaf = Lists.newArrayList();
            List<Map> secondEdges = execute(secondAql.get(STEP), secondAql.get(DIRECTION).concat(serverEd.getName()), secondAql.get(TABLE), sort, limit);
            for (Map domainMap : secondEdges) {
                EntityDetail domainEd = toBean(domainMap, DOMAIN, DOMAIN);
                List<Map> thirdEdges = execute(thirdAql.get(STEP), thirdAql.get(DIRECTION).concat(domainEd.getName()), thirdAql.get(TABLE), sort, limit);
                List<Object> domainLeaf = Lists.newArrayList();
                for (Map appMap : thirdEdges) {
                    domainLeaf.add(toBean(appMap, APP_NAME, APP_NAME));
                }
                domainEd.setLeaf(domainLeaf);
                serverLeaf.add(domainEd);
            }
            serverEd.setLeaf(serverLeaf);
            clientIpLeaf.add(serverEd);
        }
        searchClientIp.setLeaf(clientIpLeaf);
        if (ObjectUtil.isNotEmpty(clientIpLeaf)) {
            return searchClientIp;
        }
        return null;
    }
    private Object queryByDNS(String condition, String sort, String limit) throws BusinessException {

        Map<String, String> firstAql = dnsView.get(FIRST);
        Map<String, String> secondAql = dnsView.get(SECOND);
        Map<String, String> thirdAql = dnsView.get(THIRD);

        List<Map> firstEdges = execute(firstAql.get(STEP), firstAql.get(DIRECTION).concat(condition), firstAql.get(TABLE), sort, limit);
        EntityDetail searchDomain = EntityDetail.builder().name(condition).type(DOMAIN).build();

        List<Map<String, Object>> firstDistinctList = new ArrayList<>();
        List<Object> domainLeaf = new ArrayList<>();
        //cname去重并转为map,添加到domainleaf中
        firstEdges.parallelStream()
                .collect(Collectors.groupingBy(e -> Optional.ofNullable(e.get("cname")), Collectors.toList())).forEach(
                (id, transfer) -> transfer.stream().reduce((a, b) -> a).ifPresent(a->firstDistinctList.add(BeanUtil.beanToMap(toBean(a, "cname", "domain")))));

        //ns去重转为map,添加到domainleaf
        List<Map> secondEdges = execute(secondAql.get(STEP), secondAql.get(DIRECTION).concat(condition), secondAql.get(TABLE), sort, limit);
        secondEdges.parallelStream()
                .collect(Collectors.groupingBy(e -> Optional.ofNullable(e.get("ns")), Collectors.toList())).forEach(
                (id, transfer) -> transfer.stream().reduce((a, b) -> a)
                        .ifPresent(a->firstDistinctList.add(BeanUtil.beanToMap(toBean(a, "ns", "domain")))));

        //加入searchDomain
        firstDistinctList.add(BeanUtil.beanToMap(searchDomain));

        List<Map> domain1List = new ArrayList();
        //2度查域名下的ip及ip相关域名
        for (Map<String, Object> domains : firstDistinctList) {
            List<Map> thirdEdges = execute(thirdAql.get(STEP), thirdAql.get(DIRECTION)+domains.get("name"), thirdAql.get(TABLE), sort, limit);
            List<Map> ipDistinctList = new ArrayList();
            List<Map> domain2List = new ArrayList();
            //拆分2度结果跟1度结果
            List<Map> ip2List = Lists.newArrayList();
            for (Map edDomainMap : thirdEdges) {
                //1度抽取域名相同的ip
                if (condition.equals(String.valueOf(edDomainMap.get(DOMAIN)))) {
                    ip2List.add(edDomainMap);
                } else {
                    //2度domain
                    domain2List.add(edDomainMap);
                }
            }
            ip2List.parallelStream()
                    .collect(Collectors.groupingBy(e -> Optional.ofNullable(e.get(IP)), Collectors.toList())).forEach(
                    (id, transfer) -> transfer.stream().reduce((a, b) -> a).ifPresent(ipDistinctList::add));
            for (Map appMap : ipDistinctList) {
                EntityDetail ed = toBean(appMap, IP, IP);
                //2度domain的ip跟1度相同则为1度的叶子
                for (Map domain : domain2List) {
                    if (domain.get(IP)!=null&&domain.get(IP).equals(appMap.get(IP))) {
                        EntityDetail domainEd = toBean(domain, DOMAIN, DOMAIN);
                        List<Object> edLeaf = Lists.newArrayList();
                        if (ed.getLeaf() != null) {
                            edLeaf.addAll(ed.getLeaf());
                        }
                        edLeaf.add(domainEd);
                        ed.setLeaf(edLeaf);
                    }
                }
                //1度为查询条件域名的叶子
                if(condition.equals(appMap.get("domain"))){
                    domainLeaf.add(BeanUtil.beanToMap(ed));
                } else {
                    domain1List.add(BeanUtil.beanToMap(ed));
                }
            }
            if(!condition.equals(domains.get("name"))){
                domainLeaf.add(domains);
            }
        }
        searchDomain.setLeaf(domainLeaf);
        if (ObjectUtil.isNotEmpty(domainLeaf)) {
            return searchDomain;
        }

        return null;
    }

    /**
     * @return java.util.List<java.util.Map>
     * @Param [step, filter, table, sort, limit]
     **/
    private List<Map> execute(String step, String filter, String table, String sort, String limit) {
        List<Map> edges = Lists.newArrayList();
        String aql = Joiner.on(" ").join(" for v,e,p in", step, "any", "'".concat(filter).concat("'"), table, sort, limit, "return distinct p");
        log.info("entity detail aql {}", aql);
        List<Map> resultList = new ObjectMapper().convertValue(baseQueryProvider.queryArango(aql), new TypeReference<List<Map>>() {
        });
        for (Map pMap : resultList) {
            edges.addAll((Collection<? extends Map>) pMap.get("edges"));
        }
        return edges;
    }

    /**
     * @return com.mesalab.cn.common.pojo.entity.EntityDetail
     * @Description 数据库返回格式 -> 实体详细对象
     * @Param [map, field, type]
     **/
    private EntityDetail toBean(Map<String, Object> edmap, String field, String type) {
        return EntityDetail.builder()
                .type(type)
                .name(String.valueOf(edmap.get(field)==null?"":edmap.get(field)))
                .from(StrUtil.removePrefix(String.valueOf(edmap.get("_from")),"from/"))
                .to(StrUtil.removePrefix(String.valueOf(edmap.get("_to")),"to/"))
                .lastTime(Long.valueOf(String.valueOf(edmap.get("last_time"))))
                .leaf(edmap.get("leaf") == null ? null : (List) edmap.get("leaf")).build();
    }

    private void valid(DSLObject.Query.Parameters parameters) {
        DSLObject.Query.Parameters.Match match = parameters.getMatch();
        if (ObjectUtil.isEmpty(match)) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), CNErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CNErrorCode.PARAMETER_ERROR.getMessage(), CNErrorMessage.PARAMETERS_IS_INVALID));
        }
        List<Condition> condition = match.getCondition();
        if (ObjectUtil.isEmpty(condition)) { throw new BusinessException(ResultStatusEnum.FAIL.getCode(), CNErrorCode.PARAMETER_ERROR.getCode(),
                String.format(CNErrorCode.PARAMETER_ERROR.getMessage(), CNErrorMessage.PARAMETERS_MATCH_CONDITION_IS_INVALID));
        }
        if (ObjectUtil.isEmpty(condition.get(0).getFieldValues())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), CNErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CNErrorCode.PARAMETER_ERROR.getMessage(), CNErrorMessage.PARAMETERS_MATCH_CONDITION_FIELD_VALUE_IS_INVALID));
        }
        if (ObjectUtil.isEmpty(condition.get(0).getFieldKey())) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), CNErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CNErrorCode.PARAMETER_ERROR.getMessage(), CNErrorMessage.PARAMETERS_MATCH_CONDITION_FIELD_KEY_IS_INVALID));
        }
        if (!(Arrays.asList(APP_NAME, DOMAIN, IP, DNS).contains(condition.get(0).getFieldKey()))) {
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), CNErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CNErrorCode.PARAMETER_ERROR.getMessage(), CNErrorMessage.PARAMETERS_MATCH_CONDITION_FIELD_KEY_IS_INVALID));
        }
        Integer limit = parameters.getLimit();
        if(ObjectUtil.isNotEmpty(limit)&&!(limit>=0&&limit<=DEF_LIMIT)){
            throw new BusinessException(ResultStatusEnum.FAIL.getCode(), CNErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CNErrorCode.PARAMETER_ERROR.getMessage(), String.format(CNErrorMessage.PARAMETERS_LIMIT_IS_INVALID),DEF_LIMIT));
        }
    }
    private boolean existServerIp(String ip){
        String aql = "FOR doc IN R_VISIT_IP2IP FILTER doc._to == 'to/"+ip+"' LIMIT 1 RETURN doc";
        log.info("entity detail aql {}", aql);
        List<Map> resultList = new ObjectMapper().convertValue(baseQueryProvider.queryArango(aql), new TypeReference<List<Map>>() {
        });
        if(ObjectUtil.isEmpty(resultList)){
            return false;
        }
        return true;
    }
}

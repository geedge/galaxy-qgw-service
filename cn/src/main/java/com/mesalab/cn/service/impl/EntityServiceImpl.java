package com.mesalab.cn.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hutool.log.LogFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.mesalab.cn.entity.BannedApp;
import com.mesalab.cn.entity.EntityDetail;
import com.mesalab.cn.entity.Parser;
import com.mesalab.cn.entity.ShareNet;
import com.mesalab.cn.exception.CNErrorMessage;
import com.mesalab.cn.service.EntityService;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.qgw.dialect.BaseQueryProvider;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.service.ApiService;
import com.mesalab.ua.analyser.UaAnalyser;
import com.mesalab.ua.analyser.enums.UserInfo;
import com.google.common.base.Joiner;
import com.mesalab.common.entity.Condition;
import com.mesalab.common.entity.DSLObject;
import com.mesalab.common.entity.DSLParser;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.ua.analyser.impl.UaAnalyserImpl;
import com.zdjizhi.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Date: 2020-11-20 10:05
 * @Author : wWei
 * @ClassName : CNServiceImpl
 * @Description : Druid数据查询引擎实现
 */
@Service
public class EntityServiceImpl implements EntityService {
    private static final Log log = LogFactory.get();

    @Autowired
    private Parser parser;

    @Autowired
    private ApiService apiService;

    @Autowired
    private BaseQueryProvider baseQueryProvider;

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
                if (existServerIp(filter)) {
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
            if (edDomainMap.get(APP_NAME) != null) {
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
                if (domain.get(APP_NAME) != null && domain.get(APP_NAME).equals(appMap.get(APP_NAME))) {
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
                (id, transfer) -> transfer.stream().reduce((a, b) -> a).ifPresent(a -> firstDistinctList.add(BeanUtil.beanToMap(toBean(a, "cname", "domain")))));

        //ns去重转为map,添加到domainleaf
        List<Map> secondEdges = execute(secondAql.get(STEP), secondAql.get(DIRECTION).concat(condition), secondAql.get(TABLE), sort, limit);
        secondEdges.parallelStream()
                .collect(Collectors.groupingBy(e -> Optional.ofNullable(e.get("ns")), Collectors.toList())).forEach(
                (id, transfer) -> transfer.stream().reduce((a, b) -> a)
                        .ifPresent(a -> firstDistinctList.add(BeanUtil.beanToMap(toBean(a, "ns", "domain")))));

        //加入searchDomain
        firstDistinctList.add(BeanUtil.beanToMap(searchDomain));

        List<Map> domain1List = new ArrayList();
        //2度查域名下的ip及ip相关域名
        for (Map<String, Object> domains : firstDistinctList) {
            List<Map> thirdEdges = execute(thirdAql.get(STEP), thirdAql.get(DIRECTION) + domains.get("name"), thirdAql.get(TABLE), sort, limit);
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
                    if (domain.get(IP) != null && domain.get(IP).equals(appMap.get(IP))) {
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
                if (condition.equals(appMap.get("domain"))) {
                    domainLeaf.add(BeanUtil.beanToMap(ed));
                } else {
                    domain1List.add(BeanUtil.beanToMap(ed));
                }
            }
            if (!condition.equals(domains.get("name"))) {
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
                .name(String.valueOf(edmap.get(field) == null ? "" : edmap.get(field)))
                .from(StrUtil.removePrefix(String.valueOf(edmap.get("_from")), "from/"))
                .to(StrUtil.removePrefix(String.valueOf(edmap.get("_to")), "to/"))
                .lastTime(Long.valueOf(String.valueOf(edmap.get("last_time"))))
                .leaf(edmap.get("leaf") == null ? null : (List) edmap.get("leaf")).build();
    }

    private void valid(DSLObject.Query.Parameters parameters) {
        DSLObject.Query.Parameters.Match match = parameters.getMatch();
        if (ObjectUtil.isEmpty(match)) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), CNErrorMessage.PARAMETERS_IS_INVALID));
        }
        List<Condition> condition = match.getCondition();
        if (ObjectUtil.isEmpty(condition)) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), CNErrorMessage.PARAMETERS_MATCH_CONDITION_IS_INVALID));
        }
        if (ObjectUtil.isEmpty(condition.get(0).getFieldValues())) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), CNErrorMessage.PARAMETERS_MATCH_CONDITION_FIELD_VALUE_IS_INVALID));
        }
        if (ObjectUtil.isEmpty(condition.get(0).getFieldKey())) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), CNErrorMessage.PARAMETERS_MATCH_CONDITION_FIELD_KEY_IS_INVALID));
        }
        if (!(Arrays.asList(APP_NAME, DOMAIN, IP, DNS).contains(condition.get(0).getFieldKey()))) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), CNErrorMessage.PARAMETERS_MATCH_CONDITION_FIELD_KEY_IS_INVALID));
        }
        Integer limit = parameters.getLimit();
        if (ObjectUtil.isNotEmpty(limit) && !(limit >= 0 && limit <= DEF_LIMIT)) {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), String.format(CNErrorMessage.PARAMETERS_LIMIT_IS_INVALID), DEF_LIMIT));
        }
    }

    private boolean existServerIp(String ip) {
        String aql = "FOR doc IN R_VISIT_IP2IP FILTER doc._to == 'to/" + ip + "' LIMIT 1 RETURN doc";
        log.info("entity detail aql {}", aql);
        List<Map> resultList = new ObjectMapper().convertValue(baseQueryProvider.queryArango(aql), new TypeReference<List<Map>>() {
        });
        if (ObjectUtil.isEmpty(resultList)) {
            return false;
        }
        return true;
    }

    @Override
    public BaseResult shareNetQuery(DSLObject dslObject) throws BusinessException {
        dslObject.getQuery().getParameters().setGranularity("user_id,ip");
        String executeSql = parser.buildShareNetSQL(dslObject);
        ApiParam apiParam = new ApiParam();
        apiParam.setQuery(executeSql);
        BaseResult baseResult = apiService.executeQuery(apiParam);
        if (!baseResult.isSuccess()) {
            return baseResult;
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        List<ShareNet> result = formatShareNetResult(data, dslObject);
        for (ShareNet shareNet : result) {
            filterResult(dslObject, shareNet);
        }
        removeResult(result);
        removeResult(result);
        removeResult(result);

        Integer limit;
        if (StringUtil.isNotEmpty(limit = dslObject.getQuery().getParameters().getLimit()) && result.size() > limit) {
            result = result.subList(0, limit);
        }
        return BaseResultGenerator.success(result);
    }

    @Override
    public BaseResult bannedAppQuery(DSLObject dslObject) throws BusinessException {
        dslObject.getQuery().getParameters().setGranularity("user_id,ip");
        String executeSql = parser.bannedAppSQL(dslObject);
        ApiParam apiParam = new ApiParam();
        apiParam.setQuery(executeSql);
        BaseResult baseResult = apiService.executeQuery(apiParam);
        if (!baseResult.isSuccess()) {
            return baseResult;
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();
        List<BannedApp> result = formatBannedAppResult(data, dslObject);

        Integer limit;
        if (StringUtil.isNotEmpty(limit = dslObject.getQuery().getParameters().getLimit()) && result.size() > limit) {
            result = result.subList(0, limit);
        }
        return BaseResultGenerator.success(result);
    }

    private List<BannedApp> formatBannedAppResult(List<Map<String, Object>> data, DSLObject dslObject) {
        List<BannedApp> result = new ArrayList<>();
        DSLObject.Query.Parameters parameters = dslObject.getQuery().getParameters();
        DSLObject.Query.Parameters.Range range = parameters.getRange();
        for (Map<String, Object> datum : data) {
            List<BannedApp.Feature> featureList = new ArrayList<>();
            BannedApp.Feature feature;
            for (Condition condition : range.getCondition()) {
                feature = new BannedApp.Feature();
                feature.setFeatureKey(condition.getFieldKey());
                feature.setUniqueCnt(String.valueOf(datum.get(condition.getFieldKey())));
                featureList.add(feature);
            }
            List<BannedApp.IpAccessInfo> ipAccessInfoList = new ArrayList<>();
            BannedApp.IpAccessInfo ipAccessInfo = new BannedApp.IpAccessInfo();
            ipAccessInfo.setType(dslObject.getQuery().getQueryType());
            ipAccessInfo.setFeature(featureList);
            ipAccessInfoList.add(ipAccessInfo);

            List<BannedApp.UserIp> userIpList = new ArrayList<>();
            BannedApp.UserIp userIp = new BannedApp.UserIp();
            userIp.setIp(String.valueOf(datum.get("ip")));
            userIp.setIpAccessInfo(ipAccessInfoList);
            userIpList.add(userIp);

            BannedApp bannedApp = new BannedApp();
            bannedApp.setUserId(String.valueOf(datum.get("user_id")));
            bannedApp.setUserIps(userIpList);
            result.add(bannedApp);
        }
        result = mergeUserIpOfBannedApp(result);
        return result;
    }

    private List<BannedApp> mergeUserIpOfBannedApp(List<BannedApp> dataList) {
        List<BannedApp> result = new ArrayList<>();
        data:
        for (BannedApp bannedApp : dataList) {
            for (BannedApp datum : result) {
                if (datum.getUserId().equals(bannedApp.getUserId())) {
                    datum.getUserIps().add(bannedApp.getUserIps().get(0));
                    continue data;
                }
            }
            result.add(bannedApp);
        }
        return result;
    }


    private void removeResult(List<ShareNet> data) {
        Iterator<ShareNet> iterator = data.iterator();
        while (iterator.hasNext()) {
            ShareNet next = iterator.next();
            if (CollectionUtils.isEmpty(next.getUserIps())) {
                iterator.remove();
            }
            List<ShareNet.UserIp> userIpList = next.getUserIps();
            Iterator<ShareNet.UserIp> userIpIterator = userIpList.iterator();
            while (userIpIterator.hasNext()) {
                ShareNet.UserIp next1 = userIpIterator.next();
                if (CollectionUtils.isEmpty(next1.getIpAccessInfo())) {
                    userIpIterator.remove();
                }
                List<ShareNet.IpAccessInfo> ipAccessInfoList = next1.getIpAccessInfo();
                Iterator<ShareNet.IpAccessInfo> ipAccessInfoIterator = ipAccessInfoList.iterator();
                while (ipAccessInfoIterator.hasNext()) {
                    ShareNet.IpAccessInfo t = ipAccessInfoIterator.next();
                    if (StringUtil.isEmpty(t.getFeature())) {
                        ipAccessInfoIterator.remove();
                    }
                }
            }
        }
    }

    private void filterResult(DSLObject dslObject, ShareNet shareNet) {
        DSLObject.Query.Parameters parameters = dslObject.getQuery().getParameters();
        DSLObject.Query.Parameters.Distinct distinct = parameters.getDistinct();
        DSLObject.Query.Parameters.Range range = parameters.getRange();
        List<Condition> condition = new ArrayList<>();
        if (StringUtil.isNotEmpty(distinct) && !CollectionUtils.isEmpty(distinct.getCondition())) {
            condition.addAll(distinct.getCondition());
        }
        if (StringUtil.isNotEmpty(range) && !CollectionUtils.isEmpty(range.getCondition())) {
            condition.addAll(range.getCondition());
        }
        for (int i = 0; i < shareNet.getUserIps().size(); i++) {
            List<ShareNet.IpAccessInfo> ipAccessInfoList = shareNet.getUserIps().get(i).getIpAccessInfo();
            for (int i1 = 0; i1 < ipAccessInfoList.size(); i1++) {
                List<ShareNet.Feature> featureList = ipAccessInfoList.get(i1).getFeature();
                Iterator<ShareNet.Feature> iterator = featureList.iterator();
                while (iterator.hasNext()) {
                    ShareNet.Feature next = iterator.next();
                    Condition fieldFilter = null;
                    for (Condition t : condition) {
                        if (next.getFeatureKey().equalsIgnoreCase(t.getFieldKey())) {
                            fieldFilter = t;
                        }
                    }
                    long uniqueCnt = Long.parseLong(next.getUniqueCnt());
                    long filterValue = Long.parseLong(fieldFilter.getFieldValues().get(0).toString());
                    if (((uniqueCnt - filterValue) < 0)
                            && !fieldFilter.getType().equalsIgnoreCase("lt")) {
                        iterator.remove();
                    }
                    if (((uniqueCnt - filterValue) == 0)
                            && !(fieldFilter.getType().equalsIgnoreCase("eq")
                            || fieldFilter.getType().equalsIgnoreCase("ge")
                            || fieldFilter.getType().equalsIgnoreCase("le"))) {
                        iterator.remove();
                    }
                    if (((uniqueCnt - filterValue) > 0)
                            && !(fieldFilter.getType().equalsIgnoreCase("gt") || fieldFilter.getType().equalsIgnoreCase("ge"))) {
                        iterator.remove();
                    }
                }
            }
        }
    }


    private List<ShareNet> formatShareNetResult(List<Map<String, Object>> data, DSLObject dslObject) {
        List<ShareNet> shareNets = new ArrayList<>();
        DSLObject.Query.Parameters parameters = dslObject.getQuery().getParameters();
        for (Map<String, Object> t : data) {
            List<ShareNet.Feature> features = distinctFeature(t, parameters);

            ShareNet.IpAccessInfo ipAccessInfo = new ShareNet.IpAccessInfo();
            List<ShareNet.IpAccessInfo> ipAccessInfoList = new ArrayList<>();
            ipAccessInfo.setType(dslObject.getQuery().getQueryType());
            ipAccessInfo.setFeature(features);
            ipAccessInfoList.add(ipAccessInfo);

            List<ShareNet.UserIp> userIpList = new ArrayList<>();
            ShareNet.UserIp userIp = new ShareNet.UserIp();
            userIp.setIpAccessInfo(ipAccessInfoList);
            userIp.setIp(String.valueOf(t.get("ip")));
            userIpList.add(userIp);
            ShareNet shareNet = new ShareNet();
            shareNet.setUserId(String.valueOf(t.get("user_id")));
            shareNet.setUserIps(userIpList);
            shareNets.add(shareNet);
        }
        shareNets = mergeUserIpOfShareNet(shareNets);
        try {
            uaAnalyse(shareNets);
        } catch (SQLException | RuntimeException e) {
            log.error("UA解析异常:{}", e);
        }
        return shareNets;
    }

    private List<ShareNet.Feature> distinctFeature(Map<String, Object> data, DSLObject.Query.Parameters parameters) {
        List<ShareNet.Feature> features = new ArrayList<>();
        if (StringUtil.isEmpty(parameters.getDistinct()) || CollectionUtils.isEmpty(parameters.getDistinct().getCondition())) {
            return features;
        }
        List<Condition> condition = parameters.getDistinct().getCondition();
        for (Condition term : condition) {
            if (Parser.UA_FILTER.contains(term.getFieldKey())) {
                continue;
            }
            ShareNet.Feature feature = new ShareNet.Feature();
            feature.setFeatureKey(String.valueOf(term.getFieldKey()));
            feature.setUniqueCnt(String.valueOf(data.get(term.getFieldKey().concat("UniqueCnt"))));
            HashMap<String, Object> map = new HashMap<>(16);
            map.put("value", data.get(term.getFieldKey()));
            map.put("frequency", data.get("hits"));
            feature.getCountList().add(map);
            features.add(feature);
        }
        return features;
    }

    private void uaAnalyse(List<ShareNet> shareNets) throws SQLException, RuntimeException {
        for (ShareNet shareNet : shareNets) {
            for (ShareNet.UserIp userIp : shareNet.getUserIps()) {
                for (ShareNet.IpAccessInfo ipAccessInfo : userIp.getIpAccessInfo()) {
                    Iterator<ShareNet.Feature> iteratorFeature = ipAccessInfo.getFeature().iterator();
                    while (iteratorFeature.hasNext()) {
                        ShareNet.Feature feature = iteratorFeature.next();
                        uaParse(shareNet.getUserId(), userIp.getIp(), feature);
                    }
                }
            }
        }
    }

    private void uaParse(String userId, String userIp, ShareNet.Feature feature) throws SQLException {
        if (!"UA".equalsIgnoreCase(feature.getFeatureKey())) {
            return;
        }
        List<String> uaList = new ArrayList<>();
        Iterator<Map<String, Object>> iterator = feature.getCountList().iterator();
        while (iterator.hasNext()) {
            Map<String, Object> next = iterator.next();
            if (StringUtil.isEmpty(next.get("value"))) {
                iterator.remove();
                feature.setUniqueCnt(String.valueOf(feature.getCountList().size()));
                continue;
            }
            uaList.add(String.format("'%s':%s", next.get("value"), next.get("frequency")));
        }
        if (uaList.isEmpty()) {
            return;
        }
        String inputString = userId + "\t" +
                userIp + "\t" +
                (System.currentTimeMillis() / 1000) + "\t" +
                Joiner.on(", ").join(uaList);
        UaAnalyser uaAnalyser = new UaAnalyserImpl();
        UserInfo userInfo = null;
        HashMap<String, Object> t = new HashMap<>();
        try {
            userInfo = uaAnalyser.getUserinfo(inputString);
        } catch (RuntimeException e) {
            log.error("UA解析异常:{}", e);
        }
        if (StringUtil.isEmpty(userInfo)) {
            feature.setAnalyseResult(t);
            return;
        }
        t.put("osCnt", userInfo.getOsCnt());
        t.put("osList", userInfo.getOsList());
        t.put("browserList", userInfo.getBrowserList());
        t.put("browserCnt", userInfo.getBrowserCnt());
        t.put("deviceCnt", userInfo.getDeviceCnt());
        t.put("deviceList", userInfo.getDeviceList());
        t.put("terminalCnt", userInfo.getTerminalCnt());
        t.put("terminalList", userInfo.getTerminalList());
        t.put("terminalCatCnt", userInfo.getTerminalCatCnt());
        t.put("terminalCatList", userInfo.getTerminalCatList());
        feature.setAnalyseResult(t);
    }

    private List<ShareNet> mergeUserIpOfShareNet(List<ShareNet> dataList) {
        List<ShareNet> result = new ArrayList<>();
        dataShareNet:
        for (ShareNet data : dataList) {
            for (ShareNet shareNet : result) {
                if (shareNet.getUserId().equals(data.getUserId())) {
                    for (ShareNet.UserIp userIp : shareNet.getUserIps()) {
                        if (userIp.getIp().equals(data.getUserIps().get(0))) {
                            dataFeature:
                            for (ShareNet.Feature dataFeature : data.getUserIps().get(0).getIpAccessInfo().get(0).getFeature()) {
                                for (ShareNet.Feature feature : userIp.getIpAccessInfo().get(0).getFeature()) {
                                    if (feature.getFeatureKey().equals(dataFeature)) {
                                        feature.getCountList().addAll(dataFeature.getCountList());
                                        List<Map<String, Object>> value = merge(feature.getCountList(), "value");
                                        feature.setCountList(value);
                                        feature.setUniqueCnt(String.valueOf(value.size()));
                                        continue dataFeature;
                                    }
                                }
                                userIp.getIpAccessInfo().get(0).getFeature().add(dataFeature);
                                continue dataFeature;
                            }
                        }
                    }
                    shareNet.getUserIps().add(data.getUserIps().get(0));
                    continue dataShareNet;
                }
            }
            result.add(data);
        }
        return result;
    }

    public List<Map<String, Object>> merge(List<Map<String, Object>> m1, String mergeKey) {
        Set<String> set = new HashSet<>();
        return m1.stream()
                .filter(map -> map.get(mergeKey) != null)
                .collect(Collectors.groupingBy(o -> {
                    //暂存所有key
                    set.addAll(o.keySet());
                    //按mergeKey分组
                    return o.get(mergeKey).toString();
                }))
                .entrySet().stream().map(o -> {
                    //合并
                    Map<String, Object> map = o.getValue().stream().flatMap(m -> {
                        return m.entrySet().stream();
                    }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b));
                    //为没有的key赋值0
                    set.stream().forEach(k -> {
                        if (!map.containsKey(k)) map.put(k, 0);
                    });
                    return map;
                }).collect(Collectors.toList());
    }
}

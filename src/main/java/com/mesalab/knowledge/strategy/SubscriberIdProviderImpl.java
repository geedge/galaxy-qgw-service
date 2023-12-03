package com.mesalab.knowledge.strategy;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.cn.enums.EngineTypeEnum;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.knowledge.entity.*;
import com.mesalab.services.common.dsl.ComDSLObject;
import com.mesalab.services.common.enums.EntityQueryType;
import com.mesalab.services.service.RelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @description:
 * @author: zhq
 * @create: 2020-07-17
 **/
@Component("SUBSCRIBER_ID_VIEW")
public class SubscriberIdProviderImpl implements QueryProvider {

    private static final Log log = LogFactory.get();
    @Autowired
    private RelationService relationService;

    @Override
    public Object query(DSLObject.DSLQuery dslQeruy) {
        BaseResult realRelation = null;
        try {
            Parameters parameters = dslQeruy.getParameters();

            ComDSLObject.Query.QueryBean queryBean = new ComDSLObject.Query.QueryBean();
            queryBean.setMatch(getComMatch(parameters));
            queryBean.setRange(getComRange(parameters));
            queryBean.setSort(getComSort(parameters));
            queryBean.setIntervals(parameters.getIntervals());

            ComDSLObject.Query query = new ComDSLObject.Query();
            query.setDataSource(dslQeruy.getDataSource());
            query.setDataEngine(EngineTypeEnum.ANALYSIS_ENGINE.getEngine());
            query.setLimit(parameters.getLimit());
            query.setParameters(queryBean);

            ComDSLObject comDSLObject = new ComDSLObject();
            comDSLObject.setQuery(query);

            realRelation = relationService.getRealRelation(EntityQueryType.SUBSCRIBERIDPOOL.getType(), comDSLObject);
        } catch (Exception e) {
            log.error(e);
        }
        return realRelation.getData();
    }

    private List<ComDSLObject.Query.FilterBean> getComRange(Parameters parameters) throws Exception {
        List<ComDSLObject.Query.FilterBean> range = new ArrayList<>();
        if (parameters.getRange() != null) {
            for (Range parametersRange : parameters.getRange()) {
                ComDSLObject.Query.FilterBean filterBean = new ComDSLObject.Query.FilterBean();
                filterBean.setType(parametersRange.getType());
                filterBean.setFieldKey(parametersRange.getFieldKey());
                filterBean.setFieldValues(parametersRange.getFieldValues().stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList()));
                range.add(filterBean);
            }
        }

        return range;
    }

    private List<ComDSLObject.Query.FilterBean> getComMatch(Parameters parameters) throws Exception {
        List<ComDSLObject.Query.FilterBean> match = new ArrayList<>();
        if (parameters.getMatch() != null) {
            for (Match parametersMatch : parameters.getMatch()) {
                ComDSLObject.Query.FilterBean filterBean = new ComDSLObject.Query.FilterBean();
                filterBean.setType(parametersMatch.getType());
                filterBean.setFieldKey(parametersMatch.getFieldKey());
                filterBean.setFieldValues(parametersMatch.getFieldValues().stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList()));
                match.add(filterBean);
            }
        }

        return match;
    }

    private List<ComDSLObject.Query.SortBean> getComSort(Parameters parameters) throws Exception {
        List<ComDSLObject.Query.SortBean> sort = new ArrayList<>();
        if (parameters.getSort() != null) {
            for (Sort parametersSort : parameters.getSort()) {
                ComDSLObject.Query.SortBean sortBean = new ComDSLObject.Query.SortBean();
                sortBean.setType(parametersSort.getType());
                sortBean.setFieldKey(parametersSort.getFieldKey());
                sort.add(sortBean);
            }
        }
        return sort;
    }

}
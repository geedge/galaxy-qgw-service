package com.mesalab.services.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.nacos.NacosConst;
import com.mesalab.qgw.model.basic.QueryProfile;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.qgw.service.QueryService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.service.SQLDatasetService;
import com.zdjizhi.utils.StringUtil;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * TODO
 *
 * @Classname SQLDatasetServiceImp
 * @Date 2023/8/10 13:54
 * @Author wWei
 */
@Service("SQLDatasetService")
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class SQLDatasetServiceImp
        implements SQLDatasetService
{
    private static final Log log = LogFactory.get();
    @Autowired
    Environment env;
    @Autowired
    MetadataService metadataService;
    @Autowired
    QueryService queryService;

    @Override
    public List<LinkedHashMap> getVariable()
    {
        Object codeInfo = metadataService.getCfg(NacosConst.SQL_DATASETS_VARIABLES);
        if (StringUtil.isNotEmpty(codeInfo)) {
            Object json = JSON.toJSON(codeInfo);
            return JSON.parseArray(json.toString(), LinkedHashMap.class);
        }
        return Lists.newArrayList();
    }

    @Override
    public Map<String, Object> getDataset(String datasetId)
    {
        List<Map<String, Object>> results = new ArrayList<>();
        List<Record> list = Db.find(buildSQL(Lists.newArrayList(datasetId), null, null));
        if (CollectionUtils.isEmpty(list)) {
            return Maps.newHashMap();
        }
        list.forEach(record -> results.add(record.getColumns()));
        return results.get(0);
    }

    @Override
    public Map<String, Object> getDatasets(List<String> ids, String category, String title)
    {
        String sql = buildSQL(ids, category, title);
        List<Map<String, Object>> data = new ArrayList<>();
        List<Record> list = Db.find(sql);
        if (CollectionUtils.isEmpty(list)) {
            return Maps.newHashMap();
        }
        list.forEach(record -> data.add(record.getColumns()));
        Map<String, Object> result = Maps.newHashMap();
        result.put("list", data);
        return result;
    }

    @Override
    public BaseResult getPreview(String datasetId)
    {
        List<LinkedHashMap> variables = getVariable();

        Map<String, Object> dataset = getDataset(datasetId);
        if (dataset.isEmpty() || StrUtil.isBlankIfStr(dataset.get("sql_text"))) {
            return BaseResultGenerator.success();
        }
        String sql = String.valueOf(dataset.get("sql_text"));
        for (LinkedHashMap linkedHashMap : variables) {
            String variable = String.valueOf(linkedHashMap.get("key"));
            String def = String.valueOf(linkedHashMap.get("default"));
            if ("filter".equals(variable) && StringUtil.isBlank(def)) {
                def = " 1 = 1";
            }
            String parameter = "${".concat(variable).concat("}");
            sql = sql.replace(parameter, def);
        }
        return queryService.executeQuery(QueryProfile.builder().query(sql).build());
    }

    private String buildSQL(List<String> ids, String category, String title)
    {
        List<String> filterList = Lists.newArrayList();
        if (!ids.isEmpty()) {
            filterList.add(" identifier_name IN ( '".concat(String.join("', '", ids)).concat("')"));
        }
        if (StrUtil.isNotBlank(category)) {
            filterList.add(" category = '".concat(category.replace("'", "\\'")).concat("'"));
        }
        if (StrUtil.isNotBlank(title)) {
            filterList.add(" title = '".concat(title.replace("'", "\\'")).concat("'"));
        }
        String filter = String.join(" AND ", filterList);
        return String.format(Objects.requireNonNull(env.getProperty("SQL_DATASETS")), StrUtil.isNotBlank(filter) ? "WHERE ".concat(filter) : "");
    }
}

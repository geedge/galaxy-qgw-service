package com.mesalab.openapi.service.impl;

import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.openapi.service.OpenApiService;
import com.mesalab.qgw.service.SystemService;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Date: 2020-09-18 11:00
 * @Author : liuyongqiang
 * @ClassName : OpenApiServiceImpl
 * @Description : OpenApiServiceImpl
 */
@Service("openApiService")
public class OpenApiServiceImpl implements OpenApiService {

    @Autowired
    SystemService systemService;

    /**
     * @Description: APP字典列表
     * @Author: liuyongqiang
     * @Date: 2020/9/17 15:31
     * @return: com.mesalab.common.base.BaseResult
     **/
    @Override
    public BaseResult appDicList() {
        List<?> list = Db.query(Db.getSql("appDicList"));
        return BaseResultGenerator.success(list);
    }

    /**
     * @param id: 报告ID
     * @Description: 查询报告结果
     * @Author: liuyongqiang
     * @Date: 2020/10/16 17:45
     * @return: com.mesalab.common.base.BaseResult
     **/
    @Override
    public BaseResult reportResults(Integer id) {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = Db.getSql("reportResults");
        List<Record> list = Db.find(sql, id);
        if (CollectionUtils.isEmpty(list)) return BaseResultGenerator.success();

        list.stream().forEach(record -> {
            String querySql = record.get("query_sql");
            int resultId = record.getInt("result_id");
            String queryId = systemService.getQueryId(String.valueOf(resultId), querySql);
            record.set("query_id", queryId);
            results.add(record.getColumns());
        });

        return BaseResultGenerator.success(results);
    }
}

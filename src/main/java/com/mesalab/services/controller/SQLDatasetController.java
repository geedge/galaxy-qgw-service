package com.mesalab.services.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.geedgenetworks.utils.StringUtil;
import com.google.common.collect.Lists;
import com.mesalab.common.entity.BaseResult;

import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.services.service.SQLDatasetService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * SQL数据集市管理接口
 *
 * @Classname SQLDatasetController
 * @Date 2023/8/10 11:06
 * @Author wWei
 */
@RestController
@RequestMapping(value = "/v1")
public class SQLDatasetController
{
    private static final Log log = LogFactory.get();
    @Autowired
    SQLDatasetService sqlDatasetService;

    @DeleteMapping(value = "sql/query/{query_id}")
    public BaseResult killQuery(@PathVariable("query_id") String queryId) {
        log.warn("停止查询, 参数: queryId is {}", queryId);
        if (StringUtil.isBlank(queryId)) {
            return BaseResultGenerator.success4Message("ok");
        }
        return sqlDatasetService.deleteQueryTask(queryId);
    }


    @RequestMapping(value = "sql/query/query_id")
    public BaseResult getQueryId(@RequestParam Map<String, Object> param) {
        log.info("获取当前SQL Query ID, 参数: {}", param);
        Object resultId, query;
        if (StringUtil.isEmpty(resultId = param.get("result_id"))
                || StringUtil.isEmpty(query = param.get("query"))) {

            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.RESULTID_OR_QUERY_CANNOT_BE_EMPTY));
        }
        String queryId = sqlDatasetService.getCustomQueryId(resultId.toString(), query.toString());

        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> map = new HashMap<>(16);
        map.put("queryId", queryId);
        result.add(map);
        return BaseResultGenerator.success(result);
    }

    @GetMapping(value = "sql/query/{query_id}/progress/")
    public BaseResult getProcessesByQueryId(@PathVariable("query_id") String queryId) {
        log.info("查询SQL任务执行状态, 参数: queryId is {}", queryId);
        return sqlDatasetService.getProcessesByQueryId(queryId);
    }

    @GetMapping(value = "/dataset")
    public BaseResult getDatasets(@RequestParam(value = "identifier_names",required = false) String datasetIds, @RequestParam(required = false) String category, @RequestParam(required = false) String title)
    {
        log.info("Get Datasets. IDs is: {}, category is: {}, title is: {}", datasetIds, category, title);
        return BaseResultGenerator.success(sqlDatasetService.getDatasets(Arrays.asList(StrUtil.split(datasetIds, ",")), category, title));
    }

    @GetMapping(value = "/dataset/{identifier_name}")
    public BaseResult getDataset(@PathVariable("identifier_name") String identifierName)
    {
        log.info("Get Dataset, ID is: {}", identifierName);
        return BaseResultGenerator.success(sqlDatasetService.getDataset(identifierName));
    }


    @GetMapping(value = "/dataset/{identifier_name}/preview")
    public BaseResult getPreview(@PathVariable("identifier_name") String identifierName,@RequestParam(required = false) String option)
    {
        return sqlDatasetService.getPreview(identifierName,option);
    }

    @GetMapping(value = "/dataset/global_variable")
    public BaseResult getVariable()
    {
        return BaseResultGenerator.success(sqlDatasetService.getVariable());
    }
}
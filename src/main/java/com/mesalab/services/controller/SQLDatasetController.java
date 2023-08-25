package com.mesalab.services.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Lists;
import com.mesalab.common.entity.BaseResult;

import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.services.service.SQLDatasetService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * SQL数据集市管理接口
 *
 * @Classname SQLDatasetController
 * @Date 2023/8/10 11:06
 * @Author wWei
 */
@RestController
@RequestMapping(value = "/v1/sql")
public class SQLDatasetController
{

    @Autowired
    SQLDatasetService sqlDatasetService;

    @GetMapping(value = "/datasets/variables")
    public BaseResult getVariable()
    {
        return BaseResultGenerator.success(sqlDatasetService.getVariable());
    }

    @GetMapping(value = "/datasets/{datasetId}")
    public BaseResult getDataset(@PathVariable String datasetId)
    {
        return BaseResultGenerator.success(sqlDatasetService.getDataset(datasetId));
    }

    @GetMapping(value = "/datasets")
    public BaseResult getDatasets(@RequestParam(value = "identifier_names") String datasetIds, String category, String title)
    {
        return BaseResultGenerator.success(sqlDatasetService.getDatasets(Arrays.asList(StrUtil.split(datasetIds, ",")), category, title));
    }

    @GetMapping(value = "/datasets/{datasetId}/preview")
    public BaseResult getPreview(@PathVariable String datasetId)
    {
        return sqlDatasetService.getPreview(datasetId);
    }
}
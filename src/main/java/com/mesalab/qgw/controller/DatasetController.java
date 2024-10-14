package com.mesalab.qgw.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import com.google.common.collect.Lists;
import com.mesalab.common.entity.BaseResult;

import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.DBEngineType;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.AuditLog;
import com.mesalab.qgw.service.DatasetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping(value = "/v1/datasets")
public class DatasetController {

    @Autowired
    private DatasetService datasetService;

    @GetMapping(value = "/{identifier_name}")
    @AuditLog("DatasetController.getDataset")
    public BaseResult getDataset(@PathVariable("identifier_name") String identifierName) {
        log.info("Get Dataset, ID is: {}", identifierName);
        Map<String, Object> dataset = datasetService.getDataset(identifierName);
        return BaseResultGenerator.success(Lists.newArrayList(dataset));
    }

    @GetMapping
    @AuditLog("DatasetController.getDatasets")
    public BaseResult getDatasets(@RequestParam(value = "identifier_names", required = false) String datasetIds, @RequestParam(required = false) String category, @RequestParam(value = "backend_engine", required = false) String backendEngine) {
        if (StrUtil.isNotBlank(backendEngine)) {
            if (Arrays.stream(DBEngineType.values()).noneMatch(o -> o.getValue().equalsIgnoreCase(backendEngine))) {
                throw new QGWBusinessException(HttpStatus.HTTP_BAD_REQUEST, CommonErrorCode.PARAMETER_ERROR.getCode(),
                        String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.DATASET_BACKEND_ENGINE_ERROR));
            }
        }
        log.info("Get Datasets. IDs is: {}, category is: {}, backendEngine is: {}", datasetIds, category, backendEngine);
        return BaseResultGenerator.success(datasetService.getDatasets(Arrays.asList(StrUtil.split(datasetIds, ",")), category, backendEngine));
    }

    @GetMapping(value = "/{identifier_name}/result-preview")
    @AuditLog("DatasetController.getPreview")
    public BaseResult getPreview(@PathVariable("identifier_name") String identifierName) {
        return datasetService.getPreview(identifierName);
    }

    @GetMapping(value = "/global-variable")
    @AuditLog("DatasetController.getVariable")
    public BaseResult getVariable() {
        return BaseResultGenerator.success(datasetService.getVariable());
    }
}
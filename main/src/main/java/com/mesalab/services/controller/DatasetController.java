package com.mesalab.services.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.geedgenetworks.utils.StringUtil;
import com.mesalab.common.entity.BaseResult;

import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.services.service.DatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping(value = "/v1/dataset")
public class DatasetController {
    private static final Log log = LogFactory.get();
    @Autowired
    private DatasetService datasetService;


    @GetMapping(value = "/{identifier_name}")
    public BaseResult getDataset(@PathVariable("identifier_name") String identifierName) {
        log.info("Get Dataset, ID is: {}", identifierName);
        return BaseResultGenerator.success(datasetService.getDataset(identifierName));
    }

    @GetMapping
    public BaseResult getDatasets(@RequestParam(value = "identifier_names", required = false) String datasetIds, @RequestParam(required = false) String category, @RequestParam(value = "backend_engine", required = false) String backendEngine) {
        if (StrUtil.isNotBlank(backendEngine)) {
            if (Arrays.stream(DBTypeEnum.values()).noneMatch(o -> o.getValue().equalsIgnoreCase(backendEngine))) {
                throw new QGWBusinessException(HttpStatus.HTTP_BAD_REQUEST, ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.DATASET_BACKEND_ENGINE_ERROR));
            }
        }
        log.info("Get Datasets. IDs is: {}, category is: {}, backendEngine is: {}", datasetIds, category, backendEngine);
        return BaseResultGenerator.success(datasetService.getDatasets(Arrays.asList(StrUtil.split(StringUtil.removeAll(datasetIds,"\'"), ",")), category, backendEngine));
    }

    @GetMapping(value = "/{identifier_name}/result_preview")
    public BaseResult getPreview(@PathVariable("identifier_name") String identifierName) {
        return datasetService.getPreview(identifierName);
    }

    @GetMapping(value = "/dataset/global_variable")
    public BaseResult getVariable() {
        return BaseResultGenerator.success(datasetService.getVariable());
    }
}
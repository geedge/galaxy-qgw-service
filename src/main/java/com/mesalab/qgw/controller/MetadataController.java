package com.mesalab.qgw.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.qgw.service.MetadataService;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(value = "/metadata")
public class MetadataController {

    @Autowired
    private MetadataService metadataService;


    @RequestMapping("/schema/v1/{type}/{name}")
    public BaseResult getSchema(@PathVariable String type, @PathVariable String name) {
        log.debug("SCHEMA信息获取,参数为{} {}", type, name);

        if (StringUtil.isBlank(type) || StringUtil.isBlank(name)) {

            return BaseResultGenerator.success4Message("ok");
        }
        return BaseResultGenerator.success(metadataService.getSchemaInfo(type, name));
    }
}

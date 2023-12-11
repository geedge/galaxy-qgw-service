package com.mesalab.qgw.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.service.HosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/v1/hos")
public class HosController {
    private static final Log log = LogFactory.get();

    @Autowired
    private HosService hosService;

    @GetMapping(value = "/{bucket_name}", consumes = "application/x-www-form-urlencoded")
    public BaseResult getFileList(@PathVariable("bucket_name") String bucketName, String prefix) {
        if (StrUtil.isNotBlank(bucketName) && StrUtil.isNotBlank(prefix)) {
            log.info("Get File List interface,param:bucket_name is {},prefix is {}", bucketName, prefix);
            return hosService.getFileList(bucketName, prefix);
        } else {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.BUCKET_AND_PREFIX_ERROR));
        }
    }
}

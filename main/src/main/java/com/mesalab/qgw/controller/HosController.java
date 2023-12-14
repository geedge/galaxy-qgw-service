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
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(value = "/v1/hos")
public class HosController {
    private static final Log log = LogFactory.get();
    private static final String X_HOS_META_PREFIX = "x-hos-meta-";

    @Autowired
    private HosService hosService;

    @GetMapping(value = "/{bucket_name}", consumes = "application/x-www-form-urlencoded")
    public BaseResult getFileList(@PathVariable("bucket_name") String bucketName, @RequestParam(required = false) String prefix, HttpServletRequest request) {
        if (StrUtil.isNotBlank(bucketName)) {
            Map<String, String> udfMetaParam = new HashMap<>();
            Enumeration<String> parameterNames = request.getParameterNames();
            while (parameterNames.hasMoreElements()) {
                String paramName = parameterNames.nextElement();
                if (paramName.startsWith(X_HOS_META_PREFIX)) {
                    String paramValue = request.getParameter(paramName);
                    udfMetaParam.put(paramName, paramValue);
                }
            }
            log.info("Get File List interface,param:bucket_name is {},prefix is {},x-hos-meta-* is {}", bucketName, prefix, udfMetaParam.toString());
            return hosService.getFileList(bucketName, prefix, udfMetaParam);
        } else {
            throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.BUCKET_NAME_ERROR));
        }
    }
}

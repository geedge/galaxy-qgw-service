package com.mesalab.qgw.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.AuditLog;
import com.mesalab.qgw.service.HosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "/v1/hos")
public class HosController {
    private static final Log log = LogFactory.get();
    private static final String X_HOS_META_PREFIX = "x-hos-meta-";

    private HosService hosService;


    @GetMapping(value = "/{bucket_name}/{file_name}", consumes = "application/x-www-form-urlencoded")
    @AuditLog("HosController.getFile")
    public BaseResult getFile(@PathVariable("bucket_name") String bucketName, @PathVariable("file_name") String fileName) {
        return hosService.getFile(bucketName, fileName);
    }

    @GetMapping(value = "/{bucket_name}", consumes = "application/x-www-form-urlencoded")
    @AuditLog("HosController.getFileList")
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
            throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.BUCKET_NAME_ERROR));
        }
    }

    @PostMapping(value = "/{bucket_name}/{file_name}", consumes = "multipart/form-data")
    @AuditLog("HosController.uploadFile")
    public BaseResult uploadFile(@PathVariable("bucket_name") String bucketName, @PathVariable("file_name") String fileName, @RequestParam("file") MultipartFile file) {
        ByteArrayInputStream byteArrayInputStream = null;
        try {
            byteArrayInputStream = new ByteArrayInputStream(file.getBytes());
        } catch (IOException e) {
            throw new BusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.UNKNOWN_EXCEPTION.getMessage(), e.getMessage()));
        }
        return hosService.uploadFile(bucketName, fileName, byteArrayInputStream, null);
    }

    @DeleteMapping(value = "/{bucket_name}", consumes = "application/x-www-form-urlencoded")
    @AuditLog("HosController.deleteFileList")
    public BaseResult deleteFileList(@PathVariable("bucket_name") String bucketName, @RequestParam("file_names") String fileNames) {
        return hosService.deleteFileList(bucketName, Arrays.stream(fileNames.split(",")).distinct().collect(Collectors.toList()));
    }


    @Autowired
    public void setHosService(HosService hosService) {
        this.hosService = hosService;
    }
}

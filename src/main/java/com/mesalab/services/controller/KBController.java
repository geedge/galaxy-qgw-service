package com.mesalab.services.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.FileFormat;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.model.basic.AuditLog;
import com.mesalab.services.common.entity.KnowledgeBaseRequest;
import com.mesalab.services.service.KBService;
import com.geedgenetworks.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping(value = "/v1/knowledge_base")
public class KBController {
    private static final Log log = LogFactory.get();
    @Autowired
    KBService kbService;

    @PostMapping(produces = "application/json", consumes = "multipart/form-data")
    @AuditLog("KBController.publishKnowledgeBase")
    public BaseResult publishKnowledgeBase(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        try {
            KnowledgeBaseRequest KBRequest = getKnowledgeBaseRequest(request);
            validateRequest(KBRequest);
            log.info("Knowledge Base File publish interface:param is :{}", request.toString());
            return kbService.publishKnowledge(file, KBRequest);
        } catch (Exception e) {
            log.error("Knowledge Base File publish error: {}", e.getMessage());
            return BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), "Knowledge Base File publish: " + e.getMessage());
        }
    }

    @PutMapping(value = {"/{kb_Id}"},consumes = "multipart/form-data")
    @AuditLog("KBController.updateKnowledgeBase")
    public BaseResult updateKnowledgeBase(@PathVariable(value = "kb_Id",required = false) String kbId,@RequestPart(value = "file") MultipartFile file,HttpServletRequest request) {
        try {
            KnowledgeBaseRequest KBRequest = getKnowledgeBaseRequest(request);
            if (StringUtil.isBlank(kbId)) {
                throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                        String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.KB_FILE_ID_CANNOT_BE_EMPTY));
            }
            if (StringUtil.isBlank(KBRequest.getVersion())) {
                throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                        String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.KB_FILE_VERSION_CANNOT_BE_EMPTY));
            }
            KBRequest.setKbId(kbId);
            log.info("Knowledge Base File update interface:param is :{}", request.toString());
            return kbService.updateKnowledge(file, KBRequest);
        } catch (Exception e) {
            log.error("Knowledge Base File update error: {}", e.getMessage());
            return BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), "Knowledge Base File update error: " + e.getMessage());
        }
    }

    @DeleteMapping(value = "/{kb_id}",consumes = "application/x-www-form-urlencoded")
    @AuditLog("KBController.deleteKnowledgeBase")
    public BaseResult deleteKnowledgeBase(@PathVariable(value = "kb_id") String kbId, @RequestParam(required = false) String version) {
        try {
            KnowledgeBaseRequest request = new KnowledgeBaseRequest();
            if (StrUtil.isBlankIfStr(kbId)){
                throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                        String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.KB_FILE_ID_CANNOT_BE_EMPTY));
            }
            if (StrUtil.isNotBlank(version)){
                request.setVersion(version);
            }
            request.setKbId(kbId);
            log.info("Knowledge Base File delete interface,param is:{}",request.toString());
            return kbService.deleteKnowledge(request);
        } catch (Exception e) {
            log.error("Knowledge Base File delete error: {}", e.getMessage());
            return BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), "Knowledge Base File delete error:" + e.getMessage());
        }
    }

    @PutMapping(value = "/{kb_id}/status",consumes = "application/x-www-form-urlencoded")
    @AuditLog("KBController.updateStatusKnowledgeBase")
    public BaseResult updateStatusKnowledgeBase(@PathVariable(value = "kb_id") String kbId, HttpServletRequest request) {
        try {
            KnowledgeBaseRequest KBRequest = getKnowledgeBaseRequest(request);
            if (StrUtil.isBlankIfStr(kbId)){
                throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                        String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.KB_FILE_ID_CANNOT_BE_EMPTY));
            }
            if (StrUtil.isBlank(String.valueOf(KBRequest.getIsValid()))){
                throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                        String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.KB_FILE_IS_VALID_CANNOT_BE_EMPTY));
            }
            KBRequest.setKbId(kbId);
            log.info("Knowledge Base File update status interface,param is:{}",KBRequest.toString());
            return kbService.updateStatus(KBRequest);
        } catch (Exception e) {
            log.error("Knowledge Base File update status error: {}", e.getMessage());
            return BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), "Knowledge Base File update status error: " + e.getMessage());
        }
    }

    @GetMapping(produces = "application/json")
    @AuditLog("KBController.getList")
    public BaseResult getList(@RequestParam(required = false, value = "kb_id") String kbId) {
        try {
            log.info("Knowledge Base File get list interface,param is: kb_id= {}", kbId);
            return kbService.getList(kbId);
        } catch (BusinessException e) {
            log.error("Knowledge Base File get list error:{}", e.getMessage());
            return BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), "Knowledge Base File get list error: " + e.getMessage());
        }
    }

    private static KnowledgeBaseRequest getKnowledgeBaseRequest(HttpServletRequest request) {
        Map<String, String> paramMap = new HashMap<>();
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String paramValue = request.getParameter(paramName);
            paramMap.put(paramName, paramValue);
        }
        KnowledgeBaseRequest KBRequest = JSON.parseObject(JSON.toJSONString(JSON.toJSON(paramMap)), KnowledgeBaseRequest.class);
        return KBRequest;
    }
    private KnowledgeBaseRequest validateRequest(KnowledgeBaseRequest request){
        if (StrUtil.isBlankIfStr(request.getName())){
            throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.KB_FILE_NAME_CANNOT_BE_EMPTY));
        }
        if (StrUtil.isBlankIfStr(request.getFormat())){
            throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.KB_FILE_FORMAT_CANNOT_BE_EMPTY));
        }else {
            if (Arrays.stream(FileFormat.values()).noneMatch(o -> o.getValue().equalsIgnoreCase(request.getFormat()))){
                throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                        String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.KB_FILE_FORMAT_NOT_SUPPORT));
            }
        }
        if (StrUtil.isBlankIfStr(String.valueOf(request.getIsValid()))){
            request.setIsValid(1);
        }
        return request;
    }
}

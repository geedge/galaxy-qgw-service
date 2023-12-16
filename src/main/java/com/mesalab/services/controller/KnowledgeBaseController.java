package com.mesalab.services.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.services.common.entity.KnowledgeBase;
import com.mesalab.services.service.KnowledgeBaseService;
import com.geedgenetworks.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping(value = "/knowledge_base/v1")
public class KnowledgeBaseController {
    private static final Log log = LogFactory.get();
    @Autowired
    KnowledgeBaseService knowledgeService;

    @GetMapping(produces = "application/json")
    public BaseResult queryKnowledge() {
        try {
            return knowledgeService.queryKnowledge();
        } catch (BusinessException e) {
            log.error("get knowledge meta error:{}", e.getMessage());
            return BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), "get knowledge meta error: " + e.getMessage());
        }
    }


    @PostMapping(produces = "application/json")
    public BaseResult publishKnowledge(@RequestParam("file") MultipartFile file, KnowledgeBase knowledge) {
        KnowledgeBase knowledgeBase = validateKnowledge(knowledge);
        try {
            return knowledgeService.publishKnowledge(file, knowledgeBase);
        } catch (Exception e) {
            log.error("publish knowledge error: {}", e.getMessage());
            return BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), "publish knowledge error: " + e.getMessage());
        }
    }

    @PutMapping(value = {"/{id}", ""})
    public BaseResult updateKnowledge(@PathVariable(required = false) String id, @RequestPart(value = "file") MultipartFile file, KnowledgeBase knowledge) {
        try {
            if (StringUtil.isNotBlank(id)) {
                knowledge.setId(id);
            }
            return knowledgeService.updateKnowledge(file, knowledge);
        } catch (Exception e) {
            log.error("update knowledge error: {}", e);
            return BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), "update knowledge error: " + e.getMessage());
        }
    }

    @DeleteMapping(value = {"/{id}", ""})
    public BaseResult deleteKnowledge(@PathVariable(required = false) String id, KnowledgeBase knowledgeBase) {
        try {
            return knowledgeService.deleteKnowledge(StringUtil.isNotBlank(id) ? id : knowledgeBase.getId());
        } catch (Exception e) {
            log.error("delete knowledge error: {}", e.getMessage());
            return BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), "delete knowledge error:" + e.getMessage());
        }
    }

    @PatchMapping(value = "/{id}")
    public BaseResult toggleKnowledge(@PathVariable String id, KnowledgeBase knowledgeBase) {
        try {
            if (StringUtil.isNotBlank(id)) {
                knowledgeBase.setId(id);
            }
            return knowledgeService.toggleKnowledge(knowledgeBase);
        } catch (Exception e) {
            log.error("toggle knowledge error: {}", e.getMessage());
            return BaseResultGenerator.failure(HttpStatusCodeEnum.SERVER_ERROR.getCode(), "toggle knowledge error:" + e.getMessage());
        }
    }

    private KnowledgeBase validateKnowledge(KnowledgeBase knowledge) {
        if (StringUtil.isEmpty(knowledge.getName())) {
            throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),"Name is required");
        }
        if (knowledge.getName().contains(":")) {
            throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),"Name cannot contain ':'");
        }
        if (knowledge.getName().contains(".")) {
            throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),"Name cannot contain '.'");
        }
        if (StringUtil.isEmpty(knowledge.getFormat())) {
            throw new BusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),"Format is required");
        }
        return knowledge;
    }

}

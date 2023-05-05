package com.mesalab.services.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.services.common.entity.KnowledgeBase;
import com.mesalab.services.service.KnowledgeBaseService;
import com.zdjizhi.utils.StringUtil;
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
            return BaseResultGenerator.failure(ResultStatusEnum.SERVER_ERROR.getCode(), "get knowledge meta error: " + e.getMessage());
        }
    }


    @PostMapping(produces = "application/json")
    public BaseResult publishKnowledge(@RequestParam("file") MultipartFile file, KnowledgeBase knowledge) {
        try {
            KnowledgeBase knowledgeBase = validateKnowledge(knowledge);
            return knowledgeService.publishKnowledge(file, knowledgeBase);
        } catch (Exception e) {
            log.error("publish knowledge error: {}", e.getMessage());
            return BaseResultGenerator.failure(ResultStatusEnum.SERVER_ERROR.getCode(), "publish knowledge error: " + e.getMessage());
        }
    }

    @PutMapping
    public BaseResult updateKnowledge(@RequestPart(value = "file") MultipartFile file, KnowledgeBase knowledge) {

        try {
            return knowledgeService.updateKnowledge(file, knowledge);
        } catch (Exception e) {
            log.error("update knowledge error: {}", e);
            return BaseResultGenerator.failure(ResultStatusEnum.SERVER_ERROR.getCode(), "update knowledge error: " + e.getMessage());
        }
    }

    @DeleteMapping
    public BaseResult deleteKnowledge(@RequestParam String id) {

        try {
            return knowledgeService.deleteKnowledge(id);
        } catch (Exception e) {
            log.error("delete knowledge error: {}", e.getMessage());
            return BaseResultGenerator.failure(ResultStatusEnum.SERVER_ERROR.getCode(), "delete knowledge error:" + e.getMessage());
        }
    }

    @PatchMapping
    public BaseResult toggleKnowledge(KnowledgeBase knowledgeBase) {
        try {
            return knowledgeService.toggleKnowledge(knowledgeBase);
        } catch (Exception e) {
            log.error("toggle knowledge error: {}", e.getMessage());
            return BaseResultGenerator.failure(ResultStatusEnum.SERVER_ERROR.getCode(), "toggle knowledge error:" + e.getMessage());
        }
    }

    private KnowledgeBase validateKnowledge(KnowledgeBase knowledge) {
        if (StringUtil.isEmpty(knowledge.getName())) {
            throw new BusinessException("Name is required");
        }
        if (knowledge.getName().contains(":")) {
            throw new BusinessException("Name cannot contain ':'");
        }
        if (knowledge.getName().contains(".")) {
            throw new BusinessException("Name cannot contain '.'");
        }
        if (StringUtil.isEmpty(knowledge.getFormat())) {
            throw new BusinessException("Format is required");
        }
        return knowledge;
    }

}

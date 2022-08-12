package com.mesalab.knowledge.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.knowledge.entity.KnowledgeBase;
import com.mesalab.knowledge.service.impl.KnowledgeBaseService;
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
            log.error("查询知识库失败:{}", e);
            return BaseResultGenerator.failure(ResultStatusEnum.SERVER_ERROR.getCode(), "failed to query:" + e.getErrorMessage());
        }

    }


    @PostMapping(produces = "application/json")
    public BaseResult publishKnowledge(@RequestParam("file") MultipartFile file,
                                   KnowledgeBase knowledge) {

        try {
            KnowledgeBase knowledgeBase = validateKnowledge(knowledge);
            return knowledgeService.publishKnowledge(file, knowledgeBase);
        } catch (BusinessException e) {
            log.error("发布知识库失败:{}", e);
            return BaseResultGenerator.failure(ResultStatusEnum.SERVER_ERROR.getCode(), "failed to upload file:" + e.getErrorMessage());
        }
    }

    @PutMapping
    public BaseResult updateKnowledge(@RequestPart(value = "file") MultipartFile file, KnowledgeBase knowledge) {

        try {
            return knowledgeService.updateKnowledge(file, knowledge);
        } catch (BusinessException e) {
            log.error("更新知识库失败:{}", e);
            return BaseResultGenerator.failure(ResultStatusEnum.SERVER_ERROR.getCode(), "failed to upload file:" + e.getErrorMessage());
        }
    }

    @DeleteMapping
    public BaseResult deleteKnowledge(@RequestParam String id) {

        try {
            return knowledgeService.deleteKnowledge(id);
        } catch (BusinessException e) {
            log.error("删除知识库失败:{}", e);
            return BaseResultGenerator.failure(ResultStatusEnum.SERVER_ERROR.getCode(), "failed to delete file:" + e.getErrorMessage());
        }
    }

    private KnowledgeBase validateKnowledge(KnowledgeBase knowledge) {
        if (StringUtil.isEmpty(knowledge.getName())) {
            throw new BusinessException("Name is required");
        }
        if (knowledge.getName().contains(":")){
            throw new BusinessException("Name cannot contain ':'");
        }
        if (StringUtil.isEmpty(knowledge.getFormat())) {
            throw new BusinessException("Format is required");
        }
        if (!StringUtil.equals(knowledge.getType(),"ip_location")){
            throw new BusinessException("ip_location is failed");
        }
        if (StringUtil.isEmpty(knowledge.getVersion())) {
            knowledge.setVersion("null");
        }
        return knowledge;
    }

}

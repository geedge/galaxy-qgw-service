package com.mesalab.knowledge.service.impl;

import com.mesalab.common.base.BaseResult;
import com.mesalab.knowledge.entity.KnowledgeBase;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author wxs
 * @Date 2022/7/11
 */
public interface KnowledgeBaseService {
    BaseResult addKnowledge(MultipartFile file, KnowledgeBase knowledge);

    BaseResult knowledgeQuery();

    BaseResult updateKnowledge(MultipartFile file, KnowledgeBase knowledgeBase);

    BaseResult deleteKnowledge(String id);
}

package com.mesalab.services.service;

import com.mesalab.common.base.BaseResult;
import com.mesalab.services.common.entity.KnowledgeBase;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author wxs
 * @Date 2022/7/11
 */
public interface KnowledgeBaseService {
    BaseResult publishKnowledge(MultipartFile file, KnowledgeBase knowledge);

    BaseResult queryKnowledge();

    BaseResult updateKnowledge(MultipartFile file, KnowledgeBase knowledgeBase);

    BaseResult deleteKnowledge(String id);
}

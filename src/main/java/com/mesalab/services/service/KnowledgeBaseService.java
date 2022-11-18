package com.mesalab.services.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.services.common.entity.KnowledgeBase;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author wxs
 * @Date 2022/7/11
 */
public interface KnowledgeBaseService {
    BaseResult queryKnowledge();

    BaseResult publishKnowledge(MultipartFile file, KnowledgeBase knowledge) throws Exception;

    BaseResult updateKnowledge(MultipartFile file, KnowledgeBase knowledgeBase) throws Exception;

    BaseResult deleteKnowledge(String id) throws Exception;
}

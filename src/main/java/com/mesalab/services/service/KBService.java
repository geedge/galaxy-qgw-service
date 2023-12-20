package com.mesalab.services.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.services.common.entity.KnowledgeBaseRequest;
import org.springframework.web.multipart.MultipartFile;

public interface KBService {
    BaseResult publishKnowledge(MultipartFile file, KnowledgeBaseRequest request);

    BaseResult updateKnowledge(MultipartFile file, KnowledgeBaseRequest request);

    BaseResult deleteKnowledge(KnowledgeBaseRequest request);

    BaseResult updateStatus(KnowledgeBaseRequest request);

    BaseResult getList(String kbId);
}

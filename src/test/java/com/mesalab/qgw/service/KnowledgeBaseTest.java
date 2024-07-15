package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.services.common.entity.KnowledgeBaseRequest;
import com.mesalab.services.common.entity.KnowledgeConstant;
import com.mesalab.services.service.KBService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;

/**
 * @Author wxs
 * @Date 2022/10/9
 */
@EnableAutoConfiguration
public class KnowledgeBaseTest extends GalaxyQGWApplicationTests {

    @Autowired
    private KBService kbService;


    @Test
    public void query() {
        BaseResult result = kbService.getList(null, null);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

    @Test
    public void cycle() throws Exception {
        publish();
        updateFile();
        updateStatus();
        delete();

    }

    private void publish() throws Exception {
        KnowledgeBaseRequest knowledgeBase = jsonToInParameter("parameters/knowledgeBase.json", "publish", KnowledgeBaseRequest.class);
        File file = new File(KnowledgeConstant.DAT + File.separator + KnowledgeConstant.ASN_BUILTIN + KnowledgeConstant.MMDB);
        FileInputStream inputStream = new FileInputStream(file);
        MultipartFile multipartFile = new MockMultipartFile(file.getName(), inputStream);
        BaseResult result = kbService.publishKnowledge(multipartFile, knowledgeBase);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

    private void updateFile() throws Exception {
        KnowledgeBaseRequest knowledgeBase = jsonToInParameter("parameters/knowledgeBase.json", "update", KnowledgeBaseRequest.class);
        File file = new File(KnowledgeConstant.DAT + File.separator + KnowledgeConstant.ASN_BUILTIN + KnowledgeConstant.MMDB);
        FileInputStream inputStream = new FileInputStream(file);
        MultipartFile multipartFile = new MockMultipartFile(file.getName(), inputStream);
        BaseResult result = kbService.updateKnowledge(multipartFile, knowledgeBase);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

    private void updateStatus() {
        KnowledgeBaseRequest knowledgeBase = jsonToInParameter("parameters/knowledgeBase.json", "update_status", KnowledgeBaseRequest.class);
        BaseResult result = kbService.updateStatus(knowledgeBase);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

    private void delete() {
        KnowledgeBaseRequest knowledgeBase = jsonToInParameter("parameters/knowledgeBase.json", "delete", KnowledgeBaseRequest.class);
        BaseResult result = kbService.deleteKnowledge(knowledgeBase);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }
}

package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.services.common.entity.KnowledgeBase;
import com.mesalab.services.common.entity.KnowledgeConstant;
import com.mesalab.services.service.KnowledgeBaseService;
import org.apache.http.entity.ContentType;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;

/**
 * @Author wxs
 * @Date 2022/10/9
 */
@EnableAutoConfiguration
public class KnowledgeBaseTest extends GalaxyQGWApplicationTests {
    @Autowired
    private KnowledgeBaseService knowledgeService;

    private String id;
    @Test
    public void query() {

        BaseResult result = knowledgeService.queryKnowledge();
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

    @Test(priority = 1)
    public void publish() throws Exception {
        KnowledgeBase knowledgeBase = jsonToInParameter("parameters/knowledgeBase.json", "publishTest", KnowledgeBase.class);
        File file = new File(KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_BUILT_IN_V4 + KnowledgeConstant.MMDB);
        FileInputStream inputStream = new FileInputStream(file);
        MultipartFile multipartFile = new MockMultipartFile(file.getName(), file.getName(),
                ContentType.APPLICATION_OCTET_STREAM.toString(), inputStream);
        BaseResult result = knowledgeService.publishKnowledge(multipartFile, knowledgeBase);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
        id = ((Map) result.getData()).get("id").toString();
    }
    @Test(priority = 2)
    public void update() throws Exception {
        File file = new File(KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_BUILT_IN_V4 + KnowledgeConstant.MMDB);
        FileInputStream inputStream = new FileInputStream(file);
        MultipartFile multipartFile = new MockMultipartFile(file.getName(), file.getName(),
                ContentType.APPLICATION_OCTET_STREAM.toString(), inputStream);
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(id);
        BaseResult result = knowledgeService.updateKnowledge(multipartFile, knowledgeBase);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

    @Test(priority = 3)
    public void toggle() throws Exception{
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(id);
        knowledgeBase.setIsValid(KnowledgeConstant.VALID);
        BaseResult result = knowledgeService.toggleKnowledge(knowledgeBase);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }

    @Test(priority = 4)
    public void delete() throws Exception {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(id);
        BaseResult result = knowledgeService.deleteKnowledge(id);
        Assert.assertTrue(result.getMessage(), result.getStatus() == 200);
    }
}

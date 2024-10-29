package com.mesalab.qgw.service;

import com.google.common.collect.Lists;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.job.DataDecryptionRequest;
import com.mesalab.qgw.model.job.EncryptionInfo;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * TODO
 *
 * @Classname UtilServiceTest
 * @Date 2024/6/20 09:24
 * @Author wWei
 */
@EnableAutoConfiguration
public class UtilServiceTest extends GalaxyQGWApplicationTests {
    private UtilService utilService;

    @Test
    public void testEncryptWithJasypt() {
        EncryptionInfo encryptionInfo = new EncryptionInfo();
        encryptionInfo.setPassword("123");
        encryptionInfo.setSalt("abcd");
        BaseResult baseResult = utilService.encryptWithJasypt(encryptionInfo);
        Assert.assertTrue("decrypt error" + baseResult.getMessage(), baseResult.isSuccess());

    }

    @Test
    public void testDecryptWithKMS() {
        DataDecryptionRequest dataDecryptionRequest = new DataDecryptionRequest();
        dataDecryptionRequest.setEncryptionAlgorithm("aes-256-gcm96");
        dataDecryptionRequest.setCiphertextList(Lists.newArrayList("vault:v9:iWp2dLcbIdrwI6Zu4H3ba5LPnhs9pqlOp3whtUpkBTpDkWOHig2q"));
        BaseResult baseResult = utilService.decryptWithKMS(dataDecryptionRequest);
        Assert.assertTrue("decrypt error" + baseResult.getMessage(), baseResult.isSuccess());

    }

    @Autowired
    private void setUtilService(UtilService utilService) {
        this.utilService = utilService;
    }
}

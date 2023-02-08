package com.mesalab.qgw.service;

import com.measlab.tsg.service.EntityService;
import com.measlab.tsg.service.RelationService;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.ComDSLObject;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * @Author wxs
 * @Date 2022/9/28
 */
@EnableAutoConfiguration
public class EntityTest extends GalaxyQGWApplicationTests {

    @Autowired
    private EntityService entityService;

    @Autowired
    private RelationService relationService;

    @Test
    public void activeClientIp() {

        ComDSLObject dslObject = jsonToInParameter("parameters/entityTest.json", "activeClientIp", ComDSLObject.class);
        BaseResult entityInfo = entityService.getEntityInfo("activeclientip", dslObject);
        Assert.assertTrue(entityInfo.getMessage(), entityInfo.getStatus() == 200);

    }

    @Test
    public void topServerIp() {

        ComDSLObject dslObject = jsonToInParameter("parameters/entityTest.json", "topServerIp", ComDSLObject.class);
        BaseResult entityInfo = entityService.getEntityInfo("topserverip", dslObject);
        Assert.assertTrue(entityInfo.getMessage(), entityInfo.getStatus() == 200);

    }

    @Test
    public void topSni() {

        ComDSLObject dslObject = jsonToInParameter("parameters/entityTest.json", "topSni", ComDSLObject.class);
        BaseResult entityInfo = entityService.getEntityInfo("topsni", dslObject);
        Assert.assertTrue(entityInfo.getMessage(), entityInfo.getStatus() == 200);

    }

    @Test
    public void subScriberidPool() {
        ComDSLObject dslObject = jsonToInParameter("parameters/entityTest.json", "subScriberidPool", ComDSLObject.class);
        BaseResult entityInfo = relationService.getRealRelation("subscriberidpool", dslObject);
        Assert.assertTrue(entityInfo.getMessage(), entityInfo.getStatus() == 200);
    }

    @Test
    public void gtpc() {
        ComDSLObject dslObject = jsonToInParameter("parameters/entityTest.json", "gtpc", ComDSLObject.class);
        BaseResult entityInfo = relationService.getRealRelation("gtpc", dslObject);
        Assert.assertTrue(entityInfo.getMessage(), entityInfo.getStatus() == 200);
    }
}

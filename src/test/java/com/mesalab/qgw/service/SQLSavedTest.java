package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.controller.QueryController;
import com.mesalab.qgw.model.basic.SqlQueryRequestParam;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.util.Map;

/**
 * @Author ww
 * @Date 2024/1/30
 */
@EnableAutoConfiguration
public class SQLSavedTest extends GalaxyQGWApplicationTests {

    @Autowired
    private QueryController queryController;
    @Autowired
    private QueryJobService queryJobService;

    @Test
    public void testWholeProcess() {
        SqlQueryRequestParam body = jsonToInParameter("parameters/sqlSavedTest.json", "default", SqlQueryRequestParam.class);

        BaseResult commitResult = queryController.createSQLQuery(body);
        Assert.assertTrue(commitResult.getMessage(), commitResult.getStatus() == 201);
        String id = ((Map<String, Object>) commitResult.getJob()).get("job_id").toString();

        BaseResult statusResult = queryJobService.getSavedQueryStatusById(id);
        Assert.assertTrue(statusResult.getMessage(), statusResult.getStatus() == 200);

        BaseResult deletedResult = queryController.deleteJobById(id, 1);
        Assert.assertTrue(deletedResult.getMessage(), deletedResult.getStatus() == 202);

    }
}
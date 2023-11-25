package com.mesalab.qgw.service;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.services.common.dsl.ComDSLObject;
import com.mesalab.services.service.UnstructuredService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

@EnableAutoConfiguration
public class UnstructuredTest extends GalaxyQGWApplicationTests {
    @Autowired
    UnstructuredService unstructuredService;

    @Test
    public void allFilePath(){
        ComDSLObject dslObject = jsonToInParameter("parameters/unstructuredTest.json", "all", ComDSLObject.class);
        BaseResult UnstructuredData = unstructuredService.getUnstructuredData("all", dslObject);
        Assert.assertTrue(UnstructuredData.getMessage(), UnstructuredData.getStatus() == 200);
    }

    @Test
    public void mailFilePath(){
        ComDSLObject dslObject = jsonToInParameter("parameters/unstructuredTest.json", "mail", ComDSLObject.class);
        BaseResult UnstructuredData = unstructuredService.getUnstructuredData("mail", dslObject);
        Assert.assertTrue(UnstructuredData.getMessage(), UnstructuredData.getStatus() == 200);
    }

    @Test
    public void pcapFilePath(){
        ComDSLObject dslObject = jsonToInParameter("parameters/unstructuredTest.json", "pcap", ComDSLObject.class);
        BaseResult UnstructuredData = unstructuredService.getUnstructuredData("pcap", dslObject);
        Assert.assertTrue(UnstructuredData.getMessage(), UnstructuredData.getStatus() == 200);
    }

    @Test
    public void httpFilePath(){
        ComDSLObject dslObject = jsonToInParameter("parameters/unstructuredTest.json", "http", ComDSLObject.class);
        BaseResult UnstructuredData = unstructuredService.getUnstructuredData("http", dslObject);
        Assert.assertTrue(UnstructuredData.getMessage(), UnstructuredData.getStatus() == 200);
    }
}

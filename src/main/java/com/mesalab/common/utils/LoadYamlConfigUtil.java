package com.mesalab.common.utils;

/**
 * @Date: 2021-04-29 11:16
 * @Author : liuyongqiang
 * @ClassName : LoadYamlConfigUtil
 * @Description : TODO
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.util.Properties;

public class LoadYamlConfigUtil {

    private static final Logger logger = LoggerFactory.getLogger(LoadYamlConfigUtil.class);

    public static Object getValue(String key) {
        String userDir = System.getProperty("user.dir");
        String configPath = userDir + File.separator + "config" + File.separator + "application.yml";
        Resource resource = new FileSystemResource(configPath);
        Properties properties;
        try {
            YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
            yamlFactory.setResources(resource);
            properties = yamlFactory.getObject();
        } catch (Exception e) {
            logger.error("load yml error:{}", e);
            return null;
        }
        return properties.get(key);
    }

}


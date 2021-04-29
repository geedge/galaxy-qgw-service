package com.mesalab.common.listener;

import com.mesalab.common.utils.LoadYamlConfigUtil;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;

import java.io.File;
import java.util.Objects;

/**
 * @Date: 2021-04-29 10:57
 * @Author : liuyongqiang
 * @ClassName : ApplicationStartingListener
 * @Description : ApplicationStartingListener
 */
public class ApplicationStartingListener implements ApplicationListener<ApplicationStartingEvent> {

    @Override
    public void onApplicationEvent(ApplicationStartingEvent applicationStartingEvent) {
        Object localModel = LoadYamlConfigUtil.getValue("nacos.config.local-model");
        if (Objects.isNull(localModel)) return;
        if (Boolean.valueOf(localModel.toString())) {
            String userDir = System.getProperty("user.dir");
            String configPath = userDir + File.separator + "config";
            System.setProperty("JM.SNAPSHOT.PATH", configPath);
        }
    }
}


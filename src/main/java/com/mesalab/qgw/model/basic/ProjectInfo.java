package com.mesalab.qgw.model.basic;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 项目配置
 *
 * @author dazzlzy
 * @date 2018/5/26
 */
@Data
@Component
@NacosConfigurationProperties(prefix = "project", dataId = "${nacos.config.data-id}", groupId = "${nacos.config.group}", type = ConfigType.YAML, autoRefreshed = true)
public class ProjectInfo {

    /**
     * 工程名
     */
    private String name;

    /**
     * 版本
     */
    private String version;

    /**
     * 工程描述
     */
    private String description;

    /**
     * 项目组织标识
     */
    private String groupId;

    /**
     * 项目标识
     */
    private String artifactId;

    /**
     * 项目根目录
     */
    private String basedir;

    /**
     * 核心项目包
     */
    private String corePackage;

    /**
     * 业务项目包
     */
    private String servicePackage;

    /**
     * 项目作者
     */
    private Map author;


}

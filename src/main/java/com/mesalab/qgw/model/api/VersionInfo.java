package com.mesalab.qgw.model.api;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import com.mesalab.common.nacos.NacosConst;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Date: 2021-03-12 11:27
 * @Author : liuyongqiang
 * @ClassName : VersionInfo
 * @Description : VersionInfo
 */
@Data
@Component
@NacosConfigurationProperties(dataId = "galaxy-qgw-version", groupId = NacosConst.GALAXY_GROUP, type = ConfigType.JSON, autoRefreshed = true)
public class VersionInfo {

    private String product;
    private String version;
    private String registered;
    private String updated;
    private ComponentsBean components;

    @Data
    public static class ComponentsBean {
        private List<OssBean> oss;
        private List<AppsBean> apps;
        private List<TasksBean> tasks;

        @Data
        public static class OssBean {
            private String name;
            private String version;
            private String licenseType;
            private String description;
        }

        @Data
        public static class AppsBean {

            private String name;
            private String version;
            private String description;
        }

        @Data
        public static class TasksBean {
            private String name;
            private String version;
            private String description;
        }
    }
}

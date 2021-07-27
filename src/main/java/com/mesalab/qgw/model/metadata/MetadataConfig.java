package com.mesalab.qgw.model.metadata;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties;
import com.mesalab.common.nacos.NacosConst;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TODO
 *
 * @Classname MetadataConfig
 * @Date 2021/7/26 10:25 下午
 * @Author wWei
 */
@Data
@Component
@NacosConfigurationProperties(dataId = NacosConst.META_DATA_ID, groupId = "${nacos.config.group}", type = ConfigType.JSON, autoRefreshed = true)
public class MetadataConfig {
    private List<MetadataBean> metadata;
}

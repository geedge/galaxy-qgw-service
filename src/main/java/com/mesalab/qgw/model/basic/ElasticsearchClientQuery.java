package com.mesalab.qgw.model.basic;

import cn.hutool.core.util.StrUtil;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * @author wangwei
 */
@Component
public class ElasticsearchClientQuery implements Serializable {

    @Autowired
    private ElasticsearchHttpSource elasticsearchHttpSource;

    @Bean(name = "restClient")
    public RestClient restClient() {
        String[] split = StrUtil.split(elasticsearchHttpSource.getUrl(), ",");
        HttpHost[] httpHost = new HttpHost[split.length];
        for (int i = 0; i < split.length; i++) {
            String[] item = StrUtil.split(split[i], ":");
            httpHost[i] = new HttpHost(item[0], Integer.parseInt(item[1]), "http");
        }
        return RestClient.builder(httpHost).setRequestConfigCallback(requestConfigBuilder -> {
            requestConfigBuilder.setSocketTimeout(Integer.parseInt(elasticsearchHttpSource.getSocketTimeOut()));
            return requestConfigBuilder;
        }).build();
    }
}

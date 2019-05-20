package com.mesalab.qgw.service.impl;

import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("httpClientManagerFactory")
public class HttpClientManagerFactory implements FactoryBean<CloseableHttpClient>, InitializingBean, DisposableBean  {

    private CloseableHttpClient client;

    @Autowired
    private ConnectionKeepAliveStrategy connectionKeepAliveStrategy;

    @Autowired
    private HttpRequestRetryHandler httpRequestRetryHandler;

    @Autowired
    private PoolingHttpClientConnectionManager poolHttpcConnManager;

    @Autowired
    private RequestConfig config;


    @Override
    public void destroy() throws Exception {
        if(null != this.client){
            this.client.close();
        }
    }

    @Override
    public CloseableHttpClient getObject() throws Exception {

        return this.client;
    }

    @Override
    public Class<?> getObjectType() {
        return (this.client == null ? CloseableHttpClient.class : this.client.getClass());
    }

    // 构建的实例为单例
    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.client = HttpClients.custom().setConnectionManager(poolHttpcConnManager)
                .setRetryHandler(httpRequestRetryHandler)
                .setKeepAliveStrategy(connectionKeepAliveStrategy)
                .setDefaultRequestConfig(config)
                .build();
    }

}



package com.mesalab.common.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

import java.nio.charset.StandardCharsets;

/**
 * TODO
 *
 * @Classname MultipartResolverConfiguration
 * @Date 2022/11/4 2:38 PM
 * @Author wWei
 */
@Configuration
public class MultipartResolverConfiguration {

    @Value("${spring.servlet.multipart.max-request-size}")
    private Integer maxSize;

    @Bean("multipartResolver")
    public MultipartResolver multipartResolver() {
        CommonsMultipartResolver resolver = new CommonsMultipartResolver();
        resolver.setDefaultEncoding(StandardCharsets.UTF_8.name());
        resolver.setResolveLazily(true);
        resolver.setMaxUploadSize(maxSize);
        return resolver;
    }
}

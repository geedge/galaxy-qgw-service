package com.mesalab.common.configuration;

import com.hazelcast.config.Config;
import com.hazelcast.config.YamlConfigBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

/**
 * TODO
 *
 * @Classname HazelcastConfig
 * @Date 2023/12/9 10:01
 * @Author wWei
 */
@Configuration
public class HazelcastConfiguration {
    @Bean
    public Config hazelcastConfig() throws IOException, URISyntaxException {
        Resource resource = new FileSystemResource(new File("").getCanonicalPath() + File.separator + "config" + File.separator + "hazelcast.yaml");
        if (!resource.exists()) {
            resource = new ClassPathResource("hazelcast.yaml");
        }
        InputStream inputStream = resource.getInputStream();
        YamlConfigBuilder configBuilder = new YamlConfigBuilder(inputStream);
        return configBuilder.build();
    }
}

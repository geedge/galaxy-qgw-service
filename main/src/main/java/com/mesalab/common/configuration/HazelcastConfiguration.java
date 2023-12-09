package com.mesalab.common.configuration;

import com.hazelcast.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
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
        Config config = new Config();
        config.setConfigurationFile(new ClassPathResource("hazelcast.yaml").getFile());
        return config;
    }

}

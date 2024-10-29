package com.mesalab.common.configuration;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.Data;
import org.springframework.context.annotation.Configuration;


/**
 * @author wangwei
 */

@Data
@Configuration
public class VaultConfiguration {
    @NacosValue("${vault.url}")
    private String url;

    @NacosValue("${vault.username}")
    private String username;

    @NacosValue("${vault.password}")
    private String password;

    @NacosValue("${vault.keyPath}")
    private String keyPath;

    @NacosValue("${vault.ssl.skipVerification}")
    private boolean sslSkipVerification;

    @NacosValue("${vault.ssl.privateKeyPath}")
    private String sslPrivateKeyPath;

    @NacosValue("${vault.ssl.certificatePath}")
    private String sslCertificatePath;

    @NacosValue("${vault.ssl.caCertificatePath}")
    private String sslCaCertificatePath;
}

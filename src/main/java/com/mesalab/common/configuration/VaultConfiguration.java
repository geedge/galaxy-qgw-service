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
    @NacosValue(value = "${vault.url}", autoRefreshed = true)
    private String url;

    @NacosValue(value = "${vault.username}", autoRefreshed = true)
    private String username;

    @NacosValue(value = "${vault.password}", autoRefreshed = true)
    private String password;

    @NacosValue(value = "${vault.keyPath}", autoRefreshed = true)
    private String keyPath;

    @NacosValue(value = "${vault.identifier}", autoRefreshed = true)
    private String identifier;

    @NacosValue(value = "${vault.ssl.skipVerification}", autoRefreshed = true)
    private boolean sslSkipVerification;

    @NacosValue(value = "${vault.ssl.privateKeyPath}", autoRefreshed = true)
    private String sslPrivateKeyPath;

    @NacosValue(value = "${vault.ssl.certificatePath}", autoRefreshed = true)
    private String sslCertificatePath;

    @NacosValue(value = "${vault.ssl.caCertificatePath}", autoRefreshed = true)
    private String sslCaCertificatePath;
}

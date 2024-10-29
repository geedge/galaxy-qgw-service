package com.mesalab.qgw.service.impl;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.configuration.VaultConfiguration;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.utils.sqlparser.SQLSyntaxParserUtil;
import com.mesalab.qgw.model.job.DataDecryptionRequest;
import com.mesalab.qgw.model.job.EncryptionInfo;
import com.mesalab.qgw.service.UtilService;
import io.github.jopenlibs.vault.SslConfig;
import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.VaultConfig;
import io.github.jopenlibs.vault.VaultException;
import io.github.jopenlibs.vault.response.AuthResponse;
import io.github.jopenlibs.vault.response.LogicalResponse;
import org.jasypt.util.text.BasicTextEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

/**
 * @author wangwei
 */
@Service(value = "utilService")
public class UtilServiceImpl implements UtilService {
    private static final Log log = LogFactory.get();

    private VaultConfiguration vaultConfiguration;

    @Override
    public BaseResult encryptWithJasypt(EncryptionInfo param) {
        BaseResult baseResult;
        BasicTextEncryptor textEncryptor = new BasicTextEncryptor();
        textEncryptor.setPassword(param.getSalt());
        String encrypt = textEncryptor.encrypt(param.getPassword());
        Map<String, Object> dataMap = Maps.newHashMap();
        dataMap.put("password", param.getPassword());
        dataMap.put("encrypted_password", encrypt);
        baseResult = BaseResultGenerator.success("ok", dataMap);
        return baseResult;
    }

    @Override
    public BaseResult decryptWithKMS(DataDecryptionRequest param) {
        Map<String, List<Object>> result = Maps.newHashMap();
        List<Object> plaintextList = Lists.newArrayList();
        List<String> ciphertextList = param.getCiphertextList();
        String encryptionAlgorithm = param.getEncryptionAlgorithm();
        try {
            SslConfig sslConfig = new SslConfig().verify(!vaultConfiguration.isSslSkipVerification()).build();
            if (!vaultConfiguration.isSslSkipVerification()) {
                sslConfig.pemFile(new File(vaultConfiguration.getSslCaCertificatePath()))
                        .clientPemFile(new File(vaultConfiguration.getSslCertificatePath()))
                        .clientKeyPemFile(new File(vaultConfiguration.getSslPrivateKeyPath()))
                        .build();
            }
            VaultConfig config =
                    new VaultConfig()
                            .address(vaultConfiguration.getUrl())
                            .engineVersion(1)
                            .sslConfig(sslConfig)
                            .build();
            AuthResponse authResponse = Vault.create(config).auth().loginByUserPass(vaultConfiguration.getUsername(), vaultConfiguration.getPassword());
            config.token(authResponse.getAuthClientToken());
            Vault vault = Vault.create(config);
            for (String item : ciphertextList) {
                Map<String, Object> secrets = Maps.newHashMap();
                secrets.put("ciphertext", item);
                LogicalResponse decryptResponse = vault.logical().write(vaultConfiguration.getKeyPath() + "/decrypt/" + encryptionAlgorithm, secrets);
                if (decryptResponse.getRestResponse().getStatus() == HttpStatusCodeEnum.SUCCESS.getCode()) {
                    byte[] decryptData = Base64.getDecoder().decode(decryptResponse.getDataObject().get("plaintext").asString());
                    plaintextList.add(decryptData);
                } else {
                    log.error("Decrypt {} error. code: {} body: {}", item, decryptResponse.getRestResponse().getStatus(), new String(decryptResponse.getRestResponse().getBody()));
                    throw new RuntimeException("Decrypt " + item + " error: " + new String(decryptResponse.getRestResponse().getBody()));
                }
            }
        } catch (VaultException | RuntimeException e) {
            log.error("Decrypt error. message: {}", e.getMessage());
            throw new RuntimeException("Decrypt error. message: " + e.getMessage());
        }
        result.put("plaintext_list", plaintextList);
        return BaseResultGenerator.success("ok", result);
    }

    @Override
    public BaseResult getSQLSyntaxTree(String sql) {
        List list = SQLSyntaxParserUtil.syntaxParse(sql);
        return BaseResultGenerator.success(list);
    }

    @Autowired
    public void setVaultConfiguration(VaultConfiguration vaultConfiguration) {
        this.vaultConfiguration = vaultConfiguration;
    }
}

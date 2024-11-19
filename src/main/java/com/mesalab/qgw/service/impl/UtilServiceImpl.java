package com.mesalab.qgw.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
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
import io.github.jopenlibs.vault.json.Json;
import io.github.jopenlibs.vault.json.JsonArray;
import io.github.jopenlibs.vault.json.JsonObject;
import io.github.jopenlibs.vault.json.JsonValue;
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
                sslConfig.pemFile(new File(vaultConfiguration.getSslCaCertificatePath())).clientPemFile(new File(vaultConfiguration.getSslCertificatePath())).clientKeyPemFile(new File(vaultConfiguration.getSslPrivateKeyPath())).build();
            }
            VaultConfig config = new VaultConfig().address(vaultConfiguration.getUrl()).engineVersion(1).sslConfig(sslConfig).build();
            AuthResponse authResponse = Vault.create(config).auth().loginByUserPass(vaultConfiguration.getUsername(), vaultConfiguration.getPassword());
            config.token(authResponse.getAuthClientToken());
            Vault vault = Vault.create(config);
            JSONArray jsonArray = new JSONArray();
            ciphertextList.forEach(x -> jsonArray.add(new HashMap<>() {{
                put("ciphertext", x);
            }}));
            JsonValue jsonValue = Json.parse(String.valueOf(jsonArray));
            Map<String, Object> secrets = new HashMap<>();
            secrets.put("batch_input", jsonValue);
            LogicalResponse decryptResponse = vault.logical().write(vaultConfiguration.getKeyPath() + "/decrypt/" + encryptionAlgorithm, secrets);
            if (decryptResponse.getRestResponse().getStatus() == HttpStatusCodeEnum.SUCCESS.getCode()
                    // TODO 处理 vault 批量解密时，部分解析失败 status = 400
                    || decryptResponse.getRestResponse().getStatus() == HttpStatusCodeEnum.BAD_REQUEST.getCode()) {
                JsonArray batchResults = decryptResponse.getDataObject().get("batch_results").asArray();
                for (JsonValue batchResult : batchResults) {
                    JsonObject asObject = batchResult.asObject();
                    String plaintext = asObject.get("plaintext").asString();

                    //如果部分数据解密失败，对应的plaintext为空，可从error字段中获取错误信息
                    if (StrUtil.isEmpty(plaintext)) {
                        plaintextList.add(null);
                        log.error("Decrypt data error! error: " + asObject.get("error").asString());
                        continue;
                    }
                    byte[] decode = Base64.getDecoder().decode(plaintext);
                    plaintextList.add(decode);
                }
            } else {
                log.error("Decrypt {} error. code: {} body: {}", jsonValue, decryptResponse.getRestResponse().getStatus(), new String(decryptResponse.getRestResponse().getBody()));
                throw new RuntimeException("Decrypt " + jsonValue + " error: " + new String(decryptResponse.getRestResponse().getBody()));
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

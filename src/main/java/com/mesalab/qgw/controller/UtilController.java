package com.mesalab.qgw.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.geedgenetworks.utils.StringUtil;
import com.mesalab.common.configuration.VaultConfiguration;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.enums.EncryptionAlgorithm;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.model.basic.AuditLog;
import com.mesalab.qgw.model.basic.EngineConfigSource;
import com.mesalab.qgw.model.job.DataDecryptionRequest;
import com.mesalab.qgw.model.job.EncryptionInfo;
import com.mesalab.qgw.service.UtilService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/v1/util")
public class UtilController {

    private static final Log log = LogFactory.get();
    private UtilService utilService;
    private VaultConfiguration vaultConfiguration;
    private EngineConfigSource engineConfigSource;

    @GetMapping(value = "/sql-parser", consumes = "application/x-www-form-urlencoded")
    @AuditLog("UtilController.getSQLSyntaxTree")
    public BaseResult getSQLSyntaxTree(String sql) {
        return utilService.getSQLSyntaxTree(sql);
    }

    @PostMapping(value = "/password-generator", consumes = "application/json")
    @AuditLog("UtilController.encryptWithJasypt")
    public BaseResult encryptWithJasypt(@RequestBody EncryptionInfo param) {
        log.info("Encrypt with jasypt, param is : {}", param);
        if (StringUtil.isEmpty(param.getPassword()) || StringUtil.isEmpty(param.getSalt())) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.PASSWORD_OR_SALT_CANNOT_BE_EMPTY));
        }
        return utilService.encryptWithJasypt(param);
    }

    @PostMapping(value = "/kms/data-decryption", consumes = "application/json")
    @AuditLog("UtilController.decryptWithKMS")
    public BaseResult decryptWithKMS(@RequestBody DataDecryptionRequest param) {
        log.debug("Decrypt with KMS, param is : {}", param);
        if (StringUtil.isEmpty(param.getCiphertextList()) || param.getCiphertextList().size() > engineConfigSource.getDefaultResultNum()) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), "This ciphertext_list cannot be empty and must contain at most " + engineConfigSource.getDefaultResultNum() + " items."));
        }
        if (StringUtil.isEmpty(param.getEncryptionAlgorithm())) {
            param.setEncryptionAlgorithm(vaultConfiguration.getIdentifier());
        }
        if (!EncryptionAlgorithm.isValid(param.getEncryptionAlgorithm())) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), "The encryption_algorithm default is aes-128-gcm96, Supported Include aes-128-gcm, aes-128-gcm96, aes-256-gcm96, sm4-gcm96."));
        }
        return utilService.decryptWithKMS(param);
    }

    @Autowired
    public void setVaultConfiguration(VaultConfiguration vaultConfiguration) {
        this.vaultConfiguration = vaultConfiguration;
    }

    @Autowired
    public void setUtilService(UtilService utilService) {
        this.utilService = utilService;
    }

    @Autowired
    public void setEngineConfigSource(EngineConfigSource engineConfigSource) {
        this.engineConfigSource = engineConfigSource;
    }

}

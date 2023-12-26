package com.mesalab.qgw.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.geedgenetworks.utils.StringUtil;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.model.job.EncryptionInfo;
import com.mesalab.qgw.service.UtilService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/v1/util")
public class UtilController {

    private static final Log log = LogFactory.get();
    @Autowired
    private UtilService utilService;

    @GetMapping(value = "/sql_parser", consumes = "application/x-www-form-urlencoded")
    public BaseResult getSQLSyntaxTree(String sql){
        return utilService.getSQLSyntaxTree(sql);
    }

    @PostMapping(value = "/password_generator")
    public BaseResult getCiphertext(EncryptionInfo param) {
        log.info("Plaintext encrypted,The plaintext argument is : {}", param);
        if (StringUtil.isEmpty(param.getPassword()) || StringUtil.isEmpty(param.getSalt())) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.PASSWORD_OR_SALT_CANNOT_BE_EMPTY));
        }
        return utilService.getCiphertext(param);
    }
}

package com.mesalab.qgw.service.impl;

import com.google.common.collect.Lists;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.utils.sqlparser.SQLSyntaxParserUtil;
import com.mesalab.qgw.model.job.EncryptionInfo;
import com.mesalab.qgw.service.UtilService;
import org.jasypt.util.text.BasicTextEncryptor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service(value = "utilService")
public class UtilServiceImp implements UtilService {

    @Override
    public BaseResult getCiphertext(EncryptionInfo param) {
        BaseResult baseResult;
        BasicTextEncryptor textEncryptor = new BasicTextEncryptor();
        textEncryptor.setPassword(param.getSalt());
        String encrypt = textEncryptor.encrypt(param.getPassword());
        Map dataMap = new HashMap();
        dataMap.put("password", param.getPassword());
        dataMap.put("encrypted_password", encrypt);
        baseResult = BaseResultGenerator.success("ok", Lists.newArrayList(dataMap));
        return baseResult;
    }
    @Override
    public BaseResult getSQLSyntaxTree(String sql) {
        List list = SQLSyntaxParserUtil.syntaxParse(sql);
        return BaseResultGenerator.success(list);
    }
}

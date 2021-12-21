package com.mesalab.qgw.model.job;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EncryptionInfo {
    /**
     * 必填，明文密码
     */
    private String password;
    /**
     * 加密盐，配置文件中需与当前指定一致
     */
    private String salt;
}

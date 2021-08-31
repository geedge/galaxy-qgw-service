package com.mesalab.common.enums;

import lombok.Getter;

/**
 * TODO
 *
 * @Classname DiagnosisOptionEnum
 * @Date 2021/8/31 11:44 上午
 * @Author wWei
 */
@Getter
public enum DiagnosisOptionEnum {

    /**
     * 验证SQL测试集，对数据源进行功能诊断
     */
    VALIDATION("validation"),
    /**
     * 执行SQL测试集，对数据源进行POC测试
     */
    EXECUTION("execution");

    private String value;

    DiagnosisOptionEnum(String value) {
        this.value = value;
    }

}

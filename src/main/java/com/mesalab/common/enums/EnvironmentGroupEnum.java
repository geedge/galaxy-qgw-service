package com.mesalab.common.enums;

import lombok.Getter;

/**
 * 运行环境组枚举
 *
 * @author dazzlzy
 * @date 2018/5/26
 */
@Getter
public enum EnvironmentGroupEnum {

    /**
     * RUNTIME运行环境组：
     * 1. DEV(开发环境)
     * 2. PROD(生产环境)
     */
    RUNTIME(new Environment[]{Environment.DEV, Environment.PROD}),;

    /**
     * 运行环境
     */
    private Environment[] environments;

    EnvironmentGroupEnum(Environment[] environments) {
        this.environments = environments;
    }

    /**
     * 是否是runtime运行环境组
     *
     * @param s 环境名
     * @return boolean
     */
    public static boolean isRuntime(String s) {
        Environment[] environmentEnums = RUNTIME.getEnvironments();
        for (Environment environmentEnum : environmentEnums) {
            if (environmentEnum.getName().equals(s)) {
                return true;
            }
        }
        return false;
    }
}

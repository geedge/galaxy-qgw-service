package com.mesalab.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Date: 2020-07-20 18:45
 * @Author : liuyongqiang
 * @ClassName : EngineTypeEnum
 * @Description : 查询引擎映射枚举类
 */
@Getter
@AllArgsConstructor
public enum EngineTypeEnum {

    //AnalysisEngine-AnangoDB 分析、学习引擎
    //CaculationEngine-Druid  保存、计算引擎
    //BusinessEngine-ClickHouse 业务数据引擎

    ANALYSIS_ENGINE("AnalysisEngine", "AnangoDB"),
    CACULATION_ENGINE("CaculationEngine", "Druid"),
    BUSINESS_ENGINE("BusinessEngine", "ClickHouse");

    private String engine;//引擎名称
    private String dbtype;//数据库类型

    public static EngineTypeEnum getByEngine(String engine) {
        for (EngineTypeEnum constants : values()) {
            if (constants.engine.equalsIgnoreCase(engine)) {
                return constants;
            }
        }
        return null;
    }

}

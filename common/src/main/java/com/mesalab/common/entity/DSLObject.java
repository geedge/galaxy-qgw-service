package com.mesalab.common.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Date: 2020-11-13 09:48
 * @Author : wWei
 * @ClassName : DSLObject
 * @Description : 标准服务接口模型
 */

@Data
public class DSLObject implements Serializable {
    private String clientId;
    private Query query;

    @Data
    public static class Query {
        private String dataEngine;
        private String queryType;
        private String dataSource;
        private Parameters parameters;

        @Data
        public static class Parameters {
            private List<String> intervals;
            private Integer limit;
            private String granularity;
            private Match match;
            private Range range;
            private Distinct distinct;
            private List<Sort> sort;

            @Data
            public static class Match {
                private String logicType;
                private List<Condition> condition;
            }
            @Data
            public static class Distinct {
                private String logicType;
                private List<Condition> condition;
            }
            @Data
            public static class Range {
                private String logicType;
                private List<Condition> condition;
            }
            @Data
            public static class Sort {
                private String type;
                private String fieldKey;
            }
        }
    }
}


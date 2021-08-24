package com.mesalab.entity.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
/**
 * @Date: 2021-08-24 15:42
 * @Author : tiangaochao
 * @ClassName : DSLObject
 * @Description :
 */
@Data
public class DSLObject implements Serializable {
    private String clientId;
    private Query query;

    @Data
    public static class Query {
        private String dataEngine;
        private String dataSource;
        private String limit;
        private QueryBean parameters;

        @Data
        public static class QueryBean {
            private String granularity;
            private List<MatchBean> match;
            private List<String> intervals;
            private List<RangeBean> range;
            private List<SortBean> sort;
        }

        @Data
        public static class MatchBean {
            private String type;
            private String fieldKey;
            private List<String> fieldValues;
        }

        @Data
        public static class SortBean {
            private String type;
            private String fieldKey;
        }

        @Data
        public static class RangeBean {
            private String type;
            private String fieldKey;
            private List<String> fieldValues;
        }
    }
}

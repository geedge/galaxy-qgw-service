package com.mesalab.tsg.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Date: 2020-09-18 09:48
 * @Author : wangwei
 * @ClassName : DSLObject
 * @Description :
 */
@Data
public class NetWorkDSLObject implements Serializable {

    private String clientId;
    private QueryBean query;

    @Data
    public static class QueryBean {
        private String queryType;
        private String dataSource;
        private Integer limit;
        private Integer offset;
        private Parameters parameters;

        @Data
        public static class Parameters {
            private String granularity;
            private List<FilterBean> match;
            private List<FilterBean> range;
            private List<String> intervals;

            @Data
            public static class FilterBean {
                private String type;
                private String fieldKey;
                private List<String> fieldValues;
            }
        }
    }
}

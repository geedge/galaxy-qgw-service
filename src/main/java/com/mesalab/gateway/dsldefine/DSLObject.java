package com.mesalab.gateway.dsldefine;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Date: 2020-07-20 09:48
 * @Author : liuyongqiang
 * @ClassName : DSLObject
 * @Description : 标准服务接口模型
 */
@Data
public class DSLObject implements Serializable {

    private static final long serialVersionUID = 4956257906217545258L;

    private String limit;//数据条数
    private String dataEngine;//数据引擎
    private String dataSource;//数据来源
    private QueryBean query;//查询结构

    @Data
    public static class QueryBean {
        private List<MatchBean> match;//匹配查询
        private List<RangeBean> range;//区间查询
        private List<IntervalsBean> intervals;//时间范围
        private List<SortBean> sort;//排序方式

        @Data
        public static class MatchBean {
            private String type;
            private String fieldKey;
            private List<String> fieldValues;
        }

        @Data
        public static class RangeBean {
            private String type;
            private String fieldKey;
            private List<String> fieldValues;
        }

        @Data
        public static class IntervalsBean {
            private String type;
            private String fieldKey;
            private List<String> fieldValues;
        }

        @Data
        public static class SortBean {
            private String type;
            private String fieldKey;
        }
    }
}

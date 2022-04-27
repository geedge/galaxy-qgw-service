package com.mesalab.qgw.model.api;

import com.zdjizhi.utils.StringUtil;
import lombok.*;
import java.io.Serializable;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiParam implements Serializable {

    private String query;
    private String option;
    private Boolean sampled;
    private String format;
    private String schema;
    private String tableName;
    private String dbType;// 数据库类型
    private String queryId;// SQL 语句的唯一标识
    private String dialectDBType; //方言数据库类型，用于SQL格式解析校验
    private String reportId;//离线统计报告唯一ID
    private String resultId;//离线统计报告结果ID

    private String sql;//当前执行的SQL

    private SQLQuerySource engineQuerySource; //网关引擎处理SQL对象

    private SQLQuerySource dbQuerySource; //下沉数据源引擎的SQL对象


    public String getResultId() {

        return StringUtil.isNotBlank(reportId) ? reportId : resultId;
    }

    public void setResultId(String resultId) {
        this.resultId = resultId;
    }

    public void setIsSampled(Boolean isSampled) {
        this.sampled = isSampled;
    }

    public  boolean queryParamIsNull() {
        return StringUtil.isBlank(query);
    }
}

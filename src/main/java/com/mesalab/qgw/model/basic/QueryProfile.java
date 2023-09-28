package com.mesalab.qgw.model.basic;

import com.geedgenetworks.utils.StringUtil;
import com.mesalab.common.enums.DBTypeEnum;
import lombok.*;
import java.io.Serializable;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryProfile implements Serializable {

    private String query;
    private String option;
    private Boolean sampled;
    private String format;
    private String queryId;
    private String resultId;
    @Deprecated
    private String reportId;
    /**
     * @see DBTypeEnum
     */
    private String dbType;
    /**
     * 方言数据库类型，用于SQL格式解析校验
     */
    private String dialectDBType;
    private SQLQuerySource engineQuerySource;
    private SQLQuerySource dbQuerySource;

    public String getResultId() {
        return StringUtil.isNotBlank(reportId) ? reportId : resultId;
    }

    public boolean queryParamIsNull() {
        return StringUtil.isBlank(query);
    }
}

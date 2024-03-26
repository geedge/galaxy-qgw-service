package com.mesalab.qgw.model.basic;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.EnumMultiset;
import com.mesalab.common.enums.ExecutionMode;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.enums.OutputMode;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import lombok.Data;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Common request parameters for all query types, including: SQL and DSL.
 * @Date 2023/12/6 15:30
 * @Author wWei
 */
@Data
public class CommonRequestParam implements Serializable {
    @JsonProperty("output_mode")
    private OutputMode outputMode = OutputMode.JSON;
    @JsonProperty("execution_mode")
    private ExecutionMode executionMode = ExecutionMode.NORMAL;
    @JsonProperty("is_dry_run")
    private boolean isDryRun = false;
    @JsonProperty("is_saved_query")
    private boolean isSavedQuery = false;
    @JsonProperty("timeout")
    private Integer timeout;

    public void setOutputMode(String outputMode) {
        OutputMode[] values = OutputMode.values();
        for (OutputMode value : values) {
            if(value.getValue().equalsIgnoreCase(outputMode)){
                outputMode = value.name();
                break;
            }
        }
        this.outputMode = OutputMode.valueOf(outputMode.toUpperCase());
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = ExecutionMode.valueOf(executionMode.toUpperCase());
    }

    public void setIsDryRun(Integer isDryRun) {
        this.isDryRun = isDryRun != 0;
    }

    public void setIsSavedQuery(Integer isSavedQuery) {
        this.isSavedQuery = isSavedQuery != 0;
    }
}
package com.mesalab.qgw.model.basic;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesalab.common.enums.ExecMode;
import com.mesalab.common.enums.OutputMode;
import lombok.Data;

import java.io.Serializable;

/**
 * Common request parameters for all query types, including: SQL and DSL.
 * @Date 2023/12/6 15:30
 * @Author wWei
 */
@Data
public class CommonRequestParam implements Serializable {
    @JsonProperty("output_mode")
    private OutputMode outputMode = OutputMode.JSON;
    @JsonProperty("exec_mode")
    private ExecMode execMode = ExecMode.NORMAL;
    @JsonProperty("is_dry_run")
    private boolean isDryRun = false;
    @JsonProperty("is_saved_query")
    private boolean isSavedQuery = false;
    @JsonProperty("timeout")
    private Integer timeout;

    public void setOutputMode(String outputMode) {
        this.outputMode = OutputMode.valueOf(outputMode.toUpperCase());
    }

    public void setExecMode(String execMode) {
        this.execMode = ExecMode.valueOf(execMode.toUpperCase());
    }

    public void setIsDryRun(Integer isDryRun) {
        this.isDryRun = isDryRun != 0;
    }

    public void setIsSavedQuery(Integer isSavedQuery) {
        this.isSavedQuery = isSavedQuery != 0;
    }
}
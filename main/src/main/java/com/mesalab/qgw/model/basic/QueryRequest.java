package com.mesalab.qgw.model.basic;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mesalab.common.enums.ExecModeEnum;
import com.mesalab.common.enums.OutputModeEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * TODO
 *
 * @Classname QueryRequest
 * @Date 2023/12/6 15:30
 * @Author wWei
 */
@Data
public class QueryRequest implements Serializable {
    @JsonProperty("output_mode")
    private OutputModeEnum outputMode = OutputModeEnum.JSON;
    @JsonProperty("exec_mode")
    private ExecModeEnum execMode = ExecModeEnum.NORMAL;
    @JsonProperty("is_dry_run")
    private boolean isDryRun = false;
    @JsonProperty("is_saved_query")
    private boolean isSavedQuery = false;
    @JsonProperty("timeout")
    private Integer timeout;

    public void setOutputMode(String outputMode) {
        this.outputMode = OutputModeEnum.valueOf(outputMode.toUpperCase());
    }

    public void setExecMode(String execMode) {
        this.execMode = ExecModeEnum.valueOf(execMode.toUpperCase());
    }

    public void setIsDryRun(Integer isDryRun) {
        this.isDryRun = isDryRun != 0;
    }

    public void setIsSavedQuery(Integer isSavedQuery) {
        this.isSavedQuery = isSavedQuery != 0;
    }
}
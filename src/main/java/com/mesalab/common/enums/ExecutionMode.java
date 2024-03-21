package com.mesalab.common.enums;

/**
 * Create Query Job Execution Mode
 *
 * @Classname ExecutionMode
 * @Date 2023/12/6 17:06
 * @Author wWei
 */
public enum ExecutionMode {
    /**
     * Desc:  run an asynchronous query
     */
    NORMAL("normal"),
    /**
     * Desc: return the Job ID when the job is complete (sync query)
     */
    BLOCKING("blocking"),
    /**
     * Desc: returns results in the same call(sync query). Does not return the Job ID
     */
    ONESHOT("oneshot");
    private final String value;

    ExecutionMode(String value) {
        this.value = value.toLowerCase();
    }

    public String getValue() {
        return value;
    }
}

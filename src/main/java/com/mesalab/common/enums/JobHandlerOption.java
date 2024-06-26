package com.mesalab.common.enums;

import lombok.Getter;


@Getter
public enum JobHandlerOption {

    DELETE_TRAFFIC_DATA_JOB_HANDLER("deleteTrafficDataJobHandler"),
    DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER("deleteReportAndMetricsDataJobHandler"),
    DELETE_FILES_JOB_HANDLER("deleteFilesJobHandler"),
    DELETE_ALL_TRAFFIC_DATA_JOB_HANDLER("deleteAllTrafficDataJobHandler"),
    DELETE_ALL_REPORT_AND_METRICS_DATA_JOB_HANDLER("deleteAllReportAndMetricsDataJobHandler"),
    DELETE_ALL_FILES_JOB_HANDLER("deleteAllFilesJobHandler"),
    DELETE_OLD_DATA_JOB_HANDLER("deleteOldDataJobHandler"),
    ;
    private String value;

    JobHandlerOption(String value) {
        this.value = value;
    }
}

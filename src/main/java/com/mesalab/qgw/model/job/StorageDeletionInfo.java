package com.mesalab.qgw.model.job;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StorageDeletionInfo {

    private String logType;
    private Integer maxDays;
    private Integer defaultMaxDays;

}

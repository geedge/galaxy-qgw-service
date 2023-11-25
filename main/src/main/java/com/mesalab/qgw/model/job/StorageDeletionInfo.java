package com.mesalab.qgw.model.job;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StorageDeletionInfo {

    private String type;
    @JsonProperty("max_days")
    private Integer maxDays;
    @JsonProperty("default_max_days")
    private Integer defaultMaxDays;

}

package com.mesalab.qgw.model.api.udf;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PartitionKeyRange {
    private String leftOfRange;
    private String rightOfRange;
    private String equal;
}

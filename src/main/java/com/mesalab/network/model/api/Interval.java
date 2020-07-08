package com.mesalab.network.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author gouge
 * @version 1.0
 * @date 2020/7/7 5:30 下午
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Interval {
    private String startTime;
    private String endTime;
}

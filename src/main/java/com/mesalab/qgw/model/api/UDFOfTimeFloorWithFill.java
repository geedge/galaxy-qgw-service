package com.mesalab.qgw.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UDFOfTimeFloorWithFill {

    private String name;
    private List parameterList;
    private String start;
    private String end;

}

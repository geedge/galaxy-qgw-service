package com.mesalab.tsg.entity;

import lombok.Data;

import java.util.List;

@Data
public class Parameters {

    private List<Match> match;

    private List<Range> range;

    private List<String> intervals;

    private List<Sort> sort;

    private String limit;



}

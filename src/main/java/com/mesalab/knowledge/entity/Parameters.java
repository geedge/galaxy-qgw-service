package com.mesalab.knowledge.entity;

import lombok.Data;

import java.util.List;

@Data
public class Parameters {

    private List<Match> matches;

    private List<Range> ranges;

    private List<String> intervals;

    private List<Sort> sorts;

    private String limit;



}

package com.mesalab.knowledge.entity;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
public class Parameters {

    private List<Match> match;

    private List<Range> range;

    private List<String> intervals;

    private List<Sort> sort;

    private String limit;


    public List<String> toIntervals() {
        List<String> intervals = Lists.newArrayList();
        return intervals!=null&&intervals.size()>0 ? Splitter.on("/").splitToList(intervals.get(0)) : null;
    }

}

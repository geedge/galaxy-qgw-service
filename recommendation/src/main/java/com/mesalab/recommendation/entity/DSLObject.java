package com.mesalab.recommendation.entity;


import lombok.Data;

import java.io.Serializable;


@Data
public class DSLObject implements Serializable {

    private static final long serialVersionUID = 4506414702067122671L;
    private Integer clientId;

    private DSLQuery query;

    @Data
    public static class DSLQuery {

        private String queryType;
        private String dataSource;
        private Parameters parameters;

    }
}


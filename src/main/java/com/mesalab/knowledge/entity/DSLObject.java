package com.mesalab.knowledge.entity;


import lombok.Data;


@Data
public class DSLObject {

    private Integer clientId;

    private DSLQuery query;

    @Data
    public static class DSLQuery {

        private String queryType;
        private String dataSource;
        private Parameters parameters;

    }
}


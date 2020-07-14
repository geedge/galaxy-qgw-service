package com.mesalab.knowledge.entity;


import lombok.Data;

@Data
public class DSLObject {

    private Integer clientId;

    private DSLQuery query;

    @Data
    public static class DSLQuery {

        public String queryType;
        public String datasource;
        public Parameters parameters;

    }
}


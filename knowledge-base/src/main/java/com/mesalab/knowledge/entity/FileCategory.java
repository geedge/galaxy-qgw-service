package com.mesalab.knowledge.entity;

public enum FileCategory {
    APP("APP"),
    IP("IP"),
    DOMAIN("DOMAIN");
    private String value;
    FileCategory(String value){
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

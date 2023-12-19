package com.mesalab.common.enums;

public enum FileFormat {
    MMDB("MMDB"),
    CSV("CSV"),
    TXT("TXT");
    private String value;
    FileFormat(String value){
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

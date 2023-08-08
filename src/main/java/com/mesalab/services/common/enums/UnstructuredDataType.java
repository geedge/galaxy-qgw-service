package com.mesalab.services.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UnstructuredDataType {
    ALL("all"),
    MAIL("mail"),
    HTTP("http"),
    PCAP("pcap");
    private final String type;
}

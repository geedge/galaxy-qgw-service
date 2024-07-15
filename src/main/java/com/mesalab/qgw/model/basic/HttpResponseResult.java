package com.mesalab.qgw.model.basic;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class HttpResponseResult implements Serializable {
    private Integer statusCode;
    private String errorMessage;
    private Map<String, String> responseHeaders;
    private String responseBody;


}

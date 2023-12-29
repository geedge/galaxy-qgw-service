package com.mesalab.common.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class HttpResponseResult implements Serializable {
    private Integer statusCode;
    private String errorMessage;
    private String responseBody;


}

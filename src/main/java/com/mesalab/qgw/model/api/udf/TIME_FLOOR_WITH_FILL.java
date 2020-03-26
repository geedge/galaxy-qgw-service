package com.mesalab.qgw.model.api.udf;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.sf.jsqlparser.expression.Expression;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TIME_FLOOR_WITH_FILL implements UDF{
    private String name;
    private Expression timestamp;
    private Expression period;
    private Expression fill;

    public TIME_FLOOR_WITH_FILL(UDFElements udfElements) {
        this.name = udfElements.getName();
        this.timestamp = udfElements.getParams().get(0);
        this.period = udfElements.getParams().get(1);
        this.fill = udfElements.getParams().get(2);
    }
}

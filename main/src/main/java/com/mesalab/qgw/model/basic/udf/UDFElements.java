package com.mesalab.qgw.model.basic.udf;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.sf.jsqlparser.expression.Expression;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UDFElements {
    private String name;
    private List<Expression> params;
}

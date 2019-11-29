package com.mesalab.common.base;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaBase implements Serializable {

    private String type;
    private String name;
    private String namespace;
    private List<Map> fields;

    public String getNamespace() {
        return namespace = "com.zdjizhi";
    }

    public String getType() {
        return type = "record";
    }
}

package com.mesalab.qgw.model.metadata;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Date: 2021-03-11 17:49
 * @Author : liuyongqiang
 * @ClassName : MetadataBean
 * @Description : MetadataBean
 */
@Data
public class MetadataBean implements Serializable {

    private String namespace;
    private String group;
    private List<String> tables;

}

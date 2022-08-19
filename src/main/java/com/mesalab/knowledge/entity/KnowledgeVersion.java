package com.mesalab.knowledge.entity;

import lombok.Data;


/**
 * @Author wxs
 * @Date 2022/7/25
 */
@Data
public class KnowledgeVersion {

    private String ipV4UserDefined;

    private String ipV4BuiltIn;

    private String ipV6UserDefined;

    private String ipV6BuiltIn;

}

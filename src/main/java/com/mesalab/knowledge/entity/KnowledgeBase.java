package com.mesalab.knowledge.entity;

import lombok.Data;

/**
 * @Author wxs
 * @Date 2022/7/5
 */
@Data
public class KnowledgeBase {

    private String id;

    private String name;

    private String type;

    private String path;

    private String updateTime;

    private Long size;

    private String format;

    private String sha256;

    private String version;

}

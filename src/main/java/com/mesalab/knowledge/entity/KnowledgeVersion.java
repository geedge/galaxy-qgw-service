package com.mesalab.knowledge.entity;

import lombok.Data;

import java.util.List;


/**
 * @Author wxs
 * @Date 2022/7/25
 */
@Data
public class KnowledgeVersion {

    private List<Ip> ip_location;



    @Data
    public static class Ip {
        private Info ip_v4_user_defined;

        private Info ip_v6_user_defined;

        private Info ip_v4_built_in;

        private Info ip_v6_built_in;
    }
    @Data
    public static class Info {
        private String file_name;
        private String version;
    }

}

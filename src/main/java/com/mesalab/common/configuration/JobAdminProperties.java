package com.mesalab.common.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "xxl-job-admin")
public class JobAdminProperties {

    private String url;

    private String userName;

    private String password;

    private String druidDeleteNode;

    private String clickhouseLocalTable;

}

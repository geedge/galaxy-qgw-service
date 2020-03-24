package com.mesalab.qgw.model.api;

import lombok.Data;

import java.io.Serializable;

@Data
public class JobAdminHttpSource implements Serializable {

    private String url;

    private String userName;

    private String password;

    private String druidDeleteNode;

    private String clickhouseLocalTable;

}

package com.mesalab.qgw.model.api;

import com.mesalab.common.configuration.ClickHouseProperties;
import lombok.Data;

import java.io.Serializable;
@Data
public class ClickHouseHttpSource implements Serializable {

    private String url;

    private String dbName;

    private ClickHouseProperties.DBAccount realTimeAccount ;

    private ClickHouseProperties.DBAccount longTermAccount;

    private int enableHttpCompression = 1;

    private  String systemDBName = "system";

    private long socketTimeout;

}

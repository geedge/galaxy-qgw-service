package com.mesalab.qgw.model.api;

import com.mesalab.common.configuration.DBAccount;
import lombok.Data;

import java.io.Serializable;
@Data
public class ClickHouseHttpSource implements Serializable {

    private String url;

    private long socketTimeout;

    private DBAccount realTimeAccount;

    private DBAccount longTimeAccount;

    private int enableHttpCompression = 1;

    private String partitionKey;

}

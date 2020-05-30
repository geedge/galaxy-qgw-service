package com.mesalab.qgw.model.api;

import lombok.Data;

import java.io.Serializable;

@Data
public class EngineConfigSource implements Serializable {

    private int maxCacheNum;
    private int defaultResultNum;

}

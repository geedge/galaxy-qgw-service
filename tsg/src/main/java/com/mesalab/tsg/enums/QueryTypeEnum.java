package com.mesalab.tsg.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @description: for queryType
 * @author: zhq
 * @create: 2020-07-16
 **/
@Getter
@AllArgsConstructor
public enum QueryTypeEnum {


    IPLEARNING("iplearning"),
    IPPOOL("ippool"),
    SUBSCRIBERIDPOOL("subscriberidpool");
    
    
    private final String type;

}

package com.mesalab.knowledge.enums;

import lombok.AllArgsConstructor;

/**
 * @description: for queryType
 * @author: zhq
 * @create: 2020-07-16
 **/
@AllArgsConstructor
public enum QueryTypeEnum {


    IPLEARNING("iplearning"),
    IPPOOL("ippool"),
    SUBSCRIBERIDPOOL("subscriberidpool");
    
    
    private final String type;

    public String getType() {
        return type;
    }



    public static QueryTypeEnum match(String name) {
        if (name != null) {
            for (QueryTypeEnum item : QueryTypeEnum.values()) {
                if (item.type.equals(name)) {
                    return item;
                }
            }
        }
        return null;
    }
}

package com.mesalab.qgw.model.basic;

import lombok.Data;

/**
 * @Date: 2020-09-22 10:45
 * @Author : liuyongqiang
 * @ClassName : ClickHouseHttpQuery
 * @Description : ClickHouseHttpQuery
 */
@Data
public class ClickHouseHttpQuery {
    //查询参数
    private String queryParameter;
    //响应超时时间
    private int socketTimeOut;
}

package com.mesalab.knowledge.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @description: QL对象类
 * @author: zhq
 * @create: 2020-07-02
 **/
@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class QLConditions {

    private String queryType;

    private String datasource;

    private String qlFilter;



}

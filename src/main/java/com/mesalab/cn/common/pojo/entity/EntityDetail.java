package com.mesalab.cn.common.pojo.entity;

import cn.hutool.core.annotation.Alias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @description:
 * @author: zhq
 * @create: 2021-07-05
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityDetail {

    private String name;
    private String type;
    private String from;
    private String to;
    private Long lastTime;
    private List<Object> leaf;

}

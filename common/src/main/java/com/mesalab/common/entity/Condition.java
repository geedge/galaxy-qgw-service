package com.mesalab.common.entity;

import lombok.Data;

import java.util.List;

/**
 * @author wangwei
 * @description:
 * @date 2020/11/16 4:02 下午
 */
@Data
public class Condition {
    private String type;
    private String fieldKey;
    private List<Object> fieldValues;
}
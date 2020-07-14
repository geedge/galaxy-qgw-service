package com.mesalab.network.model.api;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import javax.validation.Valid;

/**
 * @author wangwei
 * @version 1.0
 * @date 2020/6/30 1:52 下午
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Param {

    private String clientId;
    @Valid
    private QueryParam query;

}

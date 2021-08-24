package com.mesalab.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
/*
* @description: for datasource
* @author: tiangaochao
* @create: 2021-08-24
* */

@Getter
@AllArgsConstructor
public enum EntityQueryTypeEnum {
    ACTIVECLIENTIP("activeclientip"),
    TOPSERVERIP("topserverip"),
    TOPSNI("topsni");
    private final String type;
}

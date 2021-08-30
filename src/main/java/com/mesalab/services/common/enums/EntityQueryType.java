package com.mesalab.services.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @description: for datasource
 * @author: tiangaochao
 * @create: 2021-08-24
 */

@Getter
@AllArgsConstructor
public enum EntityQueryType {
    ACTIVECLIENTIP("activeclientip"),
    TOPSERVERIP("topserverip"),
    TOPSNI("topsni");
    private final String type;
}

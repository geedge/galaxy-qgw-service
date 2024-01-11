package com.mesalab.common.utils;

import org.slf4j.MDC;

/**
 * TODO
 *
 * @Classname MDCUtil
 * @Date 2024/1/10 18:11
 * @Author wWei
 */
public class MDCUtil {

    public static String getTraceId() {
        return MDC.get("traceId");
    }
}

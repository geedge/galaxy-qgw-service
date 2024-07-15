package com.mesalab.qgw.service;

import com.mesalab.common.entity.BaseResult;
import com.mesalab.qgw.model.basic.DSLQueryRequestParam;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * packet combine service
 *
 * @Classname PacketCombineDslService
 * @Date 2024/4/19 15:29
 * @Author wWei
 */
public interface PacketCombineDslService {

    BaseResult run(DSLQueryRequestParam dslQueryRequestParam);

    List<Map<String, Object>> getPackets(String sql);

    byte[] packetCombine(List<Map<String, Object>> records);

    Object uploadFile(String fileName, InputStream inputStream);
}

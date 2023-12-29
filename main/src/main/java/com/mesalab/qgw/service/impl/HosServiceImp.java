package com.mesalab.qgw.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.XmlUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.httpClient.HttpClientService;
import com.mesalab.knowledge.configuration.HosConfig;
import com.mesalab.qgw.service.HosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service(value = "hosService")
public class HosServiceImp implements HosService {

    @Autowired
    private HosConfig hosConfig;
    @Autowired
    private HttpClientService httpClientService;
    private static final String PREFIX = "prefix";
    private static final String TOKEN = "token";
    private static final String CONTENTS = "Contents";
    private static final String USER_DEFINED_META = "UserDefinedMeta";
    private static final String KEY = "Key";
    private static final String SIZE = "Size";
    private static final String CHUNK_COUNT = "ChunkCount";
    private static final String REQUEST_ID = "RequestId";
    private static final String MESSAGE = "Message";
    private static final String KEY_COUNT= "KeyCount";

    @Override
    public BaseResult getFileList(String bucketName, String prefix) {
        Map<String, String> map = getDataByHos(bucketName, prefix);
        Map<String, Object> stringObjectMap = XmlUtil.xmlToMap(String.valueOf(map.get("result")));
        if (Integer.parseInt(map.get("status")) == ResultStatusEnum.SUCCESS.getCode()) {
            List<Map<String, Object>> dataList = parseXmlData(bucketName, stringObjectMap);
            return BaseResultGenerator.success(dataList);
        } else {
            return BaseResultGenerator.failure(Integer.parseInt(map.get("status")), ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(), String.valueOf(stringObjectMap.get(REQUEST_ID)), String.valueOf(stringObjectMap.get(MESSAGE)));
        }
    }

    private List<Map<String, Object>> parseXmlData(String bucketName, Map<String, Object> map) {
        List<Map<String, Object>> dataList = Lists.newArrayList();
        int keyCount = Integer.parseInt(String.valueOf(map.get(KEY_COUNT)));
        switch (keyCount) {
            case 0:
                return dataList;
            case 1:
                dataList = parseSingleValue(bucketName, map);
                break;
            default:
                dataList = parseMultipleValue(bucketName, map);
        }
        return dataList;
    }

    private List<Map<String, Object>> parseMultipleValue(String bucketName, Map<String, Object> map) {
        List<Map<String, Object>> dataList = Lists.newArrayList();
        List<Map<String, String>> contentList = (List<Map<String, String>>) map.get(CONTENTS);
        if (CollectionUtil.isNotEmpty(contentList)) {
            for (Map contents : contentList) {
                Map<String, Object> dataMap = buildData(bucketName, contents);
                dataList.add(dataMap);
            }
        }
        return dataList;
    }

    private List<Map<String, Object>> parseSingleValue(String bucketName, Map<String, Object> map) {
        List<Map<String, Object>> dataList = Lists.newArrayList();
        Map<String, String> contentMap = (Map<String, String>) map.get(CONTENTS);
        Map<String, Object> stringObjectMap = buildData(bucketName, contentMap);
        dataList.add(stringObjectMap);
        return dataList;
    }

    private Map<String, Object> buildData(String bucketName, Map contents) {
        Map<String, Object> dataMap = Maps.newHashMap();
        String key = String.valueOf(contents.get(KEY));
        dataMap.put("size", Integer.parseInt(String.valueOf(contents.get(SIZE))));
        dataMap.put("fileUrl", hosConfig.getUri().concat("/").concat(bucketName).concat("/").concat(key));
        if (MapUtil.isNotEmpty((Map<?, ?>) contents.get(USER_DEFINED_META))) {
            Map<String, String> udfMeta = (Map<String, String>) contents.get(USER_DEFINED_META);
            String fileType = StrUtil.isBlankIfStr(udfMeta.get("FileType")) ? "" : udfMeta.get("FileType");
            Integer chunkCount = StrUtil.isBlankIfStr(udfMeta.get(CHUNK_COUNT)) ? 0 : Integer.parseInt(udfMeta.get(CHUNK_COUNT));
            dataMap.put("fileName", key.concat(".").concat(fileType));
            dataMap.put("chunkCount", chunkCount);
        } else {
            dataMap.put("fileName", key);
            dataMap.put("chunkCount", 0);
        }
        return dataMap;
    }

    private Map<String, String> getDataByHos(String bucketName, String prefix) {
        String url = hosConfig.getUri().concat("/").concat(bucketName).concat("/?").concat(PREFIX).concat("=").concat(prefix);
        Map<String, String> headers = new HashMap<>();
        headers.put(TOKEN, hosConfig.getToken());
        Map<String, String> result = httpClientService.httpGet(url, headers, 60000);
        return result;
    }
}

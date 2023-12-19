package com.mesalab.services.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.XmlUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.geedgenetworks.utils.DateUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.common.utils.RandomUtil;
import com.mesalab.qgw.model.basic.HttpConfig;
import com.mesalab.qgw.model.basic.HttpResponseResult;
import com.mesalab.qgw.service.impl.HttpClientServiceV2;
import com.mesalab.services.common.entity.KnowledgeBaseRequest;
import com.mesalab.services.common.entity.KnowledgeConstant;
import com.mesalab.services.configuration.HosConfig;
import com.mesalab.services.service.KBService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("kbService")
public class KBServiceImp implements KBService {
    private static final Log log = LogFactory.get();
    @Autowired
    private HosConfig hosConfig;
    @Autowired
    private HttpClientServiceV2 httpClientServiceV2;
    @Autowired
    private HttpConfig httpConfig;
    private static final String X_HOS_METADATA_PREFIX = "x-hos-meta-";
    private static final String INITIAL_VERSION = "1.0";
    private static final String NAME = "Name";
    private static final String CATEGORY = "Category";
    private static final String ORIGIN_URL = "OriginUrl";
    private static final String GENERATED_TIME = "GeneratedTime";
    private static final String LAST_UPDATE_TIME = "LastUpdateTime";
    private static final String FORMAT = "Format";
    private static final String SHA_256 = "Sha256";
    private static final String VERSION = "Version";
    private static final String IS_VALID = "IsValid";
    private static final String KEY = "Key";
    private static final String SIZE = "Size";
    private static final String USER_DEFINED_META = "UserDefinedMeta";

    @Override
    public BaseResult publishKnowledge(MultipartFile file, KnowledgeBaseRequest request) {
        List<Map<String, Object>> dataList;
        if (StrUtil.isBlank(request.getKbId()) && StrUtil.isBlank(request.getVersion())) {
            request.setKbId(RandomUtil.getUUID());
            request.setVersion(INITIAL_VERSION);
        } else {
            if (StrUtil.isBlankIfStr(request.getVersion())) {
                request.setVersion(generateLatestVersion(request));
            }
        }
        publishFile(file, request);
        generateLatestFile(file, request);

        String getFileListUrl = hosConfig.getUri().concat("/").concat(hosConfig.getBucket()).concat("?").concat("prefix").concat("=").concat(request.getKbId());
        dataList = generateResult(getContentList(getFileListUrl));
        return BaseResultGenerator.success(dataList);
    }


    @Override
    public BaseResult updateKnowledge(MultipartFile file, KnowledgeBaseRequest request) {
        List<Map<String, Object>> dataList = Lists.newArrayList();
        Map<String, String> headers = buildHeaderMap(request);
        publishFile(file, request);
        dataList.add(generateSingleResult(request, headers));
        return BaseResultGenerator.success(dataList);
    }

    @Override
    public BaseResult deleteKnowledge(KnowledgeBaseRequest request) {
        List<Map<String, Object>> dataList = Lists.newArrayList();
        if (StrUtil.isBlankIfStr(request.getVersion())) {
            String getFileListUrl = hosConfig.getUri().concat("/").concat(hosConfig.getBucket()).concat("?").concat("prefix").concat("=").concat(request.getKbId());
            dataList = generateResult(getContentList(getFileListUrl));
            deleteFileById(request, getContentList(getFileListUrl));
        } else {
            deleteFile(request);
            dataList.add(generateSingleResult(request, Maps.newHashMap()));
        }
        return BaseResultGenerator.success(dataList);
    }

    @Override
    public BaseResult updateStatus(KnowledgeBaseRequest request) {
        List<Map<String, Object>> dataList = Lists.newArrayList();
        if (StrUtil.isBlankIfStr(request.getVersion())) {
            String getFileListUrl = hosConfig.getUri().concat("/").concat(hosConfig.getBucket()).concat("?").concat("prefix").concat("=").concat(request.getKbId());
            dataList = generateResult(getContentList(getFileListUrl));
            updateFileStatusById(request, getContentList(getFileListUrl));
        } else {
            updateFileStatus(request, buildHeaderMap(request));
            dataList.add(generateSingleResult(request, buildHeaderMap(request)));
        }
        return BaseResultGenerator.success(dataList);
    }


    @Override
    public BaseResult getList(String prefix) {
        String getListUrl;
        if (StrUtil.isNotBlank(prefix)) {
            getListUrl = hosConfig.getUri().concat("/").concat(hosConfig.getBucket()).concat("?").concat("prefix=").concat(prefix);
        } else {
            getListUrl = hosConfig.getUri().concat("/").concat(hosConfig.getBucket());
        }
        List<Map<String, String>> contentList = getContentList(getListUrl);
        return BaseResultGenerator.success(generateFileListResult(contentList));
    }

    private void generateLatestFile(MultipartFile file, KnowledgeBaseRequest request) {
        request.setVersion(KnowledgeConstant.LATEST);
        String currentDate = DateUtils.getCurrentDate("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        request.setOriginUrl(hosConfig.getUri().concat("/").concat(hosConfig.getBucket()).concat("/").concat(request.getKbId().concat("_").concat("latest")));
        request.setGeneratedTime(StrUtil.isBlank(request.getGeneratedTime()) ? currentDate : request.getGeneratedTime());
        request.setLastUpdateTime(StrUtil.isBlank(request.getLastUpdateTime()) ? currentDate : request.getLastUpdateTime());
        publishFile(file, request);
    }

    private String generateLatestVersion(KnowledgeBaseRequest request) {
        String getLatestFileUrl = hosConfig.getUri().concat("/").concat(hosConfig.getBucket()).concat("?").concat("prefix").concat("=").concat(request.getKbId());
        List<Map<String, String>> contentList = getContentList(getLatestFileUrl);
        double max = 1.0;
        double now;
        if (CollectionUtil.isNotEmpty(contentList)) {
            for (Map contents : contentList) {
                Map<String, String> udfMeta = (Map<String, String>) contents.get(USER_DEFINED_META);
                if (MapUtil.isNotEmpty(udfMeta)) {
                    if (!String.valueOf(udfMeta.get(VERSION)).equals(KnowledgeConstant.LATEST)) {
                        now = Double.parseDouble(String.valueOf(udfMeta.get(VERSION)));
                        max = now > max ? now : max;
                    }
                }
            }
        }
        String version = incrementVersion(Double.toString(max));
        return version;
    }

    private List<Map<String, String>> getContentList(String getLatestFileUrl) {
        Map<String, String> headers = new HashMap<>();
        headers.put(KnowledgeConstant.TOKEN, hosConfig.getToken());
        HttpResponseResult httpResponseResult = httpClientServiceV2.get(getLatestFileUrl, 60000, headers);
        Map<String, Object> stringObjectMap = XmlUtil.xmlToMap(String.valueOf(httpResponseResult.getResponseBody()));
        List<Map<String, String>> contentList = (List<Map<String, String>>) stringObjectMap.get("Contents");
        return contentList;
    }


    private Map<String, String> buildHeaderMap(KnowledgeBaseRequest request) {
        Map<String, String> headers = Maps.newHashMap();
        String currentDate = DateUtils.getCurrentDate("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        request.setOriginUrl(StrUtil.isBlank(request.getOriginUrl()) ? hosConfig.getUri().concat("/").concat(hosConfig.getBucket()).concat("/").concat(request.getKbId().concat("_").concat(request.getVersion())) : request.getOriginUrl());
        request.setGeneratedTime(StrUtil.isBlank(request.getGeneratedTime()) ? currentDate : request.getGeneratedTime());
        request.setLastUpdateTime(StrUtil.isBlank(request.getLastUpdateTime()) ? currentDate : request.getLastUpdateTime());
        headers.put(KnowledgeConstant.TOKEN, hosConfig.getToken());
        Field[] declaredFields = request.getClass().getDeclaredFields();
        for (Field field : declaredFields) {
            field.setAccessible(true);
            String fieldName = field.getName();
            if (!fieldName.equals("kb_id")) {
                try {
                    Object o = field.get(request);
                    if (o != null) {
                        headers.put(X_HOS_METADATA_PREFIX.concat(StrUtil.toSymbolCase(fieldName, '-')), o.toString());
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return headers;
    }

    private void publishFile(MultipartFile file, KnowledgeBaseRequest request) {
        String putFileUrl = hosConfig.getUri().concat("/").concat(hosConfig.getBucket()).concat("/").concat(request.getKbId().concat("_").concat(request.getVersion()));
        try {
            request.setSha256(DigestUtil.sha256Hex(file.getBytes()));
            Map<String, String> headers = buildHeaderMap(request);
            InputStream in = new ByteArrayInputStream(file.getBytes());
            HttpResponseResult put = httpClientServiceV2.put(putFileUrl, in, httpConfig.getServerResponseTimeOut(), headers);
            if (HttpStatusCodeEnum.SUCCESS.getCode() != put.getStatusCode()) {
                log.error("knowledge base file upload failed.");
                throw new BusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), "knowledge base file upload failed.");
            }
        } catch (IOException e) {
            throw new BusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), e.getMessage());
        }
    }

    private void deleteFileById(KnowledgeBaseRequest request, List<Map<String, String>> contentList) {
        for (Map contents : contentList) {
            Map<String, String> udfMeta = (Map<String, String>) contents.get(USER_DEFINED_META);
            request.setVersion(String.valueOf(udfMeta.get(VERSION)));
            deleteFile(request);
        }
    }

    private void deleteFile(KnowledgeBaseRequest request) {
        String fileName = request.getKbId().concat("_").concat(request.getVersion());
        String deleteFileUrl = hosConfig.getUri().concat("/").concat(hosConfig.getBucket()).concat("/").concat(fileName);
        Map<String, String> headers = Maps.newHashMap();
        headers.put(KnowledgeConstant.TOKEN, hosConfig.getToken());
        try {
            httpClientServiceV2.delete(deleteFileUrl, httpConfig.getServerRequestTimeOut(), headers);
        } catch (RuntimeException e) {
            log.error("knowledge base file delete error: {}", e.getMessage());
            throw new BusinessException(e.getMessage());
        }
    }

    private void updateFileStatusById(KnowledgeBaseRequest request, List<Map<String, String>> contentList) {
        for (Map contents : contentList) {
            Map<String, String> udfMeta = (Map<String, String>) contents.get(USER_DEFINED_META);
            request.setVersion(String.valueOf(udfMeta.get(VERSION)));
            updateFileStatus(request, buildHeaderMap(request));
        }
    }

    private void updateFileStatus(KnowledgeBaseRequest request, Map<String, String> headers) {
        String fileName = request.getKbId().concat("_").concat(request.getVersion());
        String updateStatusFileUrl = hosConfig.getUri().concat("/").concat(hosConfig.getBucket()).concat("/").concat(fileName).concat("?metadata");
        HttpResponseResult put = httpClientServiceV2.put(updateStatusFileUrl, null, httpConfig.getServerResponseTimeOut(), headers);
        if (put.getStatusCode() != HttpStatusCodeEnum.SUCCESS.getCode()) {
            log.error("knowledge base file update status failed.");
            throw new BusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), "knowledge base file update status failed.");
        }
    }

    private List<Map<String, Object>> generateResult(List<Map<String, String>> contentList) {
        List<Map<String, Object>> dataList = Lists.newArrayList();
        if (CollectionUtil.isNotEmpty(contentList)) {
            for (Map contents : contentList) {
                Map<String, Object> map1 = Maps.newHashMap();
                Map<String, String> udfMeta = (Map<String, String>) contents.get(USER_DEFINED_META);
                map1.put("version", String.valueOf(udfMeta.get(VERSION)));
                map1.put("url", String.valueOf(udfMeta.get(ORIGIN_URL)));
                map1.put("generated_time", String.valueOf(udfMeta.get(GENERATED_TIME)));
                map1.put("last_update_time", String.valueOf(udfMeta.get(LAST_UPDATE_TIME)));
                map1.put("kb_id", StrUtil.subBefore(String.valueOf(contents.get(KEY)), "_", false));
                dataList.add(map1);
            }
        }
        return dataList;
    }

    private Map<String, Object> generateSingleResult(KnowledgeBaseRequest request, Map<String, String> headers) {
        Map<String, Object> dataMap = Maps.newHashMap();
        String currentDate = DateUtils.getCurrentDate("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        dataMap.put("version", request.getVersion());
        dataMap.put("url", hosConfig.getUri().concat("/").concat(hosConfig.getBucket()).concat("/").concat(request.getKbId().concat("_").concat(request.getVersion())));
        dataMap.put("generated_time", StrUtil.isNotBlank(headers.get(X_HOS_METADATA_PREFIX.concat("generated-time"))) ? headers.get(X_HOS_METADATA_PREFIX.concat("generated-time")) : currentDate);
        dataMap.put("last_update_time", StrUtil.isNotBlank(headers.get(X_HOS_METADATA_PREFIX.concat("last-update-time"))) ? headers.get(X_HOS_METADATA_PREFIX.concat("last-update-time")) : currentDate);
        dataMap.put("kb_id", request.getKbId());
        return dataMap;
    }

    private List<Map<String, Object>> generateFileListResult(List<Map<String, String>> contentList) {
        List<Map<String, Object>> dataList = Lists.newArrayList();
        if (CollectionUtil.isNotEmpty(contentList)) {
            for (Map contents : contentList) {
                Map<String, Object> map1 = Maps.newHashMap();
                if (MapUtil.isNotEmpty((Map<?, ?>) contents.get(USER_DEFINED_META))) {
                    Map<String, String> udfMeta = (Map<String, String>) contents.get(USER_DEFINED_META);
                    map1.put("name", StrUtil.isBlankIfStr(udfMeta.get(NAME)) ? "" : udfMeta.get(NAME));
                    map1.put("category", StrUtil.isBlankIfStr(udfMeta.get(CATEGORY)) ? "" : udfMeta.get(CATEGORY));
                    map1.put("path", StrUtil.isBlankIfStr(udfMeta.get(ORIGIN_URL)) ? "" : udfMeta.get(ORIGIN_URL));
                    map1.put("generated_time", StrUtil.isBlankIfStr(udfMeta.get(GENERATED_TIME)) ? "" : udfMeta.get(GENERATED_TIME));
                    map1.put("last_update_time", StrUtil.isBlankIfStr(udfMeta.get(LAST_UPDATE_TIME)) ? "" : udfMeta.get(LAST_UPDATE_TIME));
                    map1.put("format", StrUtil.isBlankIfStr(udfMeta.get(FORMAT)) ? "" : udfMeta.get(FORMAT));
                    map1.put("sha256", StrUtil.isBlankIfStr(udfMeta.get(SHA_256)) ? "" : udfMeta.get(SHA_256));
                    map1.put("version", StrUtil.isBlankIfStr(udfMeta.get(VERSION)) ? "" : udfMeta.get(VERSION));
                    map1.put("is_valid", StrUtil.isBlankIfStr(udfMeta.get(IS_VALID)) ? 1 : Integer.parseInt(udfMeta.get(IS_VALID)));
                } else {
                    map1.put("name", "");
                    map1.put("category", "");
                    map1.put("path", "");
                    map1.put("generated_time", "");
                    map1.put("last_update_time", "");
                    map1.put("format", "");
                    map1.put("sha256", "");
                    map1.put("version", "");
                    map1.put("is_valid", 1);
                }
                map1.put("kb_id", StrUtil.subBefore(String.valueOf(contents.get(KEY)), "_", false));
                map1.put("size", StrUtil.isBlankIfStr(contents.get(SIZE)) ? 0 : Integer.parseInt(String.valueOf(contents.get(SIZE))));
                dataList.add(map1);
            }
        }
        return dataList;
    }

    private String incrementVersion(String version) {
        String[] parts = version.split("\\.");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            nums[i] = Integer.parseInt(parts[i]);
        }

        int idx = nums.length - 1;
        nums[idx]++;
        while (nums[idx] == 10 && idx > 0) {
            nums[idx] = 0;
            nums[--idx]++;
        }

        StringBuilder sb = new StringBuilder();
        for (int num : nums) {
            sb.append(num);
            sb.append('.');
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

}

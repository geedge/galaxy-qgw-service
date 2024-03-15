package com.mesalab.qgw.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.XmlUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.HttpConfig;
import com.mesalab.qgw.model.basic.HttpResponseResult;
import com.mesalab.qgw.service.HosService;
import com.mesalab.services.common.entity.KnowledgeConstant;
import com.mesalab.services.configuration.HosConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service(value = "hosService")
public class HosServiceImp implements HosService {

    private HosConfig hosConfig;
    private HttpConfig httpConfig;
    private HttpClientServiceV2 httpClientServiceV2;
    private HttpClientService httpClientService;
    private static final String TOKEN = "token";
    private static final String CONTENTS = "Contents";
    private static final String USER_DEFINED_META = "UserDefinedMeta";
    private static final String KEY = "Key";
    private static final String LAST_MODIFIED = "LastModified";
    private static final String SIZE = "Size";
    private static final String CHUNK_COUNT = "ChunkCount";
    private static final String REQUEST_ID = "RequestId";
    private static final String MESSAGE = "Message";
    private static final String KEY_COUNT = "KeyCount";
    private static final String FILE_TYPE = "FileType";
    private static final String TASK_ID = "TaskId";
    private static final String RULE_ID = "RuleId";

    @Override
    public BaseResult getFile(String bucketName, String fileName) {
        Map<String, String> hosDefaultHeaders = getHosDefaultHeaders();
        String putFileUrl = hosConfig.getUri().concat("/").concat(bucketName).concat("/").concat(fileName);
        HttpResponseResult responseResult = httpClientServiceV2.head(putFileUrl, httpConfig.getServerResponseTimeOut(), hosDefaultHeaders);
        if (HttpStatusCodeEnum.SUCCESS.getCode() != responseResult.getStatusCode()) {
            throw new BusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), "Upload file failed, message is: " + responseResult.getErrorMessage());
        }
        Map<String, String> responseHeaders = responseResult.getResponseHeaders();
        Map<String, Object> result = Maps.newHashMap();
        result.put("fileName", fileName);
        result.put("size", StrUtil.isBlankIfStr(responseHeaders.get("File-Size")) ? null : Integer.parseInt(responseHeaders.get("File-Size")));
        result.put("fileUrl", hosConfig.getUri().concat("/").concat(bucketName).concat("/").concat(fileName));
        result.put("LastModified", responseHeaders.get("Last-Modified"));
        return BaseResultGenerator.success(Lists.newArrayList(result));
    }

    @Override
    public BaseResult getFileList(String bucketName, String prefix, Map<String, String> udfMetaParam) {
        Map<String, String> map = getDataByHos(bucketName, prefix, udfMetaParam);
        Map<String, Object> stringObjectMap = XmlUtil.xmlToMap(String.valueOf(map.get("result")));
        if (Integer.parseInt(map.get("status")) == HttpStatusCodeEnum.SUCCESS.getCode()) {
            List<Map<String, Object>> dataList = parseXmlData(bucketName, stringObjectMap);
            return BaseResultGenerator.success(dataList);
        } else {
            return BaseResultGenerator.failure(Integer.parseInt(map.get("status")), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), String.valueOf(stringObjectMap.get(REQUEST_ID)), String.valueOf(stringObjectMap.get(MESSAGE)));
        }
    }

    @Override
    public BaseResult uploadFile(String bucketName, String fileName, MultipartFile file) {
        Map<String, String> headers = getHosDefaultHeaders();
        try {
            String putFileUrl = hosConfig.getUri().concat("/").concat(bucketName).concat("/").concat(fileName);
            InputStream inputStream = new ByteArrayInputStream(file.getBytes());
            HttpResponseResult responseResult = httpClientServiceV2.put(putFileUrl, inputStream, httpConfig.getServerResponseTimeOut(), headers);
            if (HttpStatusCodeEnum.SUCCESS.getCode() != responseResult.getStatusCode()) {
                throw new BusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), "Upload file failed, message is: " + responseResult.getErrorMessage());
            }
        } catch (IOException e) {
            throw new BusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), e.getMessage());
        }
        return BaseResultGenerator.success();
    }

    @Override
    public BaseResult deleteFileList(String bucketName, List<String> fileNames) {
        Map<String, String> headers = getHosDefaultHeaders();
        headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML);
        String putFileUrl = hosConfig.getUri().concat("/").concat(bucketName).concat("/?delete");
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            Element deleteElement = doc.createElement("Delete");
            doc.appendChild(deleteElement);

            Element quietElement = doc.createElement("Quiet");
            quietElement.appendChild(doc.createTextNode("true"));

            deleteElement.appendChild(quietElement);
            fileNames.forEach(fileName -> deleteElement.appendChild(createElementWithKey(doc, fileName)));

            HttpResponseResult responseResult = httpClientServiceV2.post(putFileUrl, httpConfig.getServerResponseTimeOut(), XmlUtil.toStr(doc, true), headers);
            if (HttpStatusCodeEnum.SUCCESS.getCode() != responseResult.getStatusCode()) {
                throw new BusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), "Delete file failed, message is: " + responseResult.getErrorMessage());
            }
        } catch (ParserConfigurationException e) {
            throw new BusinessException(HttpStatusCodeEnum.SERVER_ERROR.getCode(), CommonErrorCode.UNKNOWN_EXCEPTION.getCode(), e.getMessage());
        }
        return BaseResultGenerator.success();
    }

    private Map<String, String> getHosDefaultHeaders() {
        Map<String, String> headers = Maps.newHashMap();
        headers.put(KnowledgeConstant.TOKEN, hosConfig.getToken());
        return headers;
    }

    private static Element createElementWithKey(Document doc, String key) {
        Element keyElement = doc.createElement("Key");
        keyElement.appendChild(doc.createTextNode(key));
        Element result = doc.createElement("Object");
        result.appendChild(keyElement);
        return result;
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
        dataMap.put("fileName", key);
        dataMap.put("size", StrUtil.isBlankIfStr(contents.get(SIZE)) ? 0 : Integer.parseInt(String.valueOf(contents.get(SIZE))));
        dataMap.put("fileUrl", hosConfig.getUri().concat("/").concat(bucketName).concat("/").concat(key));
        dataMap.put("lastModified", String.valueOf(contents.get(LAST_MODIFIED)));
        if (MapUtil.isNotEmpty((Map<?, ?>) contents.get(USER_DEFINED_META))) {
            Map<String, String> udfMeta = (Map<String, String>) contents.get(USER_DEFINED_META);
            Integer chunkCount = StrUtil.isBlankIfStr(udfMeta.get(CHUNK_COUNT)) ? 0 : Integer.parseInt(udfMeta.get(CHUNK_COUNT));
            String taskId = StrUtil.isBlankIfStr(udfMeta.get(TASK_ID)) ? "" : udfMeta.get(TASK_ID);
            String ruleId = StrUtil.isBlankIfStr(udfMeta.get(RULE_ID)) ? "" : udfMeta.get(RULE_ID);
            dataMap.put("taskId", taskId);
            dataMap.put("ruleId", ruleId);
            dataMap.put("chunkCount", chunkCount);
        } else {
            dataMap.put("taskId", "");
            dataMap.put("ruleId", "");
            dataMap.put("chunkCount", 0);
        }
        return dataMap;
    }

    private Map<String, String> getDataByHos(String bucketName, String prefix, Map<String, String> udfMetaParam) {
        String url;
        if (StrUtil.isNotBlank(prefix) || MapUtil.isNotEmpty(udfMetaParam)) {
            url = urlBuilder(bucketName, prefix, udfMetaParam);
        } else {
            url = hosConfig.getUri().concat("/").concat(bucketName);
        }
        Map<String, String> result = httpClientService.httpGet(url, getHosDefaultHeaders(), 60000);
        return result;
    }

    private String urlBuilder(String bucketName, String prefix, Map<String, String> udfMetaParam) {
        StringBuffer sb = new StringBuffer();
        sb.append(hosConfig.getUri().concat("/").concat(bucketName)).append("?");
        if (StrUtil.isNotBlank(prefix)) {
            sb.append("prefix=").append(prefix).append("&");
        }
        if (MapUtil.isNotEmpty(udfMetaParam)) {
            for (Map.Entry<String, String> entry : udfMetaParam.entrySet()) {
                String paramKey = StrUtil.toSymbolCase(entry.getKey(), '-');
                String paramValue = StrUtil.toSymbolCase(entry.getValue(), '-');
                sb.append(paramKey).append("=").append(paramValue).append("&");
            }
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    @Autowired
    private void setHosConfig(HosConfig hosConfig) {
        this.hosConfig = hosConfig;
    }

    @Autowired
    private void setHttpClientService(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
    }

    @Autowired
    private void setHttpClientServiceV2(HttpClientServiceV2 httpClientServiceV2) {
        this.httpClientServiceV2 = httpClientServiceV2;
    }

    @Autowired
    private void setHttpConfig(HttpConfig httpConfig) {
        this.httpConfig = httpConfig;
    }
}

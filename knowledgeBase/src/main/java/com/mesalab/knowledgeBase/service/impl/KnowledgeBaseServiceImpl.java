package com.mesalab.knowledgeBase.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileMode;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.XmlUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.knowledgeBase.entity.KnowledgeBase;
import com.mesalab.qgw.common.NacosConst;
import com.mesalab.qgw.config.HosConfig;
import com.mesalab.qgw.constant.KnowledgeConstant;
import com.mesalab.qgw.model.api.HttpConfig;
import com.mesalab.qgw.utils.HttpClientService;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.apache.commons.io.FileUtils;
import com.mesalab.knowledgeBase.service.KnowledgeBaseService;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicHeader;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.xpath.XPathConstants;
import java.io.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final Log log = LogFactory.get();

    @Autowired
    private ConfigService pubConfigService;

    @Autowired
    private HosConfig hosConfig;

    @Autowired
    private HttpClientService httpClientService;

    @Autowired
    private HttpConfig httpConfig;

    @Autowired
    private CuratorFramework client;

    private static final String ZK_LOCK_PATH = "/zkLock";
    private static final int HOS_PART_SIZE = 1024 * 1024 * 100;
    private static final String HOS_ETAG = "ETag";
    private static final String HOS_PART = "Part";
    private static final String HOS_PART_NUM = "PartNumber";
    private static final String HOS_COMPLETE_MULTIPART_UPLOAD = "CompleteMultipartUpload";


    @Override
    public BaseResult queryKnowledge() {
        List<KnowledgeBase> knowledgeBaseList = queryKnowledgeBaseList();
        return BaseResultGenerator.success(knowledgeBaseList);
    }

    @Override
    public BaseResult publishKnowledge(MultipartFile multipartFile, KnowledgeBase knowledge) throws Exception {

        InterProcessMutex lock = new InterProcessMutex(client, ZK_LOCK_PATH);
        try {
            KnowledgeBase knowledgeBase = null;
            if (lock.acquire(10000, TimeUnit.SECONDS)) {
                List<KnowledgeBase> knowledgeBaseList = queryKnowledgeBaseList();
                knowledgeBase = setKnowledgeBase(multipartFile, knowledge, knowledgeBaseList);
                Boolean b = updateLatestKnowledge(knowledgeBaseList, knowledgeBase);
                if (b) {
                    KnowledgeBase base = saveLatestKnowledge(knowledgeBase);
                    knowledgeBaseList.add(base);
                }
                knowledgeBaseList.add(knowledgeBase);
                saveKnowledgeBaseList(knowledgeBaseList);
            }

            Map map = new HashMap();
            map.put("id", knowledgeBase.getId());
            return BaseResultGenerator.success(map);
        } catch (RuntimeException e) {
            log.error("publish knowledge error: {}", e.getMessage());
            throw new BusinessException(e.getMessage());
        } finally {
            lock.release();
        }

    }

    @Override
    public BaseResult updateKnowledge(MultipartFile file, KnowledgeBase knowledgeBase) throws Exception {

        InterProcessMutex lock = new InterProcessMutex(client, ZK_LOCK_PATH);
        String id = knowledgeBase.getId();
        try {
            // 尝试获取锁，最长等待10s，超时放弃获取
            if (lock.acquire(10000, TimeUnit.SECONDS)) {
                List<KnowledgeBase> knowledgeBaseList = queryKnowledgeBaseList();
                String deletePath = null;
                for (int i = 0; i < knowledgeBaseList.size(); i++) {
                    KnowledgeBase knowledge = knowledgeBaseList.get(i);
                    if (StringUtil.equals(id, knowledge.getId()) && !StringUtil.equals(knowledge.getVersion(), KnowledgeConstant.LATEST)) {
                        if (StringUtil.equals(knowledge.getId(), knowledgeBase.getId())) {
                            if (!StringUtil.equals(knowledgeBase.getVersion(), KnowledgeConstant.LATEST)) {
                                deletePath = knowledge.getPath();
                                String path = uploadFile(file);
                                String sha256Hex = DigestUtil.sha256Hex(file.getBytes());
                                knowledge.setPath(path);
                                knowledge.setSha256(sha256Hex);
                                knowledge.setSize(file.getSize());
                                knowledge.setUpdateTime(DateUtils.getFormatDate(new Date(), "yyyy-MM-dd'T'HH:mm:sss"));
                                updateLatestKnowledge(knowledgeBaseList, knowledge);
                                break;
                            }
                        }
                    }
                }
                saveKnowledgeBaseList(knowledgeBaseList);
                Header header = new BasicHeader(KnowledgeConstant.TOKEN, hosConfig.getToken());
                httpClientService.httpDelete(deletePath, httpConfig.getServerResponseTimeOut(), header);
            }
        } catch (RuntimeException e) {
            log.error("update knowledge error: {}", e.getMessage());
            throw new BusinessException(e.getMessage());
        } finally {
            lock.release();
        }
        Map map = new HashMap();
        map.put("id", id);
        return BaseResultGenerator.success(map);
    }

    @Override
    public BaseResult deleteKnowledge(String id) throws Exception {
        KnowledgeBase knowledgeBase = null;
        InterProcessMutex lock = new InterProcessMutex(client, ZK_LOCK_PATH);
        try {
            if (lock.acquire(10000, TimeUnit.SECONDS)) {
                List<KnowledgeBase> knowledgeBaseList = queryKnowledgeBaseList();
                int originalSize = knowledgeBaseList.size();
                Iterator<KnowledgeBase> iterator = knowledgeBaseList.iterator();
                while (iterator.hasNext()) {
                    knowledgeBase = iterator.next();
                    if (StringUtil.equals(id, knowledgeBase.getId())) {
                        iterator.remove();
                    }
                }
                if (originalSize > knowledgeBaseList.size()) {
                    saveKnowledgeBaseList(knowledgeBaseList);
                    String path = knowledgeBase.getPath();
                    Header header = new BasicHeader(KnowledgeConstant.TOKEN, hosConfig.getToken());
                    httpClientService.httpDelete(path, httpConfig.getServerResponseTimeOut(), header);
                }
            }
        } catch (RuntimeException e) {
            log.error("delete knowledge error: {}", e.getMessage());
            throw new BusinessException(e.getMessage());
        } finally {
            lock.release();
        }


        Map map = new HashMap();
        map.put("id", id);
        return BaseResultGenerator.success(map);
    }

    public List<KnowledgeBase> queryKnowledgeBaseList() {
        List<KnowledgeBase> knowledgeBaseList = new ArrayList<>();
        try {
            String config = pubConfigService.getConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, httpConfig.getServerRequestTimeOut());
            if (StringUtil.isNotEmpty(config)) {
                knowledgeBaseList = com.mesalab.common.utils.JsonMapper.fromJsonString(config, List.class, KnowledgeBase.class);
                knowledgeBaseList = knowledgeBaseList.stream().sorted(Comparator.comparing(KnowledgeBase::getUpdateTime).reversed()).collect(Collectors.toList());
            }
        } catch (NacosException e) {
            log.error("get knowledge meta error: {}", e.getMessage());
            throw new BusinessException(e.getMessage());
        }
        return knowledgeBaseList;
    }


    private KnowledgeBase setKnowledgeBase(MultipartFile file, KnowledgeBase knowledge, List<KnowledgeBase> knowledgeBaseList) throws IOException {

        String lastVersion = queryLsatKnowledgeVersion(knowledgeBaseList, knowledge);
        String version = generateVersion(lastVersion);
        String id = DigestUtil.md5Hex16(knowledge.getType())
                .concat("-")
                .concat(DigestUtil.md5Hex16(knowledge.getName()
                        .concat(knowledge.getFormat())
                        .concat(version)));
        String path = uploadFile(file);
        String date = DateUtils.getFormatDate(new Date(), "yyyy-MM-dd'T'HH:mm:sss");
        String sha256Hex = DigestUtil.sha256Hex(file.getBytes());
        knowledge.setId(id);
        knowledge.setPath(path);
        knowledge.setSha256(sha256Hex);
        knowledge.setVersion(version);
        knowledge.setSize(file.getSize());
        knowledge.setUpdateTime(date);
        return knowledge;
    }

    private String queryLsatKnowledgeVersion(List<KnowledgeBase> knowledgeBaseList, KnowledgeBase knowledge) {

        if (CollectionUtil.isNotEmpty(knowledgeBaseList)) {
            for (KnowledgeBase knowledgeBase : knowledgeBaseList) {
                if (!StringUtil.equals(knowledgeBase.getVersion(), KnowledgeConstant.LATEST)
                        && StringUtil.equals(knowledgeBase.getType(), knowledge.getType())
                        && StringUtil.equals(knowledgeBase.getName(), knowledge.getName())) {
                    return knowledgeBase.getVersion();
                }
            }
        }
        return null;
    }

    private void saveKnowledgeBaseList(List<KnowledgeBase> knowledgeBaseList) throws NacosException, InterruptedException {

        try {
            boolean b = pubConfigService.publishConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, JsonMapper.toJsonString(knowledgeBaseList));
            if (!b) {
                throw new BusinessException("update knowledge meta error.");
            }
        } catch (RuntimeException | NacosException e) {
            log.error("update knowledge meta error: {}", e.getMessage());
            throw new BusinessException(e.getMessage());
        }
        while (true) {
            Thread.sleep(100);
            String local = JsonMapper.toJsonString(knowledgeBaseList.stream().sorted(Comparator.comparing(KnowledgeBase::getUpdateTime).reversed()).collect(Collectors.toList()));
            String nacos = JsonMapper.toJsonString(queryKnowledgeBaseList());
            if (StringUtil.equals(local, nacos)) {
                break;
            }
        }
    }


    public String uploadFile(MultipartFile file) {
        String uri = hosConfig.getUri();
        String bucket = hosConfig.getBucket();
        Header header = new BasicHeader(KnowledgeConstant.TOKEN, hosConfig.getToken());
        String[] split = file.getOriginalFilename().split("\\.");
        String url = uri.concat("/").concat(bucket).concat("/").concat(UUID.randomUUID().toString())
                .concat("-").concat(Base64.getEncoder().encodeToString(split[0].getBytes())).concat(".").concat(split[1]);
        try {
            if (file.getBytes().length < hosConfig.getMultiUploadStartSize()) {
                InputStream in = new ByteArrayInputStream(file.getBytes());
                HttpResponse httpResponse = httpClientService.httpPutStream(url, in, httpConfig.getServerResponseTimeOut(), header);
                if (StringUtil.isEmpty(httpResponse) || httpResponse.getStatusLine().getStatusCode() != ResultStatusEnum.SUCCESS.getCode()) {
                    log.error("execute upload file failed.");
                    throw new BusinessException("upload file failed.");
                }
            } else {
                File fileTemp = null;
                try {
                    fileTemp = new File(System.currentTimeMillis() + file.getOriginalFilename());
                    String resBody = createMultipartUpload(url, header);
                    Document resXml = XmlUtil.parseXml(resBody);
                    Object uploadId = XmlUtil.getByXPath("//InitiateMultipartUploadResult/UploadId", resXml, XPathConstants.STRING);
                    Map<Integer, String> parts = Maps.newHashMap();
                    FileUtils.copyInputStreamToFile(file.getInputStream(), fileTemp);
                    RandomAccessFile randomAccessFile = FileUtil.createRandomAccessFile(fileTemp, FileMode.r);
                    long totalLength = randomAccessFile.length();
                    long hadReadLength = 0;
                    int count = 1;
                    while (hadReadLength < totalLength) {
                        byte[] waitUploadBytes = new byte[HOS_PART_SIZE <= (totalLength - hadReadLength) ? HOS_PART_SIZE : (int) (totalLength - hadReadLength)];
                        hadReadLength = hadReadLength + randomAccessFile.read(waitUploadBytes);
                        InputStream inputStream = new ByteArrayInputStream(waitUploadBytes);
                        HttpResponse httpResponse = uploadPart(url, uploadId, count, inputStream, header);
                        Header[] eTags = httpResponse.getHeaders(HOS_ETAG);
                        parts.put(count++, eTags[0].getValue());
                    }
                    Document completeBody = buildCompleteXmlReqBody(parts);
                    completeMultipartUpload(url, uploadId, completeBody, header);
                } catch (IOException | RuntimeException ex) {
                    log.error("execute upload part file failed.");
                    throw new BusinessException(ex.getMessage());
                } finally {
                    if (fileTemp != null && fileTemp.exists()) {
                        FileUtil.del(fileTemp);
                    }
                }
            }
        } catch (IOException e) {
            throw new BusinessException(e.getMessage());
        }
        return url;
    }

    private String createMultipartUpload(String url, Header header) throws BusinessException {
        String repStr = httpClientService.httpPost(url + "?uploads", null, header);
        if ("-1".equals(repStr)) {
            throw new BusinessException("create multipart upload failed");
        }
        return repStr;
    }

    private HttpResponse uploadPart(String url, Object uploadId, int times, InputStream inputStream, Header header) throws RuntimeException {
        String urlParams = StrFormatter.format("?partNumber={}&uploadId={}", times, uploadId);
        HttpResponse httpResponse = httpClientService.httpPutStream(url.concat(urlParams), inputStream, httpConfig.getServerResponseTimeOut(), header);
        if (StringUtil.isEmpty(httpResponse) || httpResponse.getStatusLine().getStatusCode() != ResultStatusEnum.SUCCESS.getCode()) {
            log.error("upload part file failed.");
            throw new BusinessException("upload part file failed.");
        }
        return httpResponse;
    }

    private void completeMultipartUpload(String url, Object uploadId, Document resXmlBody, Header header) throws RuntimeException {
        String repStr = httpClientService.httpPost(url.concat(StrFormatter.format("?uploadId={}", uploadId)), XmlUtil.toStr(resXmlBody), header);
        if ("-1".equals(repStr)) {
            throw new BusinessException("complete multipart upload failed");
        }
    }

    private Document buildCompleteXmlReqBody(Map<Integer, String> parts) {
        Document xml = XmlUtil.createXml();
        xml.setXmlStandalone(true);
        Element document = xml.createElement(HOS_COMPLETE_MULTIPART_UPLOAD);
        for (Integer partNum : parts.keySet()) {
            Element part = xml.createElement(HOS_PART);
            Element eTag = xml.createElement(HOS_ETAG);
            Element partNumber = xml.createElement(HOS_PART_NUM);
            eTag.setTextContent(parts.get(partNum));
            partNumber.setTextContent(String.valueOf(partNum));
            part.appendChild(eTag);
            part.appendChild(partNumber);
            document.appendChild(part);
        }
        xml.appendChild(document);
        return xml;
    }

    private KnowledgeBase saveLatestKnowledge(KnowledgeBase knowledgeBase) {
        KnowledgeBase base = new KnowledgeBase();
        BeanUtils.copyProperties(knowledgeBase, base);
        base.setUpdateTime(DateUtils.getFormatDate(new Date(), "yyyy-MM-dd'T'HH:mm:sss"));
        base.setVersion(KnowledgeConstant.LATEST);
        return base;
    }

    private Boolean updateLatestKnowledge(List<KnowledgeBase> knowledgeBaseList, KnowledgeBase knowledgeBase) {

        String name = knowledgeBase.getName();
        String type = knowledgeBase.getType();

        for (int i = 0; i < knowledgeBaseList.size(); i++) {

            KnowledgeBase knowledge = knowledgeBaseList.get(i);
            if (StringUtil.equals(knowledge.getName(), name) && StringUtil.equals(knowledge.getType(), type)
                    && StringUtil.equals(knowledge.getVersion(), KnowledgeConstant.LATEST)) {
                KnowledgeBase base = new KnowledgeBase();
                BeanUtils.copyProperties(knowledge, base);
                base.setId(knowledgeBase.getId());
                base.setPath(knowledgeBase.getPath());
                base.setSize(knowledgeBase.getSize());
                base.setSha256(knowledgeBase.getSha256());
                base.setOriginUrl(knowledgeBase.getOriginUrl());
                knowledgeBaseList.set(i, base);
                return false;
            }
        }
        return true;
    }

    public String generateVersion(String lastVersion) {

        String date = DateUtils.getCurrentDate("yyMMdd");
        if (StringUtils.isEmpty(lastVersion)) {
            return date.concat("_1");
        } else {
            String[] str = lastVersion.split("_");
            String index = str[1];
            if (StringUtil.equals(date, str[0])) {
                return date.concat("_").concat(String.valueOf(Integer.parseInt(index) + 1));
            } else {
                return date.concat("_1");
            }
        }

    }


}

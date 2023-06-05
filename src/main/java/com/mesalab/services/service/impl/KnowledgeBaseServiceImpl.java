package com.mesalab.services.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileMode;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.XmlUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.nacos.NacosConst;
import com.mesalab.qgw.model.api.HttpConfig;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.mesalab.services.common.entity.KnowledgeBase;
import com.mesalab.services.common.entity.KnowledgeConstant;
import com.mesalab.services.configuration.HosConfig;
import com.mesalab.services.service.KnowledgeBaseService;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
import org.apache.commons.io.FileUtils;
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
            String id = null;
            if (lock.acquire(10, TimeUnit.SECONDS)) {
                List<KnowledgeBase> knowledgeBaseList = queryKnowledgeBaseList();
                String expirePath = buildKnowledgeBaseList(multipartFile, knowledge, knowledgeBaseList);
                saveKnowledgeBaseList(knowledgeBaseList);
                id = knowledge.getId();
                if (StringUtil.isNotEmpty(expirePath)) {
                    Header header = new BasicHeader(KnowledgeConstant.TOKEN, hosConfig.getToken());
                    httpClientService.httpDelete(expirePath, httpConfig.getServerResponseTimeOut(), header);
                }
            }
            Map<String, Object> map = Maps.newHashMap();
            map.put("id", id);
            return BaseResultGenerator.success(map);
        } catch (BusinessException e) {
            log.error("publish knowledge error: {}", e.getMessage());
            throw new BusinessException(e.getStatus(), e.getCode(), e.getMessage());
        } finally {
            lock.release();
        }
    }

    @Override
    public BaseResult updateKnowledge(MultipartFile file, KnowledgeBase knowledgeBase) throws Exception {
        InterProcessMutex lock = new InterProcessMutex(client, ZK_LOCK_PATH);
        try {
            // 尝试获取锁，最长等待10s，超时放弃获取
            if (lock.acquire(10, TimeUnit.SECONDS)) {
                List<KnowledgeBase> knowledgeBaseList = queryKnowledgeBaseList();
                String expirePath = null;
                String newPath = null;
                String fileSha256 = DigestUtil.sha256Hex(file.getBytes());
                String currentDate = DateUtils.getCurrentDate("yyyy-MM-dd'T'HH:mm:ssZ");
                for (KnowledgeBase knowledgeBaseRep : knowledgeBaseList) {
                    if (StringUtil.equals(knowledgeBase.getId(), knowledgeBaseRep.getId())) {
                        if (StringUtil.isBlank(newPath)) {
                            newPath = uploadFile(file);
                            expirePath = knowledgeBaseRep.getPath();
                        }
                        if (StringUtil.isNotBlank(knowledgeBase.getName())) {
                            knowledgeBaseRep.setName(knowledgeBase.getName());
                        }
                        if (StringUtil.isNotBlank(knowledgeBase.getFormat())) {
                            knowledgeBaseRep.setFormat(knowledgeBase.getFormat());
                        }
                        if (StringUtil.isNotBlank(knowledgeBase.getType())) {
                            knowledgeBaseRep.setType(knowledgeBase.getType());
                        }
                        if (StringUtil.isNotEmpty(knowledgeBase.getIsValid())) {
                            knowledgeBaseRep.setIsValid(knowledgeBase.getIsValid());
                        }
                        if (StringUtil.isNotBlank(knowledgeBase.getOriginUrl())) {
                            knowledgeBaseRep.setOriginUrl(knowledgeBase.getOriginUrl());
                        }
                        knowledgeBaseRep.setPath(newPath);
                        knowledgeBaseRep.setSha256(fileSha256);
                        knowledgeBaseRep.setSize(file.getSize());
                        knowledgeBaseRep.setUpdateTime(currentDate);

                    }
                }
                if (StringUtil.isNotBlank(newPath)) {
                    saveKnowledgeBaseList(knowledgeBaseList);
                    Header header = new BasicHeader(KnowledgeConstant.TOKEN, hosConfig.getToken());
                    httpClientService.httpDelete(expirePath, httpConfig.getServerResponseTimeOut(), header);
                }
            }
        } catch (RuntimeException e) {
            log.error("update knowledge error: {}", e.getMessage());
            throw new BusinessException(e.getMessage());
        } finally {
            lock.release();
        }
        Map<String, Object> map = Maps.newHashMap();
        map.put("id", knowledgeBase.getId());
        return BaseResultGenerator.success(map);
    }

    @Override
    public BaseResult deleteKnowledge(String id) throws Exception {
        KnowledgeBase knowledgeBase = null;
        InterProcessMutex lock = new InterProcessMutex(client, ZK_LOCK_PATH);
        try {
            if (lock.acquire(10, TimeUnit.SECONDS)) {
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

    @Override
    public BaseResult toggleKnowledge(KnowledgeBase knowledgeBase) throws Exception {
        InterProcessMutex lock = new InterProcessMutex(client, ZK_LOCK_PATH);
        try {
            if (lock.acquire(10, TimeUnit.SECONDS)) {
                List<KnowledgeBase> knowledgeBaseList = queryKnowledgeBaseList();
                for (int i = 0; i < knowledgeBaseList.size(); i++) {
                    KnowledgeBase knowledgeBaseRep = knowledgeBaseList.get(i);
                    if (StringUtil.equals(knowledgeBase.getId(), knowledgeBaseRep.getId())) {
                        knowledgeBaseRep.setIsValid(knowledgeBase.getIsValid());
                        knowledgeBaseList.set(i,knowledgeBaseRep);
                    }
                }
                saveKnowledgeBaseList(knowledgeBaseList);
            }
        } catch (RuntimeException e) {
            log.error("toggle knowledge error: {}", e.getMessage());
            throw new BusinessException(e.getMessage());
        } finally {
            lock.release();
        }

        Map<String, String> map = Maps.newHashMap();
        map.put("id", knowledgeBase.getId());
        return BaseResultGenerator.success(map);
    }

    public List<KnowledgeBase> queryKnowledgeBaseList() {
        List<KnowledgeBase> knowledgeBaseList = new ArrayList<>();
        try {
            String config = pubConfigService.getConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, httpConfig.getServerRequestTimeOut());
            if (StringUtil.isNotEmpty(config)) {
                knowledgeBaseList = JSON.parseObject(config, List.class, KnowledgeBase.class);
                knowledgeBaseList = knowledgeBaseList.stream().sorted(Comparator.comparing(KnowledgeBase::getUpdateTime).reversed()).collect(Collectors.toList());
            }
        } catch (NacosException e) {
            log.error("get knowledge meta error: {}", e.getMessage());
            throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),e.getMessage());
        }
        return knowledgeBaseList;
    }


    private String buildKnowledgeBaseList(MultipartFile file, KnowledgeBase knowledge, List<KnowledgeBase> knowledgeBaseList) throws Exception {
        if (StringUtil.isEmpty(knowledge.getIsValid())) {
            knowledge.setIsValid(KnowledgeConstant.VALID);
        }
        String newPath = uploadFile(file);
        knowledge.setPath(newPath);
        knowledge.setSha256(DigestUtil.sha256Hex(file.getBytes()));
        knowledge.setSize(file.getSize());
        knowledge.setUpdateTime(DateUtils.getCurrentDate("yyyy-MM-dd'T'HH:mm:ssZ"));

        if (StringUtils.isNotBlank(knowledge.getId()) && knowledgeBaseList.stream().anyMatch(o -> StringUtil.equals(o.getId(), knowledge.getId()))) {
            KnowledgeBase knowledgeRep = new KnowledgeBase();
            String expirePath = null;
            for (KnowledgeBase item : knowledgeBaseList) {
                if (StringUtil.equals(knowledge.getId(), item.getId())) {
                    expirePath = item.getPath();
                    BeanUtils.copyProperties(item, knowledgeRep);
                    break;
                }
            }
            for (KnowledgeBase item : knowledgeBaseList) {
                if (StringUtil.equals(knowledge.getId(), item.getId())) {
                    knowledge.setVersion(item.getVersion());
                    if (StringUtil.equals(item.getVersion(), KnowledgeConstant.LATEST)) {
                        BeanUtils.copyProperties(knowledge, item);
                        item.setVersion(KnowledgeConstant.LATEST);
                    } else {
                        BeanUtils.copyProperties(knowledge, item);
                    }
                }
                if (StringUtil.equals(item.getName(), knowledgeRep.getName()) && StringUtil.equals(item.getType(), knowledgeRep.getType()) && StringUtil.equals(item.getVersion(), KnowledgeConstant.LATEST)) {
                    BeanUtils.copyProperties(knowledge, item);
                    item.setVersion(KnowledgeConstant.LATEST);
                }
            }
            return expirePath;
        } else {
            String lastVersion = queryLastKnowledgeVersion(knowledgeBaseList, knowledge);
            String version = generateVersion(lastVersion);
            if (StringUtil.isBlank(knowledge.getId())) {
                knowledge.setId(DigestUtil.md5Hex16(knowledge.getType()).concat("-").concat(DigestUtil.md5Hex16(knowledge.getName().concat(knowledge.getFormat()).concat(version))));
            }
            knowledge.setVersion(version);
            knowledgeBaseList.add(knowledge);
            addKnowledgeLatest(knowledge, knowledgeBaseList);
            return null;
        }
    }

    private void addKnowledgeLatest(KnowledgeBase knowledge, List<KnowledgeBase> knowledgeBaseList) {
        boolean hadLatestRep = knowledgeBaseList.stream().anyMatch(o -> StringUtil.equals(o.getName(), knowledge.getName())
                && StringUtil.equals(o.getType(), knowledge.getType())
                && StringUtil.equals(o.getVersion(), KnowledgeConstant.LATEST));
        if (hadLatestRep) {
            for (KnowledgeBase item : knowledgeBaseList) {
                if (!StringUtil.equals(item.getName(), knowledge.getName())
                        || !StringUtil.equals(item.getType(), knowledge.getType())
                        || !StringUtil.equals(item.getVersion(), KnowledgeConstant.LATEST)) {
                    continue;
                }
                BeanUtils.copyProperties(knowledge, item);
                item.setVersion(KnowledgeConstant.LATEST);
            }
        } else {
            KnowledgeBase knowledgeLatest = new KnowledgeBase();
            BeanUtils.copyProperties(knowledge, knowledgeLatest);
            knowledgeLatest.setVersion(KnowledgeConstant.LATEST);
            knowledgeBaseList.add(knowledgeLatest);
        }
    }

    private String queryLastKnowledgeVersion(List<KnowledgeBase> knowledgeBaseList, KnowledgeBase knowledge) {
        if (CollectionUtil.isEmpty(knowledgeBaseList)) {
            return null;
        }
        knowledgeBaseList = knowledgeBaseList.stream().sorted(Comparator.comparing(KnowledgeBase::getName).thenComparingInt(o -> Integer.parseInt(o.getVersion().replace("_", "").replace(KnowledgeConstant.LATEST, "0"))).reversed()).collect(Collectors.toList());
        for (KnowledgeBase knowledgeBase : knowledgeBaseList) {
            if (!StringUtil.equals(knowledgeBase.getVersion(), KnowledgeConstant.LATEST)
                    && StringUtil.equals(knowledgeBase.getType(), knowledge.getType())
                    && StringUtil.equals(knowledgeBase.getName(), knowledge.getName())) {
                return knowledgeBase.getVersion();
            }
        }
        return null;
    }

    public static void main(String[] args) {
        String e = "ee".replace("e", "3").replace("3", "4");
        System.out.println(e);
    }

    private void saveKnowledgeBaseList(List<KnowledgeBase> knowledgeBaseList) throws BusinessException, InterruptedException {

        try {
            boolean b = pubConfigService.publishConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, JSON.toJSONString(knowledgeBaseList));
            if (!b) {
                throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),"update knowledge meta error.");
            }
        } catch (RuntimeException | NacosException e) {
            log.error("update knowledge meta error: {}", e.getMessage());
            throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),e.getMessage());
        }
        while (true) {
            Thread.sleep(100);
            String local = JSON.toJSONString(knowledgeBaseList.stream().sorted(Comparator.comparing(KnowledgeBase::getUpdateTime).reversed()).collect(Collectors.toList()));
            String nacos = JSON.toJSONString(queryKnowledgeBaseList());
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
                    throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),"upload file failed.");
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
                        byte[] waitUploadBytes = new byte[hosConfig.getMultiUploadPartSize() <= (totalLength - hadReadLength) ? hosConfig.getMultiUploadPartSize().intValue() : (int) (totalLength - hadReadLength)];
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
                    throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),ex.getMessage());
                } finally {
                    if (fileTemp != null && fileTemp.exists()) {
                        FileUtil.del(fileTemp);
                    }
                }
            }
        } catch (IOException e) {
            throw new BusinessException(ResultStatusEnum.SERVER_ERROR.getCode(),ResultCodeEnum.UNKNOWN_EXCEPTION.getCode(),e.getMessage());
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
                base.setUpdateTime(knowledgeBase.getUpdateTime());
                base.setIsValid(knowledgeBase.getIsValid());
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

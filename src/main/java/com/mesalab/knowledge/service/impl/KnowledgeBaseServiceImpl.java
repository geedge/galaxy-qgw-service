package com.mesalab.knowledge.service.impl;

import cn.hutool.core.io.IoUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.nacos.NacosConst;
import com.mesalab.common.utils.IPUtil;
import com.mesalab.knowledge.common.config.HosConfig;
import com.mesalab.knowledge.entity.*;
import com.mesalab.network.common.Constants;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;


@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final Log log = LogFactory.get();

    @Autowired
    private ConfigService pubConfigService;

    @Autowired
    private HosConfig hosConfig;

    @Autowired
    private HttpClientService httpClientService;

    static Map<String, String> updateTime = new HashMap<>();

    @Override
    public BaseResult addKnowledge(MultipartFile multipartFile, KnowledgeBase knowledge) {

        List<KnowledgeBase> knowledgeBaseList = queryKnowledgeBaseList();
        try {
            knowledgeBaseList = updateOrSaveKnowledgeBase(knowledgeBaseList, knowledge, multipartFile);
            pubConfigService.publishConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, JsonMapper.toJsonString(knowledgeBaseList));
            Map map = new HashMap();
            map.put("id", knowledge.getId());
            return BaseResultGenerator.success(map);

        } catch (NacosException | IOException e) {
            log.error(e);
            throw new BusinessException(e.getMessage());
        }

    }


    @Override
    public BaseResult knowledgeQuery() {
        return BaseResultGenerator.success(queryKnowledgeBaseList());
    }

    @Override
    public BaseResult updateKnowledge(MultipartFile file, KnowledgeBase knowledgeBase) {

        List<KnowledgeBase> knowledgeBaseList = queryKnowledgeBaseList();
        String id = knowledgeBase.getId();
        try {
            String sha256Hex = DigestUtil.sha256Hex(file.getBytes());
            boolean isExist = false;
            for (int i = 0; i < knowledgeBaseList.size(); i++) {
                KnowledgeBase base = knowledgeBaseList.get(i);
                if (StringUtil.equals(id, base.getId())) {
                    isExist = true;
                    base = setKnowledgeBase(file, sha256Hex, base);
                    knowledgeBaseList.set(i, base);
                }
            }

            if (!isExist) {
                throw new BusinessException("id does not exist");
            }

            pubConfigService.publishConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, JsonMapper.toJsonString(knowledgeBaseList));
            Map map = new HashMap();
            map.put("id", knowledgeBase.getId());
            return BaseResultGenerator.success(map);
        } catch (NacosException | IOException e) {
            log.error(e);
            throw new BusinessException(e.getMessage());
        }
    }

    @Override
    public BaseResult deleteKnowledge(String id) {

        List<KnowledgeBase> knowledgeBaseList = queryKnowledgeBaseList();
        boolean isExist = false;
        for (int i = 0; i < knowledgeBaseList.size(); i++) {
            KnowledgeBase knowledgeBase = knowledgeBaseList.get(i);
            if (StringUtil.equals(id, knowledgeBase.getId()) && !StringUtil.equals(knowledgeBase.getVersion(), "latest")) {
                isExist = true;
                String path = knowledgeBase.getPath();
                Header header = new BasicHeader("token", hosConfig.getToken());
                httpClientService.httpDelete(path, 3000, header);
                knowledgeBaseList.remove(i);
            }
        }
        if (!isExist) {
            throw new BusinessException("id does not exist");
        }
        try {
            pubConfigService.publishConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, JsonMapper.toJsonString(knowledgeBaseList));
        } catch (NacosException e) {
            log.error(e);
            throw new BusinessException(e.getMessage());
        }
        Map map = new HashMap();
        map.put("id", id);
        return BaseResultGenerator.success(map);
    }

    public List<KnowledgeBase> queryKnowledgeBaseList() {

        List<KnowledgeBase> knowledgeBaseList = new ArrayList<>();
        try {
            String config = pubConfigService.getConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, 3000);
            if (StringUtil.isNotEmpty(config)) {
                knowledgeBaseList = com.mesalab.common.utils.JsonMapper.fromJsonString(config, List.class, KnowledgeBase.class);
            }
        } catch (NacosException e) {
            log.error("获取knowledge_base.json失败:{}", e);
            throw new BusinessException(e.getMessage());
        }
        return knowledgeBaseList;
    }


    private KnowledgeBase setKnowledgeBase(MultipartFile file, String sha256Hex, KnowledgeBase knowledge) {

        String id = knowledge.getId();
        String date = DateUtils.getFormatDate(new Date(), "yyyy-MM-dd'T'HH:mm:sss");
        String path = null;
        if (StringUtil.isEmpty(path)) {
            path = uploadFile(file, id);
        }
        knowledge.setId(id);
        knowledge.setPath(path);
        knowledge.setSha256(sha256Hex);
        knowledge.setSize(file.getSize());
        knowledge.setUpdateTime(date);
        return knowledge;
    }

    private List<KnowledgeBase> updateOrSaveKnowledgeBase(List<KnowledgeBase> knowledgeBaseList, KnowledgeBase knowledge, MultipartFile file) throws IOException {

        String id = DigestUtil.md5Hex16(knowledge.getType()).concat("-")
                .concat(DigestUtil.md5Hex16(knowledge.getName()
                        .concat(knowledge.getFormat())
                        .concat(knowledge.getVersion())));
        String sha256Hex = DigestUtil.sha256Hex(file.getBytes());
        Boolean isExist = false;
        knowledge.setId(id);
        for (int i = 0; i < knowledgeBaseList.size(); i++) {
            KnowledgeBase knowledgeBase = knowledgeBaseList.get(i);
            // type + name + format + version 一致且sha256Hex值不一致时覆盖
            if (StringUtil.equals(id, knowledgeBase.getId())) {
                isExist = true;
                if (!StringUtil.equals(sha256Hex, knowledgeBase.getSha256())) {
                    knowledge = setKnowledgeBase(file, sha256Hex, knowledgeBase);
                    updateLatestKnowledge(knowledgeBaseList, knowledge);
                }
            }
        }
        // 新增knowledgeBase
        if (!isExist) {
            setKnowledgeBase(file, sha256Hex, knowledge);
            updateLatestKnowledge(knowledgeBaseList, knowledge);
            knowledgeBaseList.add(knowledge);
        }

        return knowledgeBaseList;
    }


    public String uploadFile(MultipartFile file, String id) {
        String uri = hosConfig.getUri();
        String bucket = hosConfig.getBucket();

        Header header = new BasicHeader("token", hosConfig.getToken());
        String assessmentFile = null;
        try {
            assessmentFile = uri.concat(bucket).concat(Base64.getEncoder().encodeToString(id.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            log.error(e);
        }
        try (InputStream in = new ByteArrayInputStream(file.getBytes())) {
            String res = httpClientService.httpPutStream(assessmentFile, in, 3000, header);
            if ("-1".equals(res)) {
                throw new BusinessException("send file to hos fail ");
            }
        } catch (IOException e) {
            throw new BusinessException(e.getMessage());
        }

        return assessmentFile;

    }

    public void updateKnowledge(String content, KnowledgeVersion knowledgeVersion) {

        log.info("update knowledge Base version:{},update knowledge Base content:{}", knowledgeVersion, content);

        if (StringUtil.isNotEmpty(knowledgeVersion) && StringUtil.isNotEmpty(content)) {
            List<KnowledgeBase> KnowledgeBaseList = com.mesalab.common.utils.JsonMapper.fromJsonString(content, List.class, KnowledgeBase.class);
            Map<String, String> map = queryFilePath(knowledgeVersion, KnowledgeBaseList);
            String ipV4path = map.get(Constants.IP_BUILT_IN_V4);
            String ipV6path = map.get(Constants.IP_BUILT_IN_V6);
            String ipPrivateV4path = map.get(Constants.IP_USER_DEFINED_V4);
            String ipPrivateV6path = map.get(Constants.IP_USER_DEFINED_V6);

            String isUpdate = KnowledgeBaseServiceImpl.updateTime.get("isUpdate");

            if (StringUtil.isNotEmpty(isUpdate) && StringUtil.equals(isUpdate, "true")) {
                log.info("update ip location knowledge,ipV4path:{},ipV6path:{},ipPrivateV4path:{},ipPrivateV6path:{}",
                        ipV4path, ipV6path, ipPrivateV4path, ipPrivateV6path);
                IPUtil.initIpLookup(ipV4path, ipV6path, ipPrivateV4path, ipPrivateV6path);
                KnowledgeBaseServiceImpl.updateTime.put("isUpdate", "false");
            } else {
                log.info("No update required");
            }
        } else {
            log.info("init ip location knowledge");
            IPUtil.initIpLookup("dat" + File.separator + Constants.IP_BUILT_IN_V4, "dat" + File.separator + Constants.IP_BUILT_IN_V6, "dat" + File.separator + Constants.IP_USER_DEFINED_V4,
                    "dat" + File.separator + Constants.IP_USER_DEFINED_V6);
        }
    }

    private void downloadFile(KnowledgeBase knowledgeBase) {

        String name = knowledgeBase.getName();
        String updateTime = knowledgeBase.getUpdateTime();
        String lastUpdateTime = KnowledgeBaseServiceImpl.updateTime.get(name);
        if (!StringUtil.equals(updateTime, lastUpdateTime)) {
            Header header = new BasicHeader("token", hosConfig.getToken());
            InputStream inputStream = httpClientService.httpGetInputStream(knowledgeBase.getPath(), 3000, header);
            File file;
            try {
                file = new File("dat".concat(File.separator).concat(name));
                IoUtil.copy(inputStream, new FileOutputStream(file));
                log.info("knowledge download name :{},version:{}", name, knowledgeBase.getVersion());
            } catch (FileNotFoundException e) {
                log.error("file not fount,:{}", e);
            }
            KnowledgeBaseServiceImpl.updateTime.put(name, updateTime);
            KnowledgeBaseServiceImpl.updateTime.put("isUpdate", "true");
        }
    }

    private Map<String, String> queryFilePath(KnowledgeVersion knowledgeVersion, List<KnowledgeBase> knowledgeBaseList) {

        String ipV4BuiltIn = knowledgeVersion.getIpV4BuiltIn();
        String ipV4UserDefined = knowledgeVersion.getIpV4UserDefined();
        String ipV6BuiltIn = knowledgeVersion.getIpV6BuiltIn();
        String ipV6UserDefined = knowledgeVersion.getIpV6UserDefined();

        Map<String, String> map = new HashMap<>();
        for (KnowledgeBase knowledgeBase : knowledgeBaseList) {
            String name = knowledgeBase.getName();
            String version = knowledgeBase.getVersion();
            String concat = name.concat(":").concat(version);
            String path = "dat".concat(File.separator).concat(name);
            if (StringUtil.equals(concat, ipV4BuiltIn)) {
                map.put(Constants.IP_BUILT_IN_V4, path);
                downloadFile(knowledgeBase);
            } else if (StringUtil.equals(concat, ipV4UserDefined)) {
                map.put(Constants.IP_USER_DEFINED_V4, path);
                downloadFile(knowledgeBase);
            } else if (StringUtil.equals(concat, ipV6BuiltIn)) {
                map.put(Constants.IP_BUILT_IN_V6, path);
                downloadFile(knowledgeBase);
            } else if (StringUtil.equals(concat, ipV6UserDefined)) {
                map.put(Constants.IP_USER_DEFINED_V6, path);
                downloadFile(knowledgeBase);
            } else if (StringUtil.equals(name, ipV4BuiltIn) && StringUtil.equals(version, "latest")) {
                map.put(Constants.IP_BUILT_IN_V4, path);
                downloadFile(knowledgeBase);
            } else if (StringUtil.equals(name, ipV4UserDefined) && StringUtil.equals(version, "latest")) {
                map.put(Constants.IP_USER_DEFINED_V4, path);
                downloadFile(knowledgeBase);
            } else if (StringUtil.equals(name, ipV6BuiltIn) && StringUtil.equals(version, "latest")) {
                map.put(Constants.IP_BUILT_IN_V6, path);
                downloadFile(knowledgeBase);
            } else if (StringUtil.equals(name, ipV6UserDefined) && StringUtil.equals(version, "latest")) {
                map.put(Constants.IP_USER_DEFINED_V6, path);
                downloadFile(knowledgeBase);
            }
        }

        // 引用本地知识库文件
        String ipV4BuiltInPath = map.get(Constants.IP_BUILT_IN_V4);
        String ipV4UserDefinedPath = map.get(Constants.IP_USER_DEFINED_V4);
        String ipV6BuiltInPath = map.get(Constants.IP_BUILT_IN_V6);
        String ipV6UserDefinedPath = map.get(Constants.IP_USER_DEFINED_V6);
        if (StringUtil.isEmpty(ipV4BuiltInPath)) {
            ipV4BuiltInPath = "dat" + File.separator + Constants.IP_BUILT_IN_V4;
            map.put(Constants.IP_BUILT_IN_V4, ipV4BuiltInPath);
        }
        if (StringUtil.isEmpty(ipV4UserDefinedPath)) {
            ipV4UserDefinedPath = "dat" + File.separator + Constants.IP_BUILT_IN_V6;
            map.put(Constants.IP_USER_DEFINED_V4, ipV4UserDefinedPath);
        }
        if (StringUtil.isEmpty(ipV6BuiltInPath)) {
            ipV6BuiltInPath = "dat" + File.separator + Constants.IP_BUILT_IN_V6;
            map.put(Constants.IP_BUILT_IN_V6, ipV6BuiltInPath);
        }

        if (StringUtil.isEmpty(ipV6UserDefinedPath)) {
            ipV6UserDefinedPath = "dat" + File.separator + Constants.IP_BUILT_IN_V6;
            map.put(Constants.IP_USER_DEFINED_V6, ipV6UserDefinedPath);
        }

        return map;
    }

    private void updateLatestKnowledge(List<KnowledgeBase> knowledgeBaseList, KnowledgeBase knowledgeBase) {

        Boolean isExistLatest = false;
        String name = knowledgeBase.getName();
        String type = knowledgeBase.getType();
        // 更新latest Knowledge
        for (KnowledgeBase knowledge : knowledgeBaseList) {
            if (StringUtil.equals(knowledge.getName(), name) && StringUtil.equals(knowledge.getType(), type)
                    && StringUtil.equals(knowledge.getVersion(), "latest")) {
                knowledge.setId(knowledgeBase.getId());
                knowledge.setPath(knowledgeBase.getPath());
                knowledge.setSize(knowledgeBase.getSize());
                knowledge.setSha256(knowledgeBase.getSha256());
                knowledge.setUpdateTime(DateUtils.getFormatDate(new Date(), "yyyy-MM-dd'T'HH:mm:sss"));
                knowledge.setOrigin_url(knowledgeBase.getOrigin_url());
                isExistLatest = true;
            }
        }
        // 不存在则新增
        if (!isExistLatest) {
            KnowledgeBase base = new KnowledgeBase();
            base.setId(knowledgeBase.getId());
            base.setName(name);
            base.setType(type);
            base.setPath(knowledgeBase.getPath());
            base.setFormat(knowledgeBase.getFormat());
            base.setSize(knowledgeBase.getSize());
            base.setSha256(knowledgeBase.getSha256());
            base.setUpdateTime(DateUtils.getFormatDate(new Date(), "yyyy-MM-dd'T'HH:mm:sss"));
            base.setVersion("latest");
            base.setOrigin_url(knowledgeBase.getOrigin_url());
            knowledgeBaseList.add(base);
        }
    }
}

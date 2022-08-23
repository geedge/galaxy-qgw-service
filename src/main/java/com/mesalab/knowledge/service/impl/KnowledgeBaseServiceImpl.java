package com.mesalab.knowledge.service.impl;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.nacos.NacosConst;
import com.mesalab.knowledge.common.config.HosConfig;
import com.mesalab.knowledge.common.utils.Constant;
import com.mesalab.knowledge.entity.*;
import com.mesalab.network.common.Constants;
import com.mesalab.qgw.model.api.HttpConfig;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.JsonMapper;
import com.zdjizhi.utils.StringUtil;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;
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

    List<KnowledgeBase> knowledgeBaseList = new ArrayList<>();

    Map<String, String> updateMap = new HashMap<>();


    @Override
    public BaseResult queryKnowledge() {
        List<KnowledgeBase> list = knowledgeBaseList.stream().sorted(Comparator.comparing(KnowledgeBase::getUpdateTime).reversed()).collect(Collectors.toList());
        return BaseResultGenerator.success(list);
    }


    @Override
    public BaseResult publishKnowledge(MultipartFile multipartFile, KnowledgeBase knowledge) {

        try {
            saveOrUpdateKnowledgeBase(knowledge, multipartFile, "save");
            Map map = new HashMap();
            map.put("id", knowledge.getId());
            return BaseResultGenerator.success(map);
        } catch (RuntimeException | NacosException | IOException e) {
            log.error(e);
            throw new BusinessException(e.getMessage());
        }

    }

    @Override
    public BaseResult updateKnowledge(MultipartFile file, KnowledgeBase knowledgeBase) {

        try {
            queryKnowledgeBaseList();
            String id = knowledgeBase.getId();
            for (int i = 0; i < knowledgeBaseList.size(); i++) {
                KnowledgeBase base = knowledgeBaseList.get(i);
                if (StringUtil.equals(id, base.getId()) && !StringUtil.equals(base.getVersion(), Constant.LATEST)) {
                    saveOrUpdateKnowledgeBase(base, file, "update");
                }
            }
            Map map = new HashMap();
            map.put("id", knowledgeBase.getId());
            return BaseResultGenerator.success(map);
        } catch (RuntimeException | NacosException | IOException e) {
            log.error(e);
            throw new BusinessException(e.getMessage());
        }
    }

    @Override
    public BaseResult deleteKnowledge(String id) {

        queryKnowledgeBaseList();
        boolean isExist = false;
        KnowledgeBase knowledgeBase = null;
        int i = 0;
        for (; i < knowledgeBaseList.size(); i++) {
            knowledgeBase = knowledgeBaseList.get(i);
            if (StringUtil.equals(id, knowledgeBase.getId()) && !StringUtil.equals(knowledgeBase.getVersion(), Constant.LATEST)) {
                isExist = true;
                knowledgeBaseList.remove(i);
                break;
            }
        }
        if (isExist) {
            String path = knowledgeBase.getPath();
            Header header = new BasicHeader(Constant.TOKEN, hosConfig.getToken());
            String res = httpClientService.httpDelete(path, httpConfig.getServerResponseTimeOut(), header);
            if ("-1".equals(res)) {
                throw new BusinessException("delete Knowledge error");
            }
        } else {
            throw new BusinessException("id not exist");
        }
        try {
            pubConfigService.publishConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, JsonMapper.toJsonString(knowledgeBaseList));
        } catch (NacosException e) {
            knowledgeBaseList.add(knowledgeBase);
            log.error(e);
            throw new BusinessException(e.getMessage());
        }

        Map map = new HashMap();
        map.put("id", id);
        return BaseResultGenerator.success(map);
    }

    public void queryKnowledgeBaseList() {

        try {
            if (ObjectUtil.isEmpty(knowledgeBaseList)) {
                String config = pubConfigService.getConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, httpConfig.getServerRequestTimeOut());
                if (StringUtil.isNotEmpty(config)) {
                    knowledgeBaseList = com.mesalab.common.utils.JsonMapper.fromJsonString(config, List.class, KnowledgeBase.class);
                }
            }
        } catch (NacosException e) {
            log.error("获取knowledge_base.json失败:{}", e);
            throw new BusinessException(e.getMessage());
        }
    }


    private KnowledgeBase setKnowledgeBase(MultipartFile file, String sha256Hex, KnowledgeBase knowledge) {

        String id = knowledge.getId();
        String path = uploadFile(file, id);
        String date = DateUtils.getFormatDate(new Date(), "yyyy-MM-dd'T'HH:mm:sss");

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        BeanUtils.copyProperties(knowledge, knowledgeBase);
        knowledgeBase.setId(id);
        knowledgeBase.setPath(path);
        knowledgeBase.setSha256(sha256Hex);
        knowledgeBase.setSize(file.getSize());
        knowledgeBase.setUpdateTime(date);
        return knowledgeBase;
    }

    private synchronized void saveOrUpdateKnowledgeBase(KnowledgeBase knowledge, MultipartFile file, String type) throws IOException, NacosException {

        queryKnowledgeBaseList();

        List<KnowledgeBase> list = new ArrayList<>(knowledgeBaseList);
        Collections.copy(knowledgeBaseList, list);

        String id = DigestUtil.md5Hex16(knowledge.getType())
                .concat("-")
                .concat(DigestUtil.md5Hex16(knowledge.getName()
                        .concat(knowledge.getFormat())
                        .concat(knowledge.getVersion())));

        String sha256Hex = DigestUtil.sha256Hex(file.getBytes());
        Boolean isCreateKnowledge = true;
        knowledge.setId(id);
        for (int i = 0; i < knowledgeBaseList.size(); i++) {
            KnowledgeBase knowledgeBase = knowledgeBaseList.get(i);
            // type + name + format + version 一致且sha256Hex值不一致时覆盖
            if (StringUtil.equals(id, knowledgeBase.getId())) {
                isCreateKnowledge = false;
                if (!StringUtil.equals(sha256Hex, knowledgeBase.getSha256())) {
                    KnowledgeBase base = setKnowledgeBase(file, sha256Hex, knowledgeBase);
                    updateLatestKnowledge(knowledgeBaseList, base);
                    knowledgeBaseList.set(i, base);
                }
            }
        }
        if (isCreateKnowledge && StringUtil.equals(type, "save")) {
            KnowledgeBase knowledgeBase = setKnowledgeBase(file, sha256Hex, knowledge);
            Boolean isCreateLatestKnowledge = updateLatestKnowledge(knowledgeBaseList, knowledgeBase);
            if (isCreateLatestKnowledge) {
                KnowledgeBase latestKnowledge = saveLatestKnowledge(knowledgeBase);
                knowledgeBaseList.add(latestKnowledge);
            }
            knowledgeBaseList.add(knowledgeBase);
        }
        try {
            pubConfigService.publishConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, JsonMapper.toJsonString(knowledgeBaseList));
        } catch (RuntimeException | NacosException e) {
            knowledgeBaseList = new ArrayList<>(list);
            log.error(e);
            throw e;
        }
    }


    public String uploadFile(MultipartFile file, String id) {
        String uri = hosConfig.getUri();
        String bucket = hosConfig.getBucket();

        Header header = new BasicHeader(Constant.TOKEN, hosConfig.getToken());
        String assessmentFile = null;
        try {
            assessmentFile = uri.concat("/").concat(bucket).concat("/").concat(Base64.getEncoder().encodeToString(id.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            log.error(e);
        }
        try (InputStream in = new ByteArrayInputStream(file.getBytes())) {
            String res = httpClientService.httpPutStream(assessmentFile, in, httpConfig.getServerResponseTimeOut(), header);
            if ("-1".equals(res)) {
                throw new BusinessException("send file to hos fail ");
            }
        } catch (IOException e) {
            throw new BusinessException(e.getMessage());
        }

        return assessmentFile;

    }

    public Map<String, String> queryLoadKnowledgeInfo(String content, KnowledgeVersion knowledgeVersion) {

        log.info("update knowledge Base version:{},update knowledge Base content:{}", knowledgeVersion, content);

        if (StringUtil.isNotEmpty(knowledgeVersion) && StringUtil.isNotEmpty(content)) {
            List<KnowledgeBase> KnowledgeBaseList = com.mesalab.common.utils.JsonMapper.fromJsonString(content, List.class, KnowledgeBase.class);
            Map<String, String> map = queryFilePath(knowledgeVersion, KnowledgeBaseList);
            return map;
        }
        return null;
    }

    private void download(KnowledgeBase knowledgeBase) {

        String name = knowledgeBase.getName();
        String updateTime = knowledgeBase.getUpdateTime();
        String lastUpdateTime = updateMap.get(name);
        if (!StringUtil.equals(updateTime, lastUpdateTime)) {
            Header header = new BasicHeader(Constant.TOKEN, hosConfig.getToken());
            File file;
            FileOutputStream outputStream = null;
            InputStream inputStream = null;
            try {
                inputStream = httpClientService.httpGetInputStream(knowledgeBase.getPath(), httpConfig.getServerResponseTimeOut(), header);
                file = new File(Constant.DAT.concat(File.separator).concat(name));
                outputStream = new FileOutputStream(file);
                IoUtil.copy(inputStream, outputStream);
                log.info("knowledge download name :{},version:{}", name, knowledgeBase.getVersion());
            } catch (IOException e) {
                log.error("file not fount,:{}", e);
            } finally {
                IoUtil.close(inputStream);
                IoUtil.close(outputStream);
            }
            updateMap.put(name, updateTime);
            updateMap.put("isUpdate", "true");
        }
    }

    private Map<String, String> queryFilePath(KnowledgeVersion knowledgeVersion, List<KnowledgeBase> knowledgeBaseList) {

        String ipV4BuiltIn = knowledgeVersion.getIpV4BuiltIn();
        String ipV4UserDefined = knowledgeVersion.getIpV4UserDefined();
        String ipV6BuiltIn = knowledgeVersion.getIpV6BuiltIn();
        String ipV6UserDefined = knowledgeVersion.getIpV6UserDefined();

        updateMap.put("isUpdate", "false");
        Map<String, String> map = new HashMap<>();
        for (KnowledgeBase knowledgeBase : knowledgeBaseList) {
            String name = knowledgeBase.getName();
            String version = knowledgeBase.getVersion();
            String concat = name.concat(":").concat(version);
            String path = Constant.DAT.concat(File.separator).concat(name);
            if (StringUtil.equals(concat, ipV4BuiltIn)) {
                map.put(Constants.IP_BUILT_IN_V4, path);
                download(knowledgeBase);
            } else if (StringUtil.equals(concat, ipV4UserDefined)) {
                map.put(Constants.IP_USER_DEFINED_V4, path);
                download(knowledgeBase);
            } else if (StringUtil.equals(concat, ipV6BuiltIn)) {
                map.put(Constants.IP_BUILT_IN_V6, path);
                download(knowledgeBase);
            } else if (StringUtil.equals(concat, ipV6UserDefined)) {
                map.put(Constants.IP_USER_DEFINED_V6, path);
                download(knowledgeBase);
            } else if (StringUtil.equals(name, ipV4BuiltIn) && StringUtil.equals(version, Constant.LATEST)) {
                map.put(Constants.IP_BUILT_IN_V4, path);
                download(knowledgeBase);
            } else if (StringUtil.equals(name, ipV4UserDefined) && StringUtil.equals(version, Constant.LATEST)) {
                map.put(Constants.IP_USER_DEFINED_V4, path);
                download(knowledgeBase);
            } else if (StringUtil.equals(name, ipV6BuiltIn) && StringUtil.equals(version, Constant.LATEST)) {
                map.put(Constants.IP_BUILT_IN_V6, path);
                download(knowledgeBase);
            } else if (StringUtil.equals(name, ipV6UserDefined) && StringUtil.equals(version, Constant.LATEST)) {
                map.put(Constants.IP_USER_DEFINED_V6, path);
                download(knowledgeBase);
            }
        }

        // 引用本地知识库文件
        String ipV4BuiltInPath = map.get(Constants.IP_BUILT_IN_V4);
        String ipV4UserDefinedPath = map.get(Constants.IP_USER_DEFINED_V4);
        String ipV6BuiltInPath = map.get(Constants.IP_BUILT_IN_V6);
        String ipV6UserDefinedPath = map.get(Constants.IP_USER_DEFINED_V6);
        if (StringUtil.isEmpty(ipV4BuiltInPath)) {
            ipV4BuiltInPath = Constant.DAT + File.separator + Constants.IP_BUILT_IN_V4;
            map.put(Constants.IP_BUILT_IN_V4, ipV4BuiltInPath);
        }
        if (StringUtil.isEmpty(ipV4UserDefinedPath)) {
            ipV4UserDefinedPath = Constant.DAT + File.separator + Constants.IP_BUILT_IN_V6;
            map.put(Constants.IP_USER_DEFINED_V4, ipV4UserDefinedPath);
        }
        if (StringUtil.isEmpty(ipV6BuiltInPath)) {
            ipV6BuiltInPath = Constant.DAT + File.separator + Constants.IP_BUILT_IN_V6;
            map.put(Constants.IP_BUILT_IN_V6, ipV6BuiltInPath);
        }

        if (StringUtil.isEmpty(ipV6UserDefinedPath)) {
            ipV6UserDefinedPath = Constant.DAT + File.separator + Constants.IP_BUILT_IN_V6;
            map.put(Constants.IP_USER_DEFINED_V6, ipV6UserDefinedPath);
        }
        map.put("isUpdate", updateMap.get("isUpdate"));
        return map;
    }


    private KnowledgeBase saveLatestKnowledge(KnowledgeBase knowledgeBase) {
        KnowledgeBase base = new KnowledgeBase();
        BeanUtils.copyProperties(knowledgeBase, base);
        base.setUpdateTime(DateUtils.getFormatDate(new Date(), "yyyy-MM-dd'T'HH:mm:sss"));
        base.setVersion(Constant.LATEST);
        return base;
    }

    private Boolean updateLatestKnowledge(List<KnowledgeBase> knowledgeBaseList, KnowledgeBase knowledgeBase) {

        String name = knowledgeBase.getName();
        String type = knowledgeBase.getType();
        for (int i = 0; i < knowledgeBaseList.size(); i++) {

            KnowledgeBase knowledge = knowledgeBaseList.get(i);
            if (StringUtil.equals(knowledge.getName(), name) && StringUtil.equals(knowledge.getType(), type)
                    && StringUtil.equals(knowledge.getVersion(), Constant.LATEST)) {
                KnowledgeBase base = new KnowledgeBase();
                BeanUtils.copyProperties(knowledge, base);
                base.setId(knowledgeBase.getId());
                base.setPath(knowledgeBase.getPath());
                base.setSize(knowledgeBase.getSize());
                base.setSha256(knowledgeBase.getSha256());
                base.setUpdateTime(DateUtils.getFormatDate(new Date(), "yyyy-MM-dd'T'HH:mm:sss"));
                base.setOrigin_url(knowledgeBase.getOrigin_url());
                knowledgeBaseList.set(i, base);
                return false;
            }
        }
        return true;
    }


}

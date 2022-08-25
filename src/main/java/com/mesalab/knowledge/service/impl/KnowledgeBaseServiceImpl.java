package com.mesalab.knowledge.service.impl;

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

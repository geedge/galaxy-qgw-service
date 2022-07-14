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
            String path = uploadFile(multipartFile);
            KnowledgeBase knowledgeBase = setKnowledgeBase(knowledgeBaseList, knowledge, multipartFile, path, true);
            knowledgeBaseList.add(knowledgeBase);
            pubConfigService.publishConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, JsonMapper.toJsonString(knowledgeBaseList));
        } catch (NacosException | IOException e) {
            log.error(e);
            throw new BusinessException(e.getMessage());
        }

        return BaseResultGenerator.success();
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
            boolean isExist = false;
            for (int i = 0; i < knowledgeBaseList.size(); i++) {
                KnowledgeBase base = knowledgeBaseList.get(i);
                if (StringUtil.equals(id, base.getId())) {
                    isExist = true;
                    String path = uploadFile(file);
                    if (StringUtil.isNotEmpty(knowledgeBase.getVersion())) {
                        base.setVersion(knowledgeBase.getVersion());
                    }
                    knowledgeBaseList.set(i, setKnowledgeBase(knowledgeBaseList, base, file, path, false));
                }
            }

            if (!isExist) {
                throw new BusinessException("id does not exist");
            }

            pubConfigService.publishConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, JsonMapper.toJsonString(knowledgeBaseList));
        } catch (NacosException | IOException e) {
            log.error(e);
            throw new BusinessException(e.getMessage());
        }

        return BaseResultGenerator.success();
    }

    @Override
    public BaseResult deleteKnowledge(String id) {

        List<KnowledgeBase> knowledgeBaseList = queryKnowledgeBaseList();
        boolean isExist = false;
        for (int i = 0; i < knowledgeBaseList.size(); i++) {
            KnowledgeBase knowledgeBase = knowledgeBaseList.get(i);
            if (StringUtil.equals(id, knowledgeBase.getId())) {
                isExist = true;
                String path = knowledgeBase.getPath();
                Header header = new BasicHeader("token", hosConfig.getToken());
                httpClientService.httpDelete(path, 3000, header);
                File file = new File("knowledge_base".concat(File.separator).concat(knowledgeBase.getName()));
                if (file.exists()) {
                    file.delete();
                }
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

        return BaseResultGenerator.success();
    }

    public List<KnowledgeBase> queryKnowledgeBaseList() {

        List<KnowledgeBase> knowledgeBaseList = new ArrayList<>();
        try {
            String config = pubConfigService.getConfig(NacosConst.KNOWLEDGE_BASE_DATA_ID, NacosConst.DEFAULT_GROUP, 3000);
            if (StringUtil.isNotEmpty(config)) {
                knowledgeBaseList = com.mesalab.common.utils.JsonMapper.fromJsonString(config, List.class, KnowledgeBase.class);
            }
        } catch (NacosException e) {
            log.error("获取knowledge_base_hos_bucket.json失败:{}", e);
            throw new BusinessException(e.getMessage());
        }
        return knowledgeBaseList;
    }


    private KnowledgeBase setKnowledgeBase(List<KnowledgeBase> knowledgeBaseList, KnowledgeBase knowledge, MultipartFile file, String path, boolean b) throws IOException {


        if (b) {
            String id = DigestUtil.md5Hex16(knowledge.getType()).concat("-")
                    .concat(DigestUtil.md5Hex16(knowledge.getName()
                            .concat(knowledge.getFormat())
                            .concat(knowledge.getVersion())));
            for (KnowledgeBase knowledgeBase : knowledgeBaseList) {
                if (StringUtil.equals(id, knowledgeBase.getId())) {
                    throw new BusinessException("name already exists");
                }
            }
            knowledge.setId(id);

        }

        knowledge.setPath(path);
        knowledge.setSha256(DigestUtil.sha256Hex(file.getBytes()));
        knowledge.setSize(file.getSize());
        knowledge.setUpdateTime(DateUtils.getFormatDate(new Date(), "yyyy-MM-dd'T'HH:mm:sss"));

        return knowledge;
    }


    public String uploadFile(MultipartFile file) {
        String uri = hosConfig.getUri();
        String bucket = hosConfig.getBucket();

        Header header = new BasicHeader("token", hosConfig.getToken());
        String assessmentFile = null;
        try {
            assessmentFile = uri.concat(bucket).concat(UUID.randomUUID().toString()).
                    concat(Base64.getEncoder().encodeToString(file.getOriginalFilename().getBytes("UTF-8")));
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

    public void updateKnowledge(String content) {

        if (StringUtil.isNotEmpty(content)) {
            List<KnowledgeBase> KnowledgeBaseList = com.mesalab.common.utils.JsonMapper.fromJsonString(content, List.class, KnowledgeBase.class);
            for (KnowledgeBase knowledgeBase : KnowledgeBaseList) {
                String type = knowledgeBase.getType();
                if (StringUtil.equals(type, "ip_location")) {
                    String fileName = knowledgeBase.getName().replace(knowledgeBase.getVersion(), "");
                    String updateTime = knowledgeBase.getUpdateTime();
                    String lastUpdateTime = KnowledgeBaseServiceImpl.updateTime.get(fileName);
                    if (!StringUtil.equals(updateTime, lastUpdateTime)) {
                        Header header = new BasicHeader("token", hosConfig.getToken());
                        InputStream inputStream = httpClientService.httpGetInputStream(knowledgeBase.getPath(), 3000, header);
                        File file;
                        try {
                            file = new File("knowledge_base".concat(File.separator).concat(fileName));
                            IoUtil.copy(inputStream, new FileOutputStream(file));
                        } catch (FileNotFoundException e) {
                            log.error("file not fount,:{}", e);
                        }
                        KnowledgeBaseServiceImpl.updateTime.put(fileName, updateTime);
                    }
                }
            }
            String ipV4path;
            String ipV6path;
            String ipPrivateV4path;
            String ipPrivateV6path;
            String asnV4path;
            String asnV6path;
            if (new File("knowledge_base" + File.separator + Constants.IP_V4).exists()) {
                ipV4path = "knowledge_base" + File.separator + Constants.IP_V4;
            } else {
                ipV4path = "dat" + File.separator + Constants.IP_V4;
            }
            if (new File("knowledge_base" + File.separator + Constants.IP_V6).exists()) {
                ipV6path = "knowledge_base" + File.separator + Constants.IP_V6;
            } else {
                ipV6path = "dat" + File.separator + Constants.IP_V6;
            }
            if (new File("knowledge_base" + File.separator + Constants.IP_PRIVATE_V4).exists()) {
                ipPrivateV4path = "knowledge_base" + File.separator + Constants.IP_PRIVATE_V4;
            } else {
                ipPrivateV4path = "dat" + File.separator + Constants.IP_PRIVATE_V4;
            }
            if (new File("knowledge_base" + File.separator + Constants.IP_PRIVATE_V6).exists()) {
                ipPrivateV6path = "knowledge_base" + File.separator + Constants.IP_PRIVATE_V6;
            } else {
                ipPrivateV6path = "dat" + File.separator + Constants.IP_PRIVATE_V6;
            }
            if (new File("knowledge_base" + File.separator + Constants.ASN_V4).exists()) {
                asnV4path = "knowledge_base" + File.separator + Constants.ASN_V4;
            } else {
                asnV4path = "dat" + File.separator + Constants.ASN_V4;
            }
            if (new File("knowledge_base" + File.separator + Constants.ASN_V6).exists()) {
                asnV6path = "knowledge_base" + File.separator + Constants.ASN_V6;
            } else {
                asnV6path = "dat" + File.separator + Constants.ASN_V6;
            }

            IPUtil.initIpLookup(ipV4path, ipV6path, ipPrivateV4path, ipPrivateV6path, asnV4path, asnV6path);
        } else {
            IPUtil.initIpLookup("dat" + File.separator + Constants.IP_V4, "dat" + File.separator + Constants.IP_V6, "dat" + File.separator + Constants.IP_PRIVATE_V4,
                    "dat" + File.separator + Constants.IP_PRIVATE_V6, "dat" + File.separator + Constants.ASN_V4, "dat" + File.separator + Constants.ASN_V6);
        }
    }
}

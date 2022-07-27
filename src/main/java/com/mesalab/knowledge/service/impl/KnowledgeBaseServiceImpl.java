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

    public void updateKnowledge(String versionInfo, String content) {

        log.info("update knowledge Base version:{},update knowledge Base content:{}", versionInfo, content);

        if (StringUtil.isEmpty(versionInfo) || StringUtil.isEmpty(content)) {
            log.info("init ip location knowledge");
            IPUtil.initIpLookup("dat" + File.separator + Constants.IP_V4, "dat" + File.separator + Constants.IP_V6, "dat" + File.separator + Constants.IP_PRIVATE_V4,
                    "dat" + File.separator + Constants.IP_PRIVATE_V6);
        } else {
            KnowledgeVersion knowledgeVersion = com.mesalab.common.utils.JsonMapper.fromJsonString(versionInfo, KnowledgeVersion.class);
            List<KnowledgeBase> KnowledgeBaseList = com.mesalab.common.utils.JsonMapper.fromJsonString(content, List.class, KnowledgeBase.class);

            List<KnowledgeVersion.Ip> ipLocation = knowledgeVersion.getIp_location();
            if (StringUtil.isNotEmpty(ipLocation)) {
                Map<String, String> map = queryFilePath(ipLocation, KnowledgeBaseList);
                String ipV4path = map.get(Constants.IP_V4);
                String ipV6path = map.get(Constants.IP_V6);
                String ipPrivateV4path = map.get(Constants.IP_PRIVATE_V4);
                String ipPrivateV6path = map.get(Constants.IP_PRIVATE_V6);
                if (StringUtil.isEmpty(ipV4path)) {
                    ipV4path = "dat" + File.separator + Constants.IP_V4;
                }
                if (StringUtil.isEmpty(ipV6path)) {
                    ipV6path = "dat" + File.separator + Constants.IP_V6;
                }
                if (StringUtil.isEmpty(ipPrivateV4path)) {
                    ipPrivateV4path = "dat" + File.separator + Constants.IP_PRIVATE_V4;
                }
                if (StringUtil.isEmpty(ipPrivateV6path)) {
                    ipPrivateV6path = "dat" + File.separator + Constants.IP_PRIVATE_V6;
                }
                String isUpdate = KnowledgeBaseServiceImpl.updateTime.get("isUpdate");

                if (StringUtil.isNotEmpty(isUpdate) && StringUtil.equals(isUpdate,"true")){
                    log.info("update ip location knowledge");
                    IPUtil.initIpLookup(ipV4path, ipV6path, ipPrivateV4path, ipPrivateV6path);
                    KnowledgeBaseServiceImpl.updateTime.put("isUpdate","false");
                } else {
                    log.info("No update required");
                }

            } else {
                log.info("init ip location knowledge");
                IPUtil.initIpLookup("dat" + File.separator + Constants.IP_V4, "dat" + File.separator + Constants.IP_V6, "dat" + File.separator + Constants.IP_PRIVATE_V4,
                        "dat" + File.separator + Constants.IP_PRIVATE_V6);
            }
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
                file = new File("knowledge_base".concat(File.separator).concat(knowledgeBase.getVersion()).concat("-").concat(name));
                IoUtil.copy(inputStream, new FileOutputStream(file));
            } catch (FileNotFoundException e) {
                log.error("file not fount,:{}", e);
            }
            KnowledgeBaseServiceImpl.updateTime.put(name, updateTime);
            KnowledgeBaseServiceImpl.updateTime.put("isUpdate","true");
        }
    }

    private Map<String, String> queryFilePath(List<KnowledgeVersion.Ip> ipList, List<KnowledgeBase> knowledgeBaseList) {

        Map<String, String> map = new HashMap<>();
        for (KnowledgeVersion.Ip ipLocation : ipList) {

            KnowledgeVersion.Info ipV4BuiltIn = ipLocation.getIp_v4_built_in();
            KnowledgeVersion.Info ipV4UserDefined = ipLocation.getIp_v4_user_defined();
            KnowledgeVersion.Info ipV6BuiltIn = ipLocation.getIp_v6_built_in();
            KnowledgeVersion.Info ipV6UserDefined = ipLocation.getIp_v6_user_defined();
            for (KnowledgeBase knowledgeBase : knowledgeBaseList) {
                String version = knowledgeBase.getVersion();
                String name = knowledgeBase.getName();
                String path = "knowledge_base".concat(File.separator).concat(version).concat("-").concat(name);

                if (StringUtil.equals(ipV4BuiltIn.getFile_name(), name)
                        && StringUtil.equals(ipV4BuiltIn.getVersion(), version)) {
                    map.put(Constants.IP_V4, path);
                    downloadFile(knowledgeBase);
                } else if (StringUtil.equals(ipV4UserDefined.getFile_name(), name)
                        && StringUtil.equals(ipV4UserDefined.getVersion(), version)) {
                    map.put(Constants.IP_PRIVATE_V4, path);
                    downloadFile(knowledgeBase);
                } else if (StringUtil.equals(ipV6BuiltIn.getFile_name(), name)
                        && StringUtil.equals(ipV6BuiltIn.getVersion(), version)) {
                    downloadFile(knowledgeBase);
                    map.put(Constants.IP_V6, path);
                } else if (StringUtil.equals(ipV6UserDefined.getFile_name(), name)
                        && StringUtil.equals(ipV6UserDefined.getVersion(), version)) {
                    map.put(Constants.IP_PRIVATE_V6, path);
                    downloadFile(knowledgeBase);
                }
            }
        }
        return map;
    }
}

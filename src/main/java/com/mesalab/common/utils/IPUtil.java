package com.mesalab.common.utils;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.qgw.model.api.HttpConfig;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.mesalab.services.common.entity.KnowledgeBase;
import com.mesalab.services.common.entity.KnowledgeConstant;
import com.mesalab.services.common.entity.IpLocationConfiguration;
import com.mesalab.services.configuration.HosConfig;
import com.zdjizhi.utils.IpLookupV2;
import com.zdjizhi.utils.StringUtil;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IP工具类
 *
 * @author Darnell
 */
public class IPUtil {

    private static final Log log = LogFactory.get();

    private static IpLookupV2 ipLookup;

    private static Map<String, String> updateMap = new HashMap<>();

    private static HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");
    private static HosConfig hosConfig = (HosConfig) SpringContextUtil.getBean("hosConfig");
    private static HttpConfig httpConfig = (HttpConfig) SpringContextUtil.getBean("httpConfig");

    private IPUtil() {
        initIpLookup(KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_BUILT_IN_V4_MMDB,
                KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_BUILT_IN_V6_MMDB,
                KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_USER_DEFINED_V4_MMDB,
                KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_USER_DEFINED_V6_MMDB);
    }

    private static IPUtil instance = new IPUtil();

    public static IPUtil getInstance() {
        return instance;
    }


    public void initIpLookup(String ip_v4, String ip_v6, String ip_private_v4, String ip_private_v6) {
        ipLookup = new IpLookupV2.Builder(false)
                .loadDataFileV4(ip_v4)
                .loadDataFileV6(ip_v6)
                .loadDataFilePrivateV4(ip_private_v4)
                .loadDataFilePrivateV6(ip_private_v6)
                .build();
    }

    public String getGeo(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.latLngLookup(ip);
    }

    public String getCityDetail(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.cityLookupDetail(ip);
    }

    public String getCountry(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.countryLookup(ip);
    }

    public String getISP(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.ispLookup(ip);
    }

    public String getOrganization(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.organizationLookup(ip);
    }

    public void loadIpLocation(String knowledgeMetadata, IpLocationConfiguration ipLocationConfiguration) {

        Map<String, KnowledgeBase> map = queryLoadKnowledgeInfo(knowledgeMetadata, ipLocationConfiguration);

        Boolean isUpdate = false;
        for (Map.Entry<String, KnowledgeBase> entry : map.entrySet()) {
            KnowledgeBase knowledgeBase = entry.getValue();
            String id = knowledgeBase.getId();
            String sha256 = knowledgeBase.getSha256();
            if (!StringUtil.equals(sha256, updateMap.get(id))) {
                download(entry.getValue());
                isUpdate = true;
            }
        }
        Map<String, String> pathMap = validateAndQueryPath(map);
        if (isUpdate) {
            loadKnowledge(pathMap);
        } else {
            log.info("No update required");
        }
    }

    private Map<String, String> validateAndQueryPath(Map<String, KnowledgeBase> map) {

        Map<String, String> pathMap = new HashMap<>();

        KnowledgeBase ipV4BuiltIn = map.get(KnowledgeConstant.IP_BUILT_IN_V4);
        KnowledgeBase ipV4UserDefined = map.get(KnowledgeConstant.IP_USER_DEFINED_V4);
        KnowledgeBase ipV6BuiltIn = map.get(KnowledgeConstant.IP_BUILT_IN_V6);
        KnowledgeBase ipV6UserDefined = map.get(KnowledgeConstant.IP_USER_DEFINED_V6);
        if (StringUtil.isEmpty(ipV4BuiltIn) || !new File(KnowledgeConstant.DAT.concat(File.separator).
                concat(ipV4BuiltIn.getName()).concat(".").concat(ipV4BuiltIn.getFormat())).exists()) {
            log.error(KnowledgeConstant.IP_BUILT_IN_V4 + " : Configuration error");
            pathMap.put(KnowledgeConstant.IP_BUILT_IN_V4, KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_BUILT_IN_V4_MMDB);
        } else {
            pathMap.put(KnowledgeConstant.IP_BUILT_IN_V4, KnowledgeConstant.DAT.concat(File.separator).
                    concat(ipV4BuiltIn.getName()).concat(".").concat(ipV4BuiltIn.getFormat()));
        }
        if (StringUtil.isEmpty(ipV4UserDefined) || !new File(KnowledgeConstant.DAT.concat(File.separator).
                concat(ipV4UserDefined.getName()).concat(".").concat(ipV4UserDefined.getFormat())).exists()) {
            log.error(KnowledgeConstant.IP_USER_DEFINED_V4 + " : Configuration error");
            pathMap.put(KnowledgeConstant.IP_USER_DEFINED_V4, KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_USER_DEFINED_V4_MMDB);
        } else {
            pathMap.put(KnowledgeConstant.IP_USER_DEFINED_V4, KnowledgeConstant.DAT.concat(File.separator).
                    concat(ipV4UserDefined.getName()).concat(".").concat(ipV4UserDefined.getFormat()));
        }
        if (StringUtil.isEmpty(ipV6BuiltIn) || !new File(KnowledgeConstant.DAT.concat(File.separator).
                concat(ipV6BuiltIn.getName()).concat(".").concat(ipV6BuiltIn.getFormat())).exists()) {
            log.error(KnowledgeConstant.IP_BUILT_IN_V6 + " : Configuration error");
            pathMap.put(KnowledgeConstant.IP_BUILT_IN_V6, KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_BUILT_IN_V6_MMDB);
        } else {
            pathMap.put(KnowledgeConstant.IP_BUILT_IN_V6, KnowledgeConstant.DAT.concat(File.separator).
                    concat(ipV6BuiltIn.getName()).concat(".").concat(ipV6BuiltIn.getFormat()));
        }
        if (StringUtil.isEmpty(ipV6UserDefined) || !new File(KnowledgeConstant.DAT.concat(File.separator).
                concat(ipV6UserDefined.getName()).concat(".").concat(ipV6UserDefined.getFormat())).exists()) {
            log.error(KnowledgeConstant.IP_USER_DEFINED_V6 + " : Configuration error");
            pathMap.put(KnowledgeConstant.IP_USER_DEFINED_V6, KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_USER_DEFINED_V6_MMDB);
        } else {
            pathMap.put(KnowledgeConstant.IP_USER_DEFINED_V6, KnowledgeConstant.DAT.concat(File.separator).
                    concat(ipV6UserDefined.getName()).concat(".").concat(ipV6UserDefined.getFormat()));
        }
        return pathMap;
    }


    public void loadKnowledge(Map<String, String> map) {
        if (ObjectUtil.isNotEmpty(map)) {
            String ipV4path = map.get(KnowledgeConstant.IP_BUILT_IN_V4);
            String ipV6path = map.get(KnowledgeConstant.IP_BUILT_IN_V6);
            String ipPrivateV4path = map.get(KnowledgeConstant.IP_USER_DEFINED_V4);
            String ipPrivateV6path = map.get(KnowledgeConstant.IP_USER_DEFINED_V6);
            log.info("update ip location knowledge,ipV4path:{},ipV6path:{},ipPrivateV4path:{},ipPrivateV6path:{}",
                    ipV4path, ipV6path, ipPrivateV4path, ipPrivateV6path);
            initIpLookup(ipV4path, ipV6path, ipPrivateV4path, ipPrivateV6path);
        } else {
            initIpLookup(KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_BUILT_IN_V4_MMDB,
                    KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_BUILT_IN_V6_MMDB,
                    KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_USER_DEFINED_V4_MMDB,
                    KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_USER_DEFINED_V6_MMDB);
        }
    }

    public Map<String, KnowledgeBase> queryLoadKnowledgeInfo(String knowledgeMetadata, IpLocationConfiguration ipLocationConfiguration) {

        log.info("update knowledge Base version:{},update knowledge Base content:{}", ipLocationConfiguration, knowledgeMetadata);
        Map<String, KnowledgeBase> map = new HashMap<>();

        if (StringUtil.isNotEmpty(ipLocationConfiguration) && StringUtil.isNotEmpty(knowledgeMetadata)) {
            List<KnowledgeBase> knowledgeBaseList = JsonMapper.fromJsonString(knowledgeMetadata, List.class, KnowledgeBase.class);

            String ipV4BuiltIn = ipLocationConfiguration.getIpV4BuiltIn();
            String ipV4UserDefined = ipLocationConfiguration.getIpV4UserDefined();
            String ipV6BuiltIn = ipLocationConfiguration.getIpV6BuiltIn();
            String ipV6UserDefined = ipLocationConfiguration.getIpV6UserDefined();

            for (KnowledgeBase knowledgeBase : knowledgeBaseList) {
                String name = knowledgeBase.getName();
                String version = knowledgeBase.getVersion();
                String concat = name.concat(":").concat(version);

                if (StringUtil.equals(concat, ipV4BuiltIn)
                        || (StringUtil.equals(name, ipV4BuiltIn) && StringUtil.equals(version, KnowledgeConstant.LATEST))) {
                    map.put(KnowledgeConstant.IP_BUILT_IN_V4, knowledgeBase);
                } else if (StringUtil.equals(concat, ipV4UserDefined)
                        || (StringUtil.equals(name, ipV4UserDefined) && StringUtil.equals(version, KnowledgeConstant.LATEST))) {
                    map.put(KnowledgeConstant.IP_USER_DEFINED_V4, knowledgeBase);
                } else if (StringUtil.equals(concat, ipV6BuiltIn)
                        || (StringUtil.equals(name, ipV6BuiltIn) && StringUtil.equals(version, KnowledgeConstant.LATEST))) {
                    map.put(KnowledgeConstant.IP_BUILT_IN_V6, knowledgeBase);
                } else if (StringUtil.equals(concat, ipV6UserDefined)
                        || (StringUtil.equals(name, ipV6UserDefined) && StringUtil.equals(version, KnowledgeConstant.LATEST))) {
                    map.put(KnowledgeConstant.IP_USER_DEFINED_V6, knowledgeBase);
                }
            }
        }
        return map;
    }


    private void download(KnowledgeBase knowledgeBase) {

        String id = knowledgeBase.getId();
        String name = knowledgeBase.getName();
        String sha256 = knowledgeBase.getSha256();
        String format = knowledgeBase.getFormat();
        FileOutputStream outputStream = null;
        InputStream inputStream = null;
        try {
            Header header = new BasicHeader(KnowledgeConstant.TOKEN, hosConfig.getToken());
            inputStream = httpClientService.httpGetInputStream(knowledgeBase.getPath(), httpConfig.getServerResponseTimeOut(), header);
            File file = new File(KnowledgeConstant.DAT.concat(File.separator).concat(name).concat(".").concat(format));
            outputStream = new FileOutputStream(file);
            IoUtil.copy(inputStream, outputStream);
            log.info("knowledge download name :{},version:{}", name, knowledgeBase.getVersion());
        } catch (IOException e) {
            log.error("file not fount,:{}", e);
        } finally {
            IoUtil.close(inputStream);
            IoUtil.close(outputStream);
        }
        updateMap.put(id, sha256);
    }


}

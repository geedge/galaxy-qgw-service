package com.mesalab.common.utils;

import com.mesalab.services.common.entity.KnowledgeConstant;
import com.zdjizhi.utils.IpLookupV2;
import com.zdjizhi.utils.StringUtil;

import java.io.File;

/**
 * IP工具类
 *
 * @author Darnell
 */
public class IPUtil {
    private IPUtil() {
    }

    private static IpLookupV2 ipLookup;

    public static void ipLookupBuild() {
        ipLookup = new IpLookupV2.Builder(false)
                .loadDataFileV4(KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_BUILT_IN_V4 + KnowledgeConstant.MMDB)
                .loadDataFileV6(KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_BUILT_IN_V6 + KnowledgeConstant.MMDB)
                .loadDataFilePrivateV4(KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_USER_DEFINED_V4 + KnowledgeConstant.MMDB)
                .loadDataFilePrivateV6(KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_USER_DEFINED_V6 + KnowledgeConstant.MMDB)
                .build();
    }

    public static String getGeo(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.latLngLookup(ip);
    }

    public static String getCityDetail(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.cityLookupDetail(ip);
    }

    public static String getCountry(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.countryLookup(ip);
    }

    public static String getISP(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.ispLookup(ip);
    }

    public static String getOrganization(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.organizationLookup(ip);
    }
}

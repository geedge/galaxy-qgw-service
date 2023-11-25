package com.mesalab.common.utils;

import com.mesalab.services.common.entity.KnowledgeConstant;
import com.geedgenetworks.utils.IpLookupV2;
import com.geedgenetworks.utils.StringUtil;

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

    static {
        ipLookupBuild();
    }

    public static void ipLookupBuild() {
        ipLookup = new IpLookupV2.Builder(false)
                .loadDataFile(KnowledgeConstant.DAT + File.separator + KnowledgeConstant.IP_BUILTIN + KnowledgeConstant.MMDB)
                .loadDataFilePrivate(KnowledgeConstant.DAT + KnowledgeConstant.IP_USER_DEFINED + KnowledgeConstant.MMDB)
                .loadAsnDataFile(KnowledgeConstant.DAT + KnowledgeConstant.ASN_BUILTIN + KnowledgeConstant.MMDB)
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

    public static String getASN(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.asnLookup(ip);
    }

    public static String getAsnDetail(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.asnLookupDetail(ip);
    }

    public static String getAsnOrganization(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.asnLookupOrganization(ip);
    }

    public static Object getResponseObject(String ip) {
        return StringUtil.isEmpty(ip) ? StringUtil.EMPTY : ipLookup.infoLookup(ip);
    }

}

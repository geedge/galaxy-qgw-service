package com.mesalab.common.utils;

import com.zdjizhi.utils.IpLookupV2;
import com.zdjizhi.utils.StringUtil;

/**
 * IP工具类
 *
 * @author Darnell
 */
public class IPUtil {

    private static IpLookupV2 ipLookup;

    public static void initIpLookup(String ip_v4,String ip_v6,String ip_private_v4,String ip_private_v6) {
        ipLookup = new IpLookupV2.Builder(false)
                .loadDataFileV4(ip_v4)
                .loadDataFileV6(ip_v6)
                .loadDataFilePrivateV4(ip_private_v4)
                .loadDataFilePrivateV6(ip_private_v6)
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

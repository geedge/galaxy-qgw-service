package com.mesalab.common.utils;

import com.zdjizhi.utils.IpLookupV2;

/**
 * IP工具类
 *
 * @author dazzlzy
 * @date 2018/4/1
 */
public class IPUtil {

    private static IpLookupV2 ipLookup;
    public static void initIpLookup(String ip_v4,String ip_v6,String ip_private_v4,String ip_private_v6,
                                    String asn_v4,String asn_v6) {
        ipLookup = new IpLookupV2.Builder(true)
                .loadDataFileV4(ip_v4)
                .loadDataFileV6(ip_v6)
                .loadDataFilePrivateV4(ip_private_v4)
                .loadDataFilePrivateV4(ip_private_v6)
                .loadAsnDataFileV4(asn_v4)
                .loadAsnDataFileV6(asn_v6)
                .build();
    }

    public static String getGeo(String ip) {
        return ipLookup.latLngLookup(ip);
    }

    public static String getCityDetail(String ip) {
        return ipLookup.cityLookupDetail(ip);
    }

    public static String getCountry(String ip) {
        return ipLookup.countryLookup(ip);
    }

    public static String getISP(String ip) {
        return ipLookup.ispLookup(ip);
    }

    public static String getOrganization(String ip) {
        return ipLookup.organizationLookup(ip);
    }

}

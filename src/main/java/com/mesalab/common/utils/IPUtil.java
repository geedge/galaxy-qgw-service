package com.mesalab.common.utils;

import com.zdjizhi.utils.IpLookupV2;

import javax.servlet.http.HttpServletRequest;

/**
 * IP工具类
 *
 * @author dazzlzy
 * @date 2018/4/1
 */
public class IPUtil {

    private static IpLookupV2 ipLookup =  new IpLookupV2.Builder(true).build();

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

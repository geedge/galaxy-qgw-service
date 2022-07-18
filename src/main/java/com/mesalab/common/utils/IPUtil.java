package com.mesalab.common.utils;

import com.zdjizhi.utils.IpLookupV2;
import com.zdjizhi.utils.StringUtil;

import javax.servlet.http.HttpServletRequest;

/**
 * IP工具类
 *
 * @author Darnell
 */
public class IPUtil {

    private static IpLookupV2 ipLookup =  new IpLookupV2.Builder(true).build();

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

package com.mesalab.common.utils;

import com.zdjizhi.utils.IpLookup;

import javax.servlet.http.HttpServletRequest;

/**
 * IP工具类
 *
 * @author dazzlzy
 * @date 2018/4/1
 */
public class IPUtil {

    private static final String UNKNOWN = "unknown";
    private static final String DEFAULT_SEPARATOR = ",";
    private static final int DEFAULT_IP_LENGTH = 15;
    private static IpLookup ipLookup =  new IpLookup.Builder(false).loadDataFileV4("dat/all_ip_info_v4.mmdb")
            .loadDataFileV6("dat/all_ip_info_ipv6.mmdb")
            .loadAsnDataFileV4("dat/asn_v4.mmdb")
            .loadAsnDataFileV6("dat/asn_v6.mmdb")
            .build();

    public static String getGeo(String ip) {
        return ipLookup.latLngLookup(ip);
    }

    public static String getCityDetail(String ip) {
        return ipLookup.cityLookupDetail(ip);
    }

    public static String getCountry(String ip) {
        return ipLookup.countryLookup(ip);
    }



    /**
     * 从http request中获取真实IP
     *
     * @param request HttpServletRequest
     * @return 真实IP
     */
    public static String parseIP(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            // 代理IP
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            // 网宿cdn的真实ip
            ip = request.getHeader("Cdn-Src-Ip");
        }
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            // 蓝讯cdn的真实ip
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            // 真实IP
            ip = request.getRemoteAddr();
        }
        // 如果是多级代理，那么取第一个ip为客户端ip
        if (ip != null && ip.length() > DEFAULT_IP_LENGTH && ip.contains(DEFAULT_SEPARATOR)) {
            String[] ips = ip.split(DEFAULT_SEPARATOR);
            for (String strIp : ips) {
                if (!UNKNOWN.equalsIgnoreCase(strIp)) {
                    ip = strIp;
                    break;
                }
            }
        }
        return ip;
    }

}

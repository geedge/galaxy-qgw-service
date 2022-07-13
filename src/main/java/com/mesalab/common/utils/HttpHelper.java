package com.mesalab.common.utils;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import org.apache.commons.lang.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * TODO
 *
 * @Classname HttpHelper
 * @Date 2022/3/7 2:21 下午
 * @Author wWei
 */
public class HttpHelper {

    private static final Log log = LogFactory.get();

    public static String getBodyString(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        InputStream inputStream = null;
        BufferedReader reader = null;
        try {
            if (StringUtils.equals(request.getMethod(),"POST")){
                inputStream = request.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                String line = "";
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
        } catch (IOException e) {
            log.error("IOException:{}", e.getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.error("IOException:{}", e.getMessage());
                }
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    log.error("IOException:{}", e.getMessage());
                }
            }
        }
        return sb.toString();
    }

    public static String generateCacheKey(HttpServletRequest request) {
        return DigestUtil.md5Hex(Optional.ofNullable(request.getQueryString()).orElse("")
                .concat(HttpHelper.getBodyString(request)));
    }

}

package com.mesalab.qgw.service.impl;

import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class HttpClientService {

    @Resource(name="httpClientManagerFactory")
    private CloseableHttpClient httpClient;


    public  Map<String, String> httpGet(String url) {

        Map<String, String> resultMap = Maps.newHashMap();
        // 创建GET请求对象

        CloseableHttpResponse response = null;
        try {
            HttpGet httpGet = new HttpGet(url);

            // 执行请求
            response = httpClient.execute(httpGet);
            // 获取响应实体
            HttpEntity entity = response.getEntity();
            // 获取响应信息
            resultMap.put("status", String.valueOf(response.getStatusLine().getStatusCode()));
            resultMap.put("result", EntityUtils.toString(entity, "UTF-8"));
        } catch (ClientProtocolException e) {
            log.error("协议错误: {}", e.getMessage());
            resultMap.put("status", String.valueOf(HttpStatus.SC_SERVICE_UNAVAILABLE));
            resultMap.put("message", e.getMessage());
        } catch (ParseException e) {
            log.error("解析错误: {}", e.getMessage());
            resultMap.put("status", String.valueOf(HttpStatus.SC_SERVICE_UNAVAILABLE));
            resultMap.put("message", e.getMessage());
        } catch (IOException e) {
            log.error("IO错误: {}", e.getMessage());
            resultMap.put("status", String.valueOf(HttpStatus.SC_BAD_GATEWAY));
            resultMap.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("其它错误: {}", e.getMessage());
            resultMap.put("status", String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR));
            resultMap.put("message", e.getMessage());
        } finally {
            if (null != response) {
                try {
                    EntityUtils.consumeQuietly(response.getEntity());
                    response.close();
                } catch (IOException e) {
                    log.error("释放链接错误: {}", e.getMessage());
                }
            }
        }
        return resultMap;
    }



    public  Map<String, String> httpGet(String url, Map<String, String> headers) {

        Map<String, String> resultMap = Maps.newHashMap();
        // 创建GET请求对象

        CloseableHttpResponse response = null;
        try {
            HttpGet httpGet = new HttpGet(url);

            for (String key : headers.keySet()) {
                httpGet.setHeader(key, headers.get(key));
            }

            // 执行请求
            response = httpClient.execute(httpGet);
            // 获取响应实体
            HttpEntity entity = response.getEntity();
            // 获取响应信息
            resultMap.put("status", String.valueOf(response.getStatusLine().getStatusCode()));
            resultMap.put("result", EntityUtils.toString(entity, "UTF-8"));
        } catch (ClientProtocolException e) {
            log.error("协议错误: {}", e.getMessage());
            resultMap.put("status", String.valueOf(HttpStatus.SC_SERVICE_UNAVAILABLE));
            resultMap.put("message", e.getMessage());
        } catch (ParseException e) {
            log.error("解析错误: {}", e.getMessage());
            resultMap.put("status", String.valueOf(HttpStatus.SC_SERVICE_UNAVAILABLE));
            resultMap.put("message", e.getMessage());
        } catch (IOException e) {
            log.error("IO错误: {}", e.getMessage());
            resultMap.put("status", String.valueOf(HttpStatus.SC_BAD_GATEWAY));
            resultMap.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("其它错误: {}", e.getMessage());
            resultMap.put("status", String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR));
            resultMap.put("message", e.getMessage());
        } finally {
            if (null != response) {
                try {
                    EntityUtils.consumeQuietly(response.getEntity());
                    response.close();
                } catch (IOException e) {
                    log.error("释放链接错误: {}", e.getMessage());
                }
            }
        }
        return resultMap;
    }


    public  Map<String, String> httpPost(String url, String jsonString) {

        Map<String, String> resultMap = Maps.newHashMap();
        // 创建GET请求对象
        CloseableHttpResponse response = null;
        try {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new ByteArrayEntity(jsonString.getBytes("utf-8")));
            response = httpClient.execute(httpPost);
            // 获取响应实体
            HttpEntity entity = response.getEntity();
            // 获取响应信息
            resultMap.put("status", String.valueOf(response.getStatusLine().getStatusCode()));
            resultMap.put("result", EntityUtils.toString(entity, "UTF-8"));
        } catch (ClientProtocolException e) {
            log.error("协议错误: {}", e.getMessage());
            resultMap.put("status", String.valueOf(HttpStatus.SC_SERVICE_UNAVAILABLE));
            resultMap.put("message", e.getMessage());
        } catch (ParseException e) {
            log.error("解析错误: {}", e.getMessage());
            resultMap.put("status", String.valueOf(HttpStatus.SC_SERVICE_UNAVAILABLE));
            resultMap.put("message", e.getMessage());
        } catch (IOException e) {
            log.error("IO错误: {}", e.getMessage());
            resultMap.put("status", String.valueOf(HttpStatus.SC_BAD_GATEWAY));
            resultMap.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("其它错误: {}", e.getMessage());
            resultMap.put("status", String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR));
            resultMap.put("message", e.getMessage());
        } finally {
            if (null != response) {
                try {
                    EntityUtils.consumeQuietly(response.getEntity());
                    response.close();
                } catch (IOException e) {
                    log.error("释放链接错误: {}", e.getMessage());
                }
            }
        }
        return resultMap;
    }

    public Map<String, String> getHttpPostResponseHeads(String url, Map<String, String> headers) {

        CloseableHttpResponse response = null;
        HashMap<String, String> map = Maps.newHashMap();
        try {
            HttpPost httpPost = new HttpPost(url);
            for (Object k : headers.keySet()) {
                httpPost.setHeader(k.toString(), headers.get(k).toString());
            }
            response = httpClient.execute(httpPost);
            Header[] Headers = response.getAllHeaders();
            for (Header h : Headers) {
                map.put(h.getName().toUpperCase(), h.getValue());
            }
        } catch (ClientProtocolException e) {
            log.error("协议错误: {}", e.getMessage());
        } catch (ParseException e) {
            log.error("解析错误: {}", e.getMessage());
        } catch (IOException e) {
            log.error("IO错误: {}", e.getMessage());
        } finally {
            if (null != response) {
                try {
                    EntityUtils.consumeQuietly(response.getEntity());
                    response.close();
                } catch (IOException e) {
                    log.error("释放链接错误: {}", e.getMessage());
                }
            }
        }
        return map;
    }
}

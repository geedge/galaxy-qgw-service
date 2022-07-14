package com.mesalab.qgw.service.impl;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Maps;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.qgw.model.api.HttpConfig;
import com.zdjizhi.utils.StringUtil;
import org.apache.commons.io.IOUtils;
import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

@Service
public class HttpClientService {
    private static final Log log = LogFactory.get();
    @Autowired
    private HttpConfig httpConfig;

    //全局连接池对象
    private PoolingHttpClientConnectionManager connectionManager;

    /**
     * 初始化连接池信息
     */
    @PostConstruct
    public void initConnectionManager() {
        if (connectionManager == null) {
            connectionManager = new PoolingHttpClientConnectionManager();
            // 整个连接池最大连接数
            connectionManager.setMaxTotal(httpConfig.getMaxConnectionNum());
            // 每路由最大连接数，默认值是2
            connectionManager.setDefaultMaxPerRoute(httpConfig.getMaxPerRoute());
        }
        log.info("Initializing PoolingHttpClientConnectionManager Complete");
    }

    /**
     * 获取Http客户端连接对象
     *
     * @param socketTimeOut 响应超时时间
     * @return Http客户端连接对象
     */
    public CloseableHttpClient getHttpClient(int socketTimeOut) {
        // 创建Http请求配置参数
        RequestConfig requestConfig = RequestConfig.custom()
                // 获取连接超时时间
                .setConnectionRequestTimeout(httpConfig.getServerRequestTimeOut())
                // 请求超时时间
                .setConnectTimeout(httpConfig.getServerConnectTimeOut())
                // 响应超时时间
                .setSocketTimeout(socketTimeOut)
                .build();

        /**
         * 测出超时重试机制为了防止超时不生效而设置
         *  如果直接放回false,不重试
         *  这里会根据情况进行判断是否重试
         */
        HttpRequestRetryHandler retry = (exception, executionCount, context) -> {
            if (executionCount >= 3) {// 如果已经重试了3次，就放弃
                return false;
            }
            if (exception instanceof NoHttpResponseException) {// 如果服务器丢掉了连接，那么就重试
                return true;
            }
            if (exception instanceof SSLHandshakeException) {// 不要重试SSL握手异常
                return false;
            }
            if(exception instanceof SocketTimeoutException) {
                if (exception.getMessage().contains("Read timed out")) {
                    return false;
                }
            }
            if (exception instanceof UnknownHostException) {// 目标服务器不可达
                return false;
            }
            if (exception instanceof ConnectTimeoutException) {// 连接被拒绝
                return false;
            }
            if (exception instanceof SSLException) {// ssl握手异常
                return false;
            }
            if (exception instanceof InterruptedIOException) {// 超时
                return true;
            }


            HttpClientContext clientContext = HttpClientContext.adapt(context);
            HttpRequest request = clientContext.getRequest();
            // 如果请求是幂等的，就再次尝试
            if (!(request instanceof HttpEntityEnclosingRequest)) {
                return true;
            }
            return false;
        };


        ConnectionKeepAliveStrategy myStrategy = (response, context) -> {
            HeaderElementIterator it = new BasicHeaderElementIterator
                    (response.headerIterator(HTTP.CONN_KEEP_ALIVE));
            while (it.hasNext()) {
                HeaderElement he = it.nextElement();
                String param = he.getName();
                String value = he.getValue();
                if (value != null && param.equalsIgnoreCase("timeout")) {
                    return Long.parseLong(value) * 1000;
                }
            }
            return 60 * 1000;//如果没有约定，则默认定义时长为60s
        };

        // 创建httpClient
        return HttpClients.custom()
                // 把请求相关的超时信息设置到连接客户端
                .setDefaultRequestConfig(requestConfig)
                // 把请求重试设置到连接客户端
                .setRetryHandler(retry)
                .setKeepAliveStrategy(myStrategy)
                // 配置连接池管理对象
                .setConnectionManager(connectionManager)
                .build();
    }

    /**
     * Desc: 发起http delete请求，返回status code与response body
     * @param url
     * @param socketTimeout
     * @return {@link Map< String, String>}
     * @created by wWei
     * @date 2021/1/8 3:29 下午
     */
    public Map<String, String> httpDelete(String url, int socketTimeout) {
        Map<String, String> resultMap = Maps.newHashMap();
        // 创建GET请求对象
        CloseableHttpResponse response = null;
        try {
            HttpDelete httpDelete = new HttpDelete(url);
            // 执行请求
            response = getHttpClient(socketTimeout).execute(httpDelete);
            // 获取响应实体
            HttpEntity entity = response.getEntity();
            // 获取响应信息
            resultMap.put("status", String.valueOf(response.getStatusLine().getStatusCode()));
            resultMap.put("result", EntityUtils.toString(entity, "UTF-8"));
        } catch (ClientProtocolException e) {
            log.error("协议错误: {}", e.getMessage());
            resultMap.put("status", String.valueOf(ResultStatusEnum.SERVICE_UNAVAILABLE.getCode()));
            resultMap.put("message", e.getMessage());
        } catch (ParseException e) {
            log.error("解析错误: {}", e.getMessage());
            resultMap.put("status", String.valueOf(ResultStatusEnum.SERVICE_UNAVAILABLE.getCode()));
            resultMap.put("message", e.getMessage());
        } catch (IOException e) {
            log.error("IO错误: {}", e.getMessage());
            resultMap.put("status", String.valueOf(ResultStatusEnum.BAD_GATEWAY.getCode()));
            resultMap.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("其它错误: {}", e.getMessage());
            resultMap.put("status", String.valueOf(ResultStatusEnum.SERVER_ERROR.getCode()));
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

    /**
     *  返回status code与response body
     *  @param url:请求地址
     * @param socketTimeout: 响应超时时间
     *
     **/
    public Map<String, String> httpGet(String url, int socketTimeout) {
        Map<String, String> resultMap = Maps.newHashMap();
        // 创建GET请求对象
        CloseableHttpResponse response = null;
        try {
            HttpGet httpGet = new HttpGet(url);
            // 执行请求
            response = getHttpClient(socketTimeout).execute(httpGet);
            // 获取响应实体
            HttpEntity entity = response.getEntity();
            // 获取响应信息
            resultMap.put("status", String.valueOf(response.getStatusLine().getStatusCode()));
            resultMap.put("result", EntityUtils.toString(entity, "UTF-8"));
        } catch (ClientProtocolException e) {
            log.error("ClientProtocolException:{}", e.getMessage());
            resultMap.put("status", String.valueOf(ResultStatusEnum.SERVICE_UNAVAILABLE.getCode()));
            resultMap.put("message", e.getMessage());
        } catch (ParseException e) {
            log.error("ParseException:{}", e.getMessage());
            resultMap.put("status", String.valueOf(ResultStatusEnum.SERVICE_UNAVAILABLE.getCode()));
            resultMap.put("message", e.getMessage());
        } catch (IOException e) {
            log.error("IOException:{}", e.getMessage());
            resultMap.put("status", String.valueOf(ResultStatusEnum.BAD_GATEWAY.getCode()));
            resultMap.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Exception:{}", e.getMessage());
            resultMap.put("status", String.valueOf(ResultStatusEnum.SERVER_ERROR.getCode()));
            resultMap.put("message", e.getMessage());
        } finally {
            if (null != response) {
                try {
                    EntityUtils.consumeQuietly(response.getEntity());
                    response.close();
                } catch (IOException e) {
                    log.error("CloseConnectionException:{}", e.getMessage());
                }
            }
        }
        return resultMap;
    }

    /**
     * 返回status code与response body
     * @param url:请求地址
     * @param headers:       Headers
     * @param socketTimeOut: 响应超时时间
     * @return: java.util.Map<java.lang.String, java.lang.String>
     **/
    public Map<String, String> httpGet(String url, Map<String, String> headers, int socketTimeOut) {
        Map<String, String> resultMap = Maps.newHashMap();
        // 创建GET请求对象
        CloseableHttpResponse response = null;
        try {
            HttpGet httpGet = new HttpGet(url);
            for (String key : headers.keySet()) {
                httpGet.setHeader(key, headers.get(key));
            }
            // 执行请求
            response = getHttpClient(socketTimeOut).execute(httpGet);
            // 获取响应实体
            HttpEntity entity = response.getEntity();
            // 获取响应信息
            resultMap.put("status", String.valueOf(response.getStatusLine().getStatusCode()));
            resultMap.put("result", EntityUtils.toString(entity, "UTF-8"));
        } catch (ClientProtocolException e) {
            log.error("ClientProtocolException:{}", e.getMessage());
            resultMap.put("status", String.valueOf(ResultStatusEnum.SERVICE_UNAVAILABLE.getCode()));
            resultMap.put("message", e.getMessage());
        } catch (ParseException e) {
            log.error("ParseException:{}", e.getMessage());
            resultMap.put("status", String.valueOf(ResultStatusEnum.SERVICE_UNAVAILABLE.getCode()));
            resultMap.put("message", e.getMessage());
        } catch (IOException e) {
            log.error("IOException:{}", e.getMessage());
            resultMap.put("status", String.valueOf(ResultStatusEnum.BAD_GATEWAY.getCode()));
            resultMap.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Exception:{}", e.getMessage());
            resultMap.put("status", String.valueOf(ResultStatusEnum.SERVER_ERROR.getCode()));
            resultMap.put("message", e.getMessage());
        } finally {
            if (null != response) {
                try {
                    EntityUtils.consumeQuietly(response.getEntity());
                    response.close();
                } catch (IOException e) {
                    log.error("CloseConnectionException:{}", e.getMessage());
                }
            }
        }
        return resultMap;
    }

    /**
     * 返回status code与response body
     * @param url:请求地址
     * @param jsonString:请求参数
     * @param socketTimeOut:响应超时时间
     **/
    public Map<String, String> httpPost(String url, String jsonString, int socketTimeOut) {
        Map<String, String> resultMap = Maps.newHashMap();
        // 创建GET请求对象
        CloseableHttpResponse response = null;
        try {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new ByteArrayEntity(jsonString.getBytes("utf-8")));
            response = getHttpClient(socketTimeOut).execute(httpPost);
            // 获取响应实体
            HttpEntity entity = response.getEntity();
            // 获取响应信息
            resultMap.put("status", String.valueOf(response.getStatusLine().getStatusCode()));
            resultMap.put("result", EntityUtils.toString(entity, "UTF-8"));
        } catch (ClientProtocolException e) {
            log.error("ClientProtocolException: {}", e.getMessage());
            resultMap.put("status", String.valueOf(ResultStatusEnum.SERVICE_UNAVAILABLE.getCode()));
            resultMap.put("message", e.getMessage());
        } catch (ParseException e) {
            log.error("ParseException: {}", e.getMessage());
            resultMap.put("status", String.valueOf(ResultStatusEnum.SERVICE_UNAVAILABLE.getCode()));
            resultMap.put("message", e.getMessage());
        } catch (IOException e) {
            log.error("IOException: {}", e.getMessage());
            resultMap.put("status", String.valueOf(ResultStatusEnum.BAD_GATEWAY.getCode()));
            resultMap.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            resultMap.put("status", String.valueOf(ResultStatusEnum.SERVER_ERROR.getCode()));
            resultMap.put("message", e.getMessage());
        } finally {
            if (null != response) {
                try {
                    EntityUtils.consumeQuietly(response.getEntity());
                    response.close();
                } catch (IOException e) {
                    log.error("CloseConnectionException: {}", e.getMessage());
                }
            }
        }
        return resultMap;
    }

    /**
     * 返回status code与response body
     * @param url:请求地址
     * @param headers:       Headers
     * @param socketTimeOut: 响应超时时间
     **/
    public Map<String, String> getHttpPostResponseHeads(String url, Map<String, String> headers, int socketTimeOut) {
        CloseableHttpResponse response = null;
        HashMap<String, String> map = Maps.newHashMap();
        try {
            HttpPost httpPost = new HttpPost(url);
            for (Object k : headers.keySet()) {
                httpPost.setHeader(k.toString(), headers.get(k).toString());
            }
            response = getHttpClient(socketTimeOut).execute(httpPost);
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

    /**
     * @param url:请求地址
     **/
    public String httpGet(String url) {
        String msg = "-1";
        // 获取客户端连接对象
        CloseableHttpClient httpClient = getHttpClient(httpConfig.getArangoSocketTimeOut());
        CloseableHttpResponse response = null;
        try {
            URL ul = new URL(url);
            URI uri = new URI(ul.getProtocol(), null, ul.getHost(), ul.getPort(), ul.getPath(), ul.getQuery(), null);
            log.info("http get uri {}", uri);
            // 创建GET请求对象
            HttpGet httpGet = new HttpGet(uri);
            // 执行请求
            response = httpClient.execute(httpGet);
            int statusCode = response.getStatusLine().getStatusCode();
            // 获取响应实体
            HttpEntity entity = response.getEntity();
            // 获取响应信息
            msg = EntityUtils.toString(entity, "UTF-8");
            if (statusCode != ResultStatusEnum.SUCCESS.getCode()) {
                throw new BusinessException("Http get content is :" + msg);
            }
        } catch (URISyntaxException e) {
            log.error("URI 转换错误: {}", e.getMessage());
        } catch (ClientProtocolException e) {
            log.error("协议错误: {}", e.getMessage());
        } catch (ParseException e) {
            log.error("解析错误: {}", e.getMessage());
        } catch (IOException e) {
            log.error("IO错误: {}", e.getMessage());
        } finally {
            if (null != response) {
                try {
                    EntityUtils.consume(response.getEntity());
                    response.close();
                } catch (IOException e) {
                    log.error("释放链接错误: {}", e.getMessage());
                }
            }
        }
        return msg;
    }

    /**
     * @param url:           请求地址
     * @param requestBody:   请求参数
     * @param headers:       Header
     **/
    public String httpPost(String url, String requestBody, Header... headers) {
        String msg = "-1";
        // 获取客户端连接对象
        CloseableHttpClient httpClient = getHttpClient(httpConfig.getArangoSocketTimeOut());
        // 创建POST请求对象
        CloseableHttpResponse response = null;
        try {

            URL ul = new URL(url);
            URI uri = new URI(ul.getProtocol(), null, ul.getHost(), ul.getPort(), ul.getPath(), ul.getQuery(), null);
            log.debug("http post uri:{}， http post body:{}", uri, requestBody);
            HttpPost httpPost = new HttpPost(uri);
            httpPost.setHeader("Content-Type", "application/json");
            if (StringUtil.isNotEmpty(headers)) {
                for (Header h : headers) {
                    httpPost.addHeader(h);
                }
            }
            if (StringUtil.isNotBlank(requestBody)) {
                httpPost.setEntity(new ByteArrayEntity(requestBody.getBytes("utf-8")));
            }
            response = httpClient.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();
            // 获取响应实体
            HttpEntity entity = response.getEntity();
            // 获取响应信息
            msg = EntityUtils.toString(entity, "UTF-8");
            if (statusCode != ResultStatusEnum.SUCCESS.getCode() && statusCode != ResultStatusEnum.CREATED.getCode()) {
                throw new BusinessException(msg);
            }
        } catch (URISyntaxException e) {
            log.error("URI 转换错误: {}", e.getMessage());
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
        return msg;
    }

    /**
     * PUT 请求
     * @param url
     * @param headers
     * @return
     */
    public String httpPutStream(String url, InputStream in, int socketTimeout,Header... headers ) {
        String msg = "-1";
        // 获取客户端连接对象
        CloseableHttpClient httpClient = getHttpClient(socketTimeout);
        CloseableHttpResponse response = null;
        try {

            URL ul = new URL(url);
            URI uri = new URI(ul.getProtocol(),null,  ul.getHost(),  ul.getPort(), ul.getPath(), ul.getQuery(), null);

            log.info("http post uri:{} ", url);

            HttpPut httpPut = new HttpPut(uri);
            httpPut.setHeader("Content-Type", "application/octet-stream");
            if (StringUtil.isNotEmpty(headers)) {
                for (Header h : headers) {
                    httpPut.addHeader(h);
                    log.info("request header : {}",h);
                }
            }

            httpPut.setEntity(new InputStreamEntity(in));

            response = httpClient.execute(httpPut);
            int statusCode = response.getStatusLine().getStatusCode();
            // 获取响应实体
            HttpEntity entity = response.getEntity();
            // 获取响应信息
            msg = EntityUtils.toString(entity, "UTF-8");

            if (statusCode != ResultStatusEnum.SUCCESS.getCode()) {
                throw new BusinessException(String.valueOf(statusCode), "Http put content is :" + msg);
            }
        } catch (URISyntaxException e) {
            log.error("URI 转换错误: {}", e.getMessage());
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
        return msg;
    }

    public InputStream httpGetInputStream(String url, int socketTimeout, Header... headers) {
        InputStream result = null;
        // 获取客户端连接对象
        CloseableHttpClient httpClient = getHttpClient(socketTimeout);
        // 创建GET请求对象
        HttpGet httpGet = new HttpGet(url);
        if (StringUtil.isNotEmpty(headers)) {
            for (Header h : headers) {
                httpGet.addHeader(h);
                log.info("request header : {}",h);
            }
        }
        CloseableHttpResponse response = null;

        try {
            // 执行请求
            response = httpClient.execute(httpGet);
            // 获取响应实体
            result = IOUtils.toBufferedInputStream(response.getEntity().getContent());
            // 获取响应信息
            EntityUtils.consume(response.getEntity());
        } catch (ClientProtocolException e) {
            log.error("current file: {},Protocol error:{}", url, e.getMessage());

        } catch (ParseException e) {
            log.error("current file: {}, Parser error:{}", url, e.getMessage());

        } catch (IOException e) {
            log.error("current file: {},IO error:{}", url, e.getMessage());

        } finally {
            if (null != response) {
                try {
                    EntityUtils.consume(response.getEntity());
                    response.close();
                } catch (IOException e) {
                    log.error("Release Connection error:{}", e.getMessage());

                }
            }
            return result;
        }
    }

    /**
     * 发送http delete请求
     */
    public String httpDelete(String url, int socketTimeout, Header... headers){
        String msg = "-1";
        // 获取客户端连接对象
        CloseableHttpClient httpClient = getHttpClient(socketTimeout);

        // 创建POST请求对象
        CloseableHttpResponse response = null;

        try {
            URL ul = new URL(url);

            URI uri = new URI(ul.getProtocol(),null,  ul.getHost(),  ul.getPort(), ul.getPath(), ul.getQuery(), null);

            log.info("http delete uri:{}", uri);

            HttpDelete httpdelete = new HttpDelete(uri);
            if (StringUtil.isNotEmpty(headers)) {
                for (Header h : headers) {
                    httpdelete.addHeader(h);
                    log.info("request header : {}",h);
                }
            }
            response = httpClient.execute(httpdelete);
            int statusCode = response.getStatusLine().getStatusCode();
            // 返回204，删除成功
            if (statusCode != 204) {
                throw new BusinessException(String.valueOf(statusCode), "Http post content is :" + msg);
            }

        } catch (URISyntaxException e) {
            log.error("URI 转换错误: {}", e.getMessage());
        } catch (ClientProtocolException e) {
            log.error("协议错误: {}", e.getMessage());
        } catch (ParseException e) {
            log.error("解析错误: {}", e.getMessage());

        } catch (IOException e) {
            log.error("IO错误: {}", e.getMessage());

        }finally{
            if (null != response) {
                try {
                    EntityUtils.consumeQuietly(response.getEntity());
                    response.close();
                } catch (IOException e) {
                    log.error("释放链接错误: {}", e.getMessage());

                }
            }
        }
        return msg;
    }
}

package com.mesalab.cn.common.util;

import com.mesalab.common.exception.BusinessException;
import com.zdjizhi.utils.StringUtil;
import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Objects;

public class HttpClientUtil {
    /**
     * 全局连接池对象
     */
    private static final PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();

    private static Logger logger = LoggerFactory.getLogger(HttpClientUtil.class);

    /**
     * 静态代码块配置连接池信息
     */
    static {

        // 设置最大连接数
        connManager.setMaxTotal(400);
        // 设置每个连接的路由数
        connManager.setDefaultMaxPerRoute(80);

    }

    /**
     * 获取Http客户端连接对象
     *
     * @return Http客户端连接对象
     */
    public static CloseableHttpClient getHttpClient() {
        // 创建Http请求配置参数
        RequestConfig requestConfig = RequestConfig.custom()
                // 获取连接超时时间
                .setConnectionRequestTimeout(30000)
                // 请求超时时间
                .setConnectTimeout(10000)
                // 响应超时时间
                .setSocketTimeout(10000)
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
            if (exception instanceof InterruptedIOException) {// 超时
                return true;
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
                if (value != null && param.equalsIgnoreCase
                        ("timeout")) {
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
                .setConnectionManager(connManager)
                .build();
    }


    /**
     * GET请求
     *
     * @param url 请求地
     * @return
     */
    public static String httpGet(String url, Header... headers) throws BusinessException {
        String msg = "-1";

        // 获取客户端连接对象
        CloseableHttpClient httpClient = getHttpClient();
        CloseableHttpResponse response = null;

        try {

            URL ul = new URL(url);

            URI uri = new URI(ul.getProtocol(), null, ul.getHost(), ul.getPort(), ul.getPath(), ul.getQuery(), null);
            logger.info("http get uri {}", uri);
            // 创建GET请求对象
            HttpGet httpGet = new HttpGet(uri);
            if (Objects.nonNull(headers)) {
                for (Header h : headers) {
                    httpGet.addHeader(h);
                }
            }
            // 执行请求
            response = httpClient.execute(httpGet);
            int statusCode = response.getStatusLine().getStatusCode();
            // 获取响应实体
            HttpEntity entity = response.getEntity();
            // 获取响应信息
            msg = EntityUtils.toString(entity, "UTF-8");

            if (statusCode != HttpStatus.SC_OK) {
                throw new BusinessException("Http get content is :" + msg);
            }

        } catch (URISyntaxException e) {
            logger.error("URI 转换错误: {}", e.getMessage());
            throw new BusinessException(e.getMessage());
        } catch (ClientProtocolException e) {
            logger.error("协议错误: {}", e.getMessage());
            throw new BusinessException(e.getMessage());
        } catch (ParseException e) {
            logger.error("解析错误: {}", e.getMessage());
            throw new BusinessException(e.getMessage());
        } catch (IOException e) {
            logger.error("IO错误: {}", e.getMessage());
            throw new BusinessException(e.getMessage());
        } finally {
            if (null != response) {
                try {
                    EntityUtils.consume(response.getEntity());
                    response.close();
                } catch (IOException e) {
                    logger.error("释放链接错误: {}", e.getMessage());
                    throw new BusinessException(e.getMessage());
                }
            }
        }

        return msg;
    }

    /**
     * POST 请求
     *
     * @param url
     * @param requestBody
     * @return
     */
    public static String httpPost(String url, String requestBody, Header... headers) throws BusinessException {
        String msg = "-1";
        // 获取客户端连接对象
        CloseableHttpClient httpClient = getHttpClient();
        // 创建POST请求对象
        CloseableHttpResponse response = null;
        try {

            URL ul = new URL(url);

            URI uri = new URI(ul.getProtocol(), null, ul.getHost(), ul.getPort(), ul.getPath(), ul.getQuery(), null);

            logger.info("http post url:{}， http post body:{}", uri, requestBody);

            HttpPost httpPost = new HttpPost(uri);
            httpPost.setHeader("Content-Type", "application/json");
            if (Objects.nonNull(headers)) {
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

            if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_CREATED) {
                throw new BusinessException(msg);
            }
        } catch (URISyntaxException e) {
            logger.error("URI 转换错误: {}", e.getMessage());
            throw new BusinessException(e.getMessage());
        } catch (ClientProtocolException e) {
            logger.error("协议错误: {}", e.getMessage());
            throw new BusinessException(e.getMessage());
        } catch (ParseException e) {
            logger.error("解析错误: {}", e.getMessage());
            throw new BusinessException(e.getMessage());
        } catch (IOException e) {
            logger.error("IO错误: {}", e.getMessage());
            throw new BusinessException(e.getMessage());
        } finally {
            if (null != response) {
                try {
                    EntityUtils.consumeQuietly(response.getEntity());
                    response.close();
                } catch (IOException e) {
                    logger.error("释放链接错误: {}", e.getMessage());
                    throw new BusinessException(e.getMessage());
                }
            }
        }
        return msg;
    }

}

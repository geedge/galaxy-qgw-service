package com.mesalab.common.httpClient;

import cn.hutool.core.exceptions.ExceptionUtil;
import com.geedgenetworks.utils.StringUtil;
import com.google.common.collect.Maps;
import com.mesalab.common.configuration.HttpConfig;
import com.mesalab.common.entity.HttpResponseResult;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
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
import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
@Service
@Slf4j
public class HttpClientServiceV2 {
    @Autowired
    private HttpConfig httpConfig;
    private PoolingHttpClientConnectionManager connectionManager;
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    /**
     * Init PoolingHttpClientConnectionManager, support http and https
     */
    @PostConstruct
    private void initConnectionManager() {
        try {
            // Use TrustManager to trust all certificates
            X509TrustManager trustManager = new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                @Override
                public void checkClientTrusted(X509Certificate[] xcs, String str) {
                }
                @Override
                public void checkServerTrusted(X509Certificate[] xcs, String str) {
                }
            };
            SSLContext ctx = SSLContext.getInstance(SSLConnectionSocketFactory.TLS);
            ctx.init(null, new TrustManager[]{trustManager}, null);
            SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(ctx, NoopHostnameVerifier.INSTANCE);
            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.INSTANCE)
                    .register("https", socketFactory).build();

            if (connectionManager == null) {
                connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
                connectionManager.setMaxTotal(httpConfig.getMaxConnectionNum());
                connectionManager.setDefaultMaxPerRoute(httpConfig.getMaxPerRoute());
            }
            log.info("Initializing PoolingHttpClientConnectionManager Complete");
        } catch (KeyManagementException e) {
            log.error("KeyManagementException", e);
            throw new BusinessException(e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            log.error("NoSuchAlgorithmException", e);
            throw new BusinessException(e.getMessage());
        }
    }


    /**
     * Specify the timeout time for each request and returned the corresponding CloseableHttpClient
     * @param socketTimeOut
     * @return CloseableHttpClient instance
     */
    private CloseableHttpClient getHttpClient(int socketTimeOut) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(httpConfig.getServerRequestTimeOut())
                .setConnectTimeout(httpConfig.getServerConnectTimeOut())
                .setSocketTimeout(socketTimeOut) // Setting the socket timeout time
                .build();
        //Http request for retry mechanism. if return false, no retry, otherwise retry.
        HttpRequestRetryHandler retry = (exception, executionCount, context) -> {
            if (executionCount >= 3) { // Maximum number of retries
                return false;
            }
            // If the server has not responded, retry
            if (exception instanceof NoHttpResponseException) {
                return true;
            }
            //no retry for SSLHandshakeException
            if (exception instanceof SSLHandshakeException) {
                return false;
            }
            if(exception instanceof SocketTimeoutException) {
                if (exception.getMessage().contains("Read timed out")) {
                    return false;
                }
            }
            //No retry for UnknownHostException, indicates the host is unreachable
            if (exception instanceof UnknownHostException) {
                return false;
            }
            // No retry for ConnectTimeoutException, indicates the connection is refused
            if (exception instanceof ConnectTimeoutException) {
                return false;
            }
            // No retry for SSLException, indicates the SSL handshake error
            if (exception instanceof SSLException) {
                return false;
            }
            // If the request has been sent, but the client has been interrupted, retry
            if (exception instanceof InterruptedIOException) {
                return true;
            }

            HttpClientContext clientContext = HttpClientContext.adapt(context);
            HttpRequest request = clientContext.getRequest();
            // If the request is idempotent, retry
            if (!(request instanceof HttpEntityEnclosingRequest)) {
                return true;
            }
            return false;
        };


        ConnectionKeepAliveStrategy myStrategy = (response, context) -> {
            HeaderElementIterator it = new BasicHeaderElementIterator
                    (response.headerIterator(HTTP.CONN_KEEP_ALIVE));
            while (it.hasNext()) {
                HeaderElement headerElement = it.nextElement();
                String param = headerElement.getName();
                String value = headerElement.getValue();
                if (value != null && param.equalsIgnoreCase("timeout")) {
                    return Long.parseLong(value) * 1000;
                }
            }
            return 60 * 1000; // if not specified, default keep alive is 60 seconds
        };

        //Create a CloseableHttpClient instance
        return HttpClients.custom()
                // Set the timeout time for each request
                .setDefaultRequestConfig(requestConfig)
                // Set the retry mechanism
                .setRetryHandler(retry)
                .setKeepAliveStrategy(myStrategy)
                // set the connection pool
                .setConnectionManager(connectionManager)
                .build();
    }
    public HttpResponseResult get(String url) {
        return get(url, httpConfig.getServerResponseTimeOut(), null);
    }
    public HttpResponseResult get(String url, int socketTimeout) {
        return get(url, socketTimeout, null);
    }

    public HttpResponseResult get(String url, int socketTimeout, Map<String, String> headers) {
        return executeHttpRequest(new HttpGet(url), socketTimeout, headers);
    }

    public HttpResponseResult post(String url, int socketTimeout, String requestBody, Map<String, String> headers) {
        if (StringUtil.isEmpty(headers)) {
            headers = Maps.newHashMap();
        }
        if(headers.get(CONTENT_TYPE) == null) {
            headers.put(CONTENT_TYPE, APPLICATION_JSON);
        }
        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(new ByteArrayEntity(requestBody.getBytes(StandardCharsets.UTF_8)));
        return executeHttpRequest(httpPost, socketTimeout, headers);
    }

    public HttpResponseResult post(String url, int socketTimeout, String requestBody){
        return post(url, socketTimeout, requestBody , null);
    }

    public HttpResponseResult post(String url, String requestBody, Map<String, String> headers){
        return post(url, httpConfig.getServerResponseTimeOut(), requestBody , headers);
    }

    public HttpResponseResult post(String url, String requestBody){
        return post(url, httpConfig.getServerResponseTimeOut(), requestBody , null);
    }

    public HttpResponseResult delete(String url) {
        return executeHttpRequest(new HttpDelete(url), httpConfig.getServerResponseTimeOut(), null);
    }

    public HttpResponseResult delete(String url, int socketTimeout) {
        return executeHttpRequest(new HttpDelete(url), socketTimeout, null);
    }


    public HttpResponseResult delete(String url, int socketTimeout, Map<String, String> headers){
        return executeHttpRequest(new HttpDelete(url), socketTimeout, headers);
    }


    public HttpResponseResult put(String url, InputStream in, int socketTimeout, Map<String, String> headers) {
        HttpPut httpPut = new HttpPut(url);
        if (StringUtil.isEmpty(headers)) {
            headers = Maps.newHashMap();
        }
        if(headers.get(CONTENT_TYPE) == null) {
            headers.put(CONTENT_TYPE, APPLICATION_JSON);
        }
        httpPut.setEntity(new InputStreamEntity(in));
        return executeHttpRequest(httpPut, socketTimeout, headers);
    }

    public Map<String, String> getHttpPostResponseHeader(String url, int socketTimeOut, Map<String, String> headers) {
        CloseableHttpResponse response = null;
        HashMap<String, String> map = Maps.newHashMap();
        try {
            HttpPost httpPost = new HttpPost(url);
            headers.forEach(httpPost::setHeader);
            response = getHttpClient(socketTimeOut).execute(httpPost);
            Header[] Headers = response.getAllHeaders();
            for (Header h : Headers) {
                map.put(h.getName().toUpperCase(), h.getValue());
            }
        } catch (Exception e) {
           throw new BusinessException(handleException(e), CommonErrorCode.HTTP_REQUEST_EXCEPTION.getCode(), e.getMessage());
        } finally {
            closeResponse(response);
        }
        return map;
    }

    public InputStream getInputStream(String url, int socketTimeout, Map<String, String> headers) {
        CloseableHttpResponse response = null;
        InputStream result = null;
        try {
            HttpGet httpGet = new HttpGet(url);
            if (headers != null) {
                headers.forEach(httpGet::setHeader);
            }
            response = getHttpClient(socketTimeout).execute(httpGet);
            result = IOUtils.toBufferedInputStream(response.getEntity().getContent());
        } catch (Exception e) {
            throw new BusinessException(handleException(e), CommonErrorCode.HTTP_REQUEST_EXCEPTION.getCode(), e.getMessage());
        } finally {
            closeResponse(response);
        }
        return result;
    }


    private HttpResponseResult executeHttpRequest(HttpRequestBase request, int socketTimeout, Map<String, String> headers) {
        HttpResponseResult responseResult = new HttpResponseResult();
        CloseableHttpResponse response = null;
        try {
            if (headers != null) {
                headers.forEach(request::setHeader);
            }
            response = getHttpClient(socketTimeout).execute(request);
            HttpEntity entity = response.getEntity();
            responseResult.setStatusCode(response.getStatusLine().getStatusCode());
            responseResult.setResponseBody(EntityUtils.toString(entity, StandardCharsets.UTF_8));
            if (responseResult.getStatusCode() >= 300) {
                log.error("Http request failed, url:{}, statusCode:{}, responseBody:{}",
                        request.getURI(), responseResult.getStatusCode(), responseResult.getResponseBody());
                responseResult.setErrorMessage(responseResult.getResponseBody());
            }
        } catch (Exception e) {
            responseResult.setStatusCode(handleException(e));
            responseResult.setErrorMessage(ExceptionUtil.getRootCauseMessage(e));
        } finally {
            closeResponse(response);
        }
        return responseResult;
    }

    private int handleException(Exception e) {
        if (e instanceof ClientProtocolException || e instanceof ParseException) {
            log.error("Protocol or Parsing error:{}", e.getMessage());
            return HttpStatusCodeEnum.SERVICE_UNAVAILABLE.getCode();
        } else if (e instanceof SocketTimeoutException) {
            log.error("Socket timeout error:{}", e.getMessage());
            return HttpStatusCodeEnum.GATEWAY_TIMEOUT.getCode();
        } else if (e instanceof ConnectTimeoutException) {
            log.error("Connect timeout error:{}", e.getMessage());
            return HttpStatusCodeEnum.GATEWAY_TIMEOUT.getCode();
        } else if (e instanceof UnknownHostException) {
            log.error("Unknown host error:{}", e.getMessage());
            return HttpStatusCodeEnum.BAD_GATEWAY.getCode();
        } else if (e instanceof InterruptedIOException) {
            log.error("Interrupted error:{}", e.getMessage());
            return HttpStatusCodeEnum.BAD_GATEWAY.getCode();
        } else if (e instanceof IOException) {
            log.error("IO error:{}", e.getMessage());
            return HttpStatusCodeEnum.BAD_GATEWAY.getCode();
        } else {
            log.error("Other error:{}", e.getMessage());
            return HttpStatusCodeEnum.SERVER_ERROR.getCode();
        }
    }

    private void closeResponse(CloseableHttpResponse response) {
        if (response != null) {
            try {
                EntityUtils.consumeQuietly(response.getEntity());
                response.close();
            } catch (IOException e) {
                log.error("Close Connection Exception: {}", e.getMessage());
            }
        }
    }

}

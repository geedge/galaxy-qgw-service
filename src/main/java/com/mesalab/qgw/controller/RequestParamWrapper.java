package com.mesalab.qgw.controller;



import com.google.common.collect.Lists;
import com.mesalab.common.enums.QueryParamEnum;
import com.mesalab.common.utils.HttpHelper;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class RequestParamWrapper extends HttpServletRequestWrapper {
    private String queryString = null;
    private final byte[] body;


    RequestParamWrapper(HttpServletRequest request) {
        super(request);
        queryString = request.getQueryString();
        body = HttpHelper.getBodyString(request).getBytes(StandardCharsets.UTF_8);
    }


    @Override
    public String[] getParameterValues(String name) {
        if (QueryParamEnum.QUERY.getValue().equals(name)) {
            queryString =  queryString.replaceAll("\\+", "%2B");
            List<NameValuePair> values = URLEncodedUtils.parse(queryString, Charset.forName("UTF-8"));
            List<String> valueList = Lists.newArrayList();
              for (NameValuePair nameValuePair: values) {
                  if (nameValuePair.getName().equals(QueryParamEnum.QUERY.getValue())) {
                      valueList.add(nameValuePair.getValue());
                  }
              }
              String[] valueString = new String[valueList.size()];
              valueList.toArray(valueString);
            return valueString;
        }
        return super.getParameterValues(name);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }
    @Override
    public ServletInputStream getInputStream() throws IOException {

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body);

        return new ServletInputStream() {

            @Override
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }

            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return false;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }
        };
    }

}

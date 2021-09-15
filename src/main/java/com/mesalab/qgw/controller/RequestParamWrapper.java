package com.mesalab.qgw.controller;



import com.google.common.collect.Lists;
import com.mesalab.common.enums.QueryParamEnum;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.nio.charset.Charset;
import java.util.List;

public class RequestParamWrapper extends HttpServletRequestWrapper {
    private String queryString = null;
    RequestParamWrapper(HttpServletRequest request) {
        super(request);
        queryString = request.getQueryString();

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







}

package com.mesalab.qgw.controller;

import org.apache.http.entity.ContentType;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
@Component
@WebFilter(urlPatterns = "/*")
public class ControllerFilter implements  Filter {

    private MultipartResolver multipartResolver = null;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        ServletContext servletContext = filterConfig.getServletContext();
        WebApplicationContext webApplicationContext = WebApplicationContextUtils.getWebApplicationContext(servletContext);
        multipartResolver = (MultipartResolver) webApplicationContext.getBean("multipartResolver");
    }


    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        String contentType = servletRequest.getContentType();
        if (contentType != null && contentType.contains(ContentType.MULTIPART_FORM_DATA.getMimeType())) {
            MultipartHttpServletRequest multipartRequest = multipartResolver.resolveMultipart((HttpServletRequest) servletRequest);
            filterChain.doFilter(multipartRequest, servletResponse);
        } else {
            filterChain.doFilter(new RequestParamWrapper((HttpServletRequest) servletRequest), servletResponse);
        }
    }

    @Override
    public void destroy() {

    }
}

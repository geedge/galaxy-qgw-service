package com.mesalab.qgw.controller;

import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
@WebFilter(urlPatterns = "/*")
public class ControllerFilter implements  Filter {

    private static final Set<String> NOT_FILTER_PATHS = Collections.unmodifiableSet(new HashSet<>(
            Collections.singletonList("/knowledge_base/v1")));

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }


    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = ((HttpServletRequest) servletRequest);
        String servletPath = httpServletRequest.getServletPath();
        if (NOT_FILTER_PATHS.contains(servletPath)) {
            filterChain.doFilter(servletRequest, servletResponse);
        } else {
            filterChain.doFilter(new RequestParamWrapper(httpServletRequest), servletResponse);
        }
    }

    @Override
    public void destroy() {

    }
}

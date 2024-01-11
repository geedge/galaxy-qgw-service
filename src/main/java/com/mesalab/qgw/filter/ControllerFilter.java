package com.mesalab.qgw.filter;

import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Component
@WebFilter(urlPatterns = "/*")
public class ControllerFilter implements Filter {
    private static final String NOT_FILTER_PATHS = "/v1/knowledge_base";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }


    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = ((HttpServletRequest) servletRequest);
        String servletPath = httpServletRequest.getServletPath();
        if (String.valueOf(servletPath).contains(NOT_FILTER_PATHS)) {
            filterChain.doFilter(servletRequest, servletResponse);
        } else {
            filterChain.doFilter(new RequestParamWrapper(httpServletRequest), servletResponse);
        }
    }

    @Override
    public void destroy() {

    }
}

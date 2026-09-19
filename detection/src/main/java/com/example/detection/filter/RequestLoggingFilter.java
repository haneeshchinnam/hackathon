package com.example.detection.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final System.Logger log = System.getLogger(RequestLoggingFilter.class.getName());

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();

        String requestId = request.getHeader("X-Request-ID");

        if (requestId == null || requestId.isEmpty()) {
            requestId = java.util.UUID.randomUUID().toString();
        }

        MDC.put("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);

        try {

            log.log(System.Logger.Level.INFO, "Request ID: {0}, Method: {1}, URI: {2}, Remote Address: {3}", requestId, request.getMethod(), request.getRequestURI(), request.getRemoteAddr());

            filterChain.doFilter(request, response);

        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.log(System.Logger.Level.INFO, "Request ID: {0}, Method: {1}, URI: {2}, Duration: {3} ms", requestId, request.getMethod(), request.getRequestURI(), duration);
            MDC.remove("requestId");
        }
    }
}

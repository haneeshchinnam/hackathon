package com.example.detection.filter;

import com.example.detection.dto.RateLimitBucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 10;
    private static final long WINDOW_SECONDS = 60;

    private final ConcurrentHashMap<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(request.getRequestURI().startsWith("/api/auth/") || request.getRequestURI().startsWith("/api/v1/auth/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // Implement your rate limiting logic here
        // For example, you can check the request count for the client IP and decide whether to allow or block the request

        // If the request is allowed, continue the filter chain

        String key = getRateLimitKey(request);

        RateLimitBucket bucket = buckets.compute(key, (k, existingBucket) -> updateBucket(existingBucket));

        if (bucket.getCount() > MAX_REQUESTS) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");

            response.setHeader("Retry-After", Long.toString(Math.max(1,bucket.windowEnd()-Instant.now().getEpochSecond())));
            new com.fasterxml.jackson.databind.ObjectMapper().writeValue(response.getOutputStream(), java.util.Map.of(
                "status",429,"error","Too Many Requests","message","Authentication request limit exceeded",
                "path",request.getRequestURI(),"timestamp",Instant.now().toString()));
            return;
        }


        filterChain.doFilter(request, response);
    }

    private String getRateLimitKey(HttpServletRequest request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            return "USER:" + authentication.getName(); // Use username as key
        } else {
            return "IP:" + request.getRemoteAddr(); // Use IP address as key
        }
    }

    private RateLimitBucket getRateLimitBucket(String key) {
        return buckets.computeIfAbsent(key, k -> new RateLimitBucket(new AtomicInteger(1), WINDOW_SECONDS));
    }

    private RateLimitBucket updateBucket(RateLimitBucket bucket) {
        long now = Instant.now().getEpochSecond();

        if (bucket == null || now >= bucket.windowEnd()) {
            // Reset the bucket
            return new RateLimitBucket(new AtomicInteger(1), now + WINDOW_SECONDS);
        }

        // Increment the request count
        bucket.count().incrementAndGet();
        return bucket;
    }

}

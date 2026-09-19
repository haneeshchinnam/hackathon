package com.example.detection.config;

import com.example.detection.filter.RateLimitFilter;
import com.example.detection.filter.RequestLoggingFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AppConfig {

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<com.example.detection.security.BearerTokenAuthenticationFilter> bearerRegistration(com.example.detection.security.BearerTokenAuthenticationFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter); registration.setEnabled(false); return registration;
    }
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<RateLimitFilter> rateRegistration(RateLimitFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter); registration.setEnabled(false); return registration;
    }
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<RequestLoggingFilter> loggingRegistration(RequestLoggingFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter); registration.setEnabled(false); return registration;
    }
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter();
    }

    @Bean
    RequestLoggingFilter requestLoggingFilter() {
        return new RequestLoggingFilter();
    }
}

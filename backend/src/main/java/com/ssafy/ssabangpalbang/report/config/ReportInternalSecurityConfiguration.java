package com.ssafy.ssabangpalbang.report.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.report.security.ReportInternalAuthenticationFilter;
import com.ssafy.ssabangpalbang.report.security.ReportInternalSecurityErrorHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(ReportInternalProperties.class)
public class ReportInternalSecurityConfiguration {

    @Bean
    public ReportInternalAuthenticationFilter reportInternalAuthenticationFilter(
            ReportInternalProperties properties
    ) {
        return new ReportInternalAuthenticationFilter(properties);
    }

    @Bean
    public FilterRegistrationBean<ReportInternalAuthenticationFilter>
    reportInternalAuthenticationFilterRegistration(
            ReportInternalAuthenticationFilter filter
    ) {
        FilterRegistrationBean<ReportInternalAuthenticationFilter>
                registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public ReportInternalSecurityErrorHandler reportInternalSecurityErrorHandler(
            ObjectMapper objectMapper
    ) {
        return new ReportInternalSecurityErrorHandler(objectMapper);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain reportInternalSecurityFilterChain(
            HttpSecurity http,
            ReportInternalAuthenticationFilter authenticationFilter,
            ReportInternalSecurityErrorHandler securityErrorHandler
    ) throws Exception {
        return http
                .securityMatcher("/internal/v1/reports/**")
                .csrf(csrf -> csrf.disable())
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .logout(logout -> logout.disable())
                .requestCache(requestCache -> requestCache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                ))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler)
                )
                .addFilterBefore(
                        authenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }
}

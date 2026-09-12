package com.ssafy.ssabangpalbang.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.auth.security.JwtAuthenticationEntryPoint;
import com.ssafy.ssabangpalbang.auth.security.JwtAuthenticationFilter;
import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class AuthSecurityConfiguration {

    private static final String[] PUBLIC_AUTH_PATHS = {
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/social-login",
            "/api/v1/auth/social-signup",
            "/api/v1/auth/reissue",
            "/api/v1/auth/password-reset/request",
            "/api/v1/auth/password-reset/confirm",
            "/api/v1/auth/password-reset/verify"
    };

    private static final String[] PUBLIC_AUTH_GET_PATHS = {
            "/api/v1/auth/check-email",
            "/api/v1/auth/check-nickname"
    };

    private static final String[] PUBLIC_DOCUMENTATION_PATHS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    @Bean
    public JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint(
            ObjectMapper objectMapper
    ) {
        return new JwtAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            JwtAuthenticationEntryPoint authenticationEntryPoint
    ) {
        return new JwtAuthenticationFilter(
                jwtTokenProvider,
                authenticationEntryPoint
        );
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint authenticationEntryPoint
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .logout(logout -> logout.disable())
                .requestCache(requestCache -> requestCache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                ))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR,
                                DispatcherType.FORWARD
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                PUBLIC_AUTH_PATHS
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                PUBLIC_AUTH_GET_PATHS
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/oauth/kakao/callback",
                                "/oauth/naver/callback",
                                "/report/*",
                                "/ws",
                                "/actuator/health"
                        ).permitAll()
                        .requestMatchers(
                                PUBLIC_DOCUMENTATION_PATHS
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }
}

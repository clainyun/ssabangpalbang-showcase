package com.ssafy.ssabangpalbang.report.security;

import com.ssafy.ssabangpalbang.report.config.ReportInternalProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public final class ReportInternalAuthenticationFilter
        extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expectedTokenHash;

    public ReportInternalAuthenticationFilter(
            ReportInternalProperties properties
    ) {
        this.expectedTokenHash = properties.token().isBlank()
                ? new byte[0]
                : sha256(properties.token());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (matches(authorization)) {
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            ReportInternalPrincipal.worker(),
                            null,
                            List.of()
                    );
            SecurityContext context = SecurityContextHolder
                    .createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } else {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private boolean matches(String authorization) {
        if (expectedTokenHash.length == 0
                || authorization == null
                || authorization.length() <= BEARER_PREFIX.length()
                || !authorization.regionMatches(
                        true,
                        0,
                        BEARER_PREFIX,
                        0,
                        BEARER_PREFIX.length()
                )) {
            return false;
        }

        String candidate = authorization
                .substring(BEARER_PREFIX.length())
                .strip();
        return !candidate.isEmpty()
                && MessageDigest.isEqual(
                        expectedTokenHash,
                        sha256(candidate)
                );
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    exception
            );
        }
    }
}

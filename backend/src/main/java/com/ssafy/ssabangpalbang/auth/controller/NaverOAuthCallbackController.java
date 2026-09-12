package com.ssafy.ssabangpalbang.auth.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 네이버가 허용하는 HTTPS callback과 앱의 custom scheme 사이를 연결합니다.
 *
 * 네이버의 authorization code는 앱에서 기존 social-login API로 교환하므로,
 * 이 endpoint는 code/state 또는 provider error를 앱으로 전달하기만 합니다.
 *
 * 앱이 직접 호출하는 REST 계약이 아닌 브라우저 리다이렉트 브리지이므로
 * Swagger 문서에서는 제외합니다.
 */
@Hidden
@RestController
public class NaverOAuthCallbackController {

    private static final URI APP_REDIRECT_URI =
            URI.create("ssabangpalbang://oauth");

    @GetMapping("/oauth/naver/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false)
            String errorDescription
    ) {
        UriComponentsBuilder redirect = UriComponentsBuilder
                .fromUri(APP_REDIRECT_URI);
        addQueryParamIfPresent(redirect, "code", code);
        addQueryParamIfPresent(redirect, "state", state);
        addQueryParamIfPresent(redirect, "error", error);
        addQueryParamIfPresent(redirect, "error_description", errorDescription);

        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(redirect.build().encode().toUri())
                .build();
    }

    private void addQueryParamIfPresent(
            UriComponentsBuilder redirect,
            String name,
            String value
    ) {
        if (value != null && !value.isBlank()) {
            redirect.queryParam(name, value);
        }
    }
}

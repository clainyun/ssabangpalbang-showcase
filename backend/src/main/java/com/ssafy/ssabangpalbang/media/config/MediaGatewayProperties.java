package com.ssafy.ssabangpalbang.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "ssabangpalbang.media.gateway")
public record MediaGatewayProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String baseUrl,
        @DefaultValue("") String internalToken,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("10s") Duration readTimeout,
        @DefaultValue("24h") Duration sttRetention
) {

    public MediaGatewayProperties {
        if (enabled && (baseUrl == null || baseUrl.isBlank())) {
            throw new IllegalArgumentException(
                    "MEDIA_GATEWAY_BASE_URL is required when the media gateway is enabled"
            );
        }
        if (enabled && (internalToken == null || internalToken.isBlank())) {
            throw new IllegalArgumentException(
                    "MEDIA_GATEWAY_INTERNAL_TOKEN is required when the media gateway is enabled"
            );
        }
        if (connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "MEDIA_GATEWAY_CONNECT_TIMEOUT must be positive"
            );
        }
        if (readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "MEDIA_GATEWAY_READ_TIMEOUT must be positive"
            );
        }
        if (sttRetention.isZero() || sttRetention.isNegative()) {
            throw new IllegalArgumentException(
                    "MEDIA_STT_RETENTION must be positive"
            );
        }
    }
}

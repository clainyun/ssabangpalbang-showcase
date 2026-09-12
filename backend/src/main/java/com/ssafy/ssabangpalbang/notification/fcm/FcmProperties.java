package com.ssafy.ssabangpalbang.notification.fcm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fcm")
public record FcmProperties(
        boolean enabled,
        String serviceAccountPath
) {

    public boolean isConfigured() {
        return enabled
                && serviceAccountPath != null
                && !serviceAccountPath.isBlank();
    }
}

package com.ssafy.ssabangpalbang.apartment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;

@ConfigurationProperties(prefix = "ssabangpalbang.apartment.image")
public record ApartmentImageProperties(
        @DefaultValue("") String publicBaseUrl
) {

    public ApartmentImageProperties {
        publicBaseUrl = normalize(publicBaseUrl);
        if (!publicBaseUrl.isEmpty()) {
            URI uri = URI.create(publicBaseUrl);
            if (!uri.isAbsolute()
                    || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException(
                        "APARTMENT_IMAGE_PUBLIC_BASE_URL must be an absolute HTTP(S) URL"
                );
            }
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().replaceAll("/+$", "");
    }
}

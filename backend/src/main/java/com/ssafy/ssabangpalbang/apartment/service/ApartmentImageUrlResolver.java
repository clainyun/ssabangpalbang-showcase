package com.ssafy.ssabangpalbang.apartment.service;

import com.ssafy.ssabangpalbang.apartment.config.ApartmentImageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApartmentImageUrlResolver {

    private final ApartmentImageProperties properties;

    public String resolve(String objectKey) {
        if (properties.publicBaseUrl().isEmpty()
                || objectKey == null
                || objectKey.isBlank()) {
            return null;
        }
        return properties.publicBaseUrl() + "/" + objectKey.replaceFirst("^/+", "");
    }
}

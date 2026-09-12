package com.ssafy.ssabangpalbang.apartment.service;

import com.ssafy.ssabangpalbang.apartment.config.ApartmentImageProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApartmentImageUrlResolverTest {

    @Test
    void resolvesVersionedObjectKeyAgainstConfiguredPublicOrigin() {
        var resolver = new ApartmentImageUrlResolver(
                new ApartmentImageProperties("https://cdn.example.com/")
        );

        assertThat(resolver.resolve("/apartment-images/v1/A100.webp"))
                .isEqualTo("https://cdn.example.com/apartment-images/v1/A100.webp");
    }

    @Test
    void returnsNullWhenBaseUrlOrObjectKeyIsUnavailable() {
        assertThat(new ApartmentImageUrlResolver(
                new ApartmentImageProperties("")
        ).resolve("apartment-images/v1/A100.webp")).isNull();
        assertThat(new ApartmentImageUrlResolver(
                new ApartmentImageProperties("https://cdn.example.com")
        ).resolve(null)).isNull();
    }

    @Test
    void rejectsNonHttpPublicBaseUrl() {
        assertThatThrownBy(() -> new ApartmentImageProperties("file:///tmp/images"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute HTTP(S) URL");
    }
}

package com.ssafy.ssabangpalbang.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.config.import=optional:file:src/main/resources/application.yaml",
        "jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
class ResponseCompressionConfigTest {

    @Autowired
    private ServerProperties serverProperties;

    @Test
    void JSON_응답_gzip_압축이_켜져_있다() {
        var compression = serverProperties.getCompression();

        assertThat(compression.getEnabled()).isTrue();
        assertThat(compression.getMimeTypes()).contains("application/json");
        assertThat(compression.getMinResponseSize().toBytes()).isEqualTo(1024L);
    }
}

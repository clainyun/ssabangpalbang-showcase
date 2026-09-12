package com.ssafy.ssabangpalbang.media.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class MediaGatewayConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            MediaGatewayConfiguration.class
                    )
                    .withBean(
                            RestClient.Builder.class,
                            RestClient::builder
                    );

    @Test
    void startsWithoutGatewayClientWhenDisabled() {
        contextRunner
                .withPropertyValues(
                        "ssabangpalbang.media.gateway.enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(
                            MediaGatewayProperties.class
                    );
                    assertThat(context).doesNotHaveBean(
                            RestClient.class
                    );
                });
    }

    @Test
    void createsGatewayClientWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "ssabangpalbang.media.gateway.enabled=true",
                        "ssabangpalbang.media.gateway.base-url=https://gateway.example",
                        "ssabangpalbang.media.gateway.internal-token=test-token"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(
                            RestClient.class
                    );
                });
    }

    @Test
    void failsFastWhenEnabledTokenIsBlank() {
        contextRunner
                .withPropertyValues(
                        "ssabangpalbang.media.gateway.enabled=true",
                        "ssabangpalbang.media.gateway.base-url=https://gateway.example",
                        "ssabangpalbang.media.gateway.internal-token="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining(
                                    "MEDIA_GATEWAY_INTERNAL_TOKEN"
                            );
                });
    }
}

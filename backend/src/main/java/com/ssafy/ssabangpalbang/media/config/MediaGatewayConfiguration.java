package com.ssafy.ssabangpalbang.media.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MediaGatewayProperties.class)
public class MediaGatewayConfiguration {

    @Bean
    @Qualifier("mediaGatewayRestClient")
    @ConditionalOnProperty(
            prefix = "ssabangpalbang.media.gateway",
            name = "enabled",
            havingValue = "true"
    )
    public RestClient mediaGatewayRestClient(
            RestClient.Builder builder,
            MediaGatewayProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(
                        "Authorization",
                        "Bearer " + properties.internalToken()
                )
                .build();
    }
}

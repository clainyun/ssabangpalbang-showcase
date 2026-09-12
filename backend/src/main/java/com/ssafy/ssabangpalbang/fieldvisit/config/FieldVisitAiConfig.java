package com.ssafy.ssabangpalbang.fieldvisit.config;

import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistAiClient;
import com.ssafy.ssabangpalbang.fieldvisit.client.ChecklistSelectAiClient;
import com.ssafy.ssabangpalbang.fieldvisit.client.RestChecklistAiClient;
import com.ssafy.ssabangpalbang.fieldvisit.client.RestChecklistSelectAiClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(FieldVisitAiProperties.class)
public class FieldVisitAiConfig {

    @Bean
    @ConditionalOnMissingBean(ChecklistAiClient.class)
    ChecklistAiClient checklistAiClient(
            FieldVisitAiProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        return new RestChecklistAiClient(buildRestClient(properties, restClientBuilder), properties);
    }

    @Bean
    @ConditionalOnMissingBean(ChecklistSelectAiClient.class)
    ChecklistSelectAiClient checklistSelectAiClient(
            FieldVisitAiProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        return new RestChecklistSelectAiClient(
                buildRestClient(properties, restClientBuilder),
                properties
        );
    }

    private static RestClient buildRestClient(
            FieldVisitAiProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .build();
    }
}

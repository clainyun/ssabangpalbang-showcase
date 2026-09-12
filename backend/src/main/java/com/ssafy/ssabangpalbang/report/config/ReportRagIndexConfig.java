package com.ssafy.ssabangpalbang.report.config;

import com.ssafy.ssabangpalbang.report.client.ReportRagIndexClient;
import com.ssafy.ssabangpalbang.report.client.RestReportRagIndexClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ReportRagIndexProperties.class)
public class ReportRagIndexConfig {

    @Bean
    @ConditionalOnMissingBean(ReportRagIndexClient.class)
    ReportRagIndexClient reportRagIndexClient(
            ReportRagIndexProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient restClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .build();
        return new RestReportRagIndexClient(restClient, properties);
    }
}

package com.ssafy.ssabangpalbang.apartment.dataload;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

@Configuration
@Profile({"dataload", "dataload-tx"})
@EnableConfigurationProperties(DataLoadProperties.class)
public class DataLoadClientConfig {

    @Bean
    RestTemplate dataLoadRestTemplate() {
        return new RestTemplate();
    }
}

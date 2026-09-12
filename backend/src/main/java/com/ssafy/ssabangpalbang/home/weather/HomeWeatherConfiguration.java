package com.ssafy.ssabangpalbang.home.weather;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(HomeWeatherProperties.class)
public class HomeWeatherConfiguration {

    @Bean
    @Qualifier("homeWeatherRestClient")
    public RestClient homeWeatherRestClient(
            HomeWeatherProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public Cache<HomeWeatherCacheKey, HomeWeatherCacheEntry> homeWeatherCache(
            HomeWeatherProperties properties
    ) {
        return Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(properties.staleTtl())
                .build();
    }

    @Bean
    public Cache<HomeWeatherAlertCacheKey, HomeWeatherAlertCacheEntry>
    homeWeatherAlertCache(HomeWeatherProperties properties) {
        return Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(properties.alertStaleTtl())
                .build();
    }
}

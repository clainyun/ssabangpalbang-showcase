package com.ssafy.ssabangpalbang.fieldvisit.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ssafy.ssabangpalbang.fieldvisit.client.KakaoLocalPoi;
import com.ssafy.ssabangpalbang.fieldvisit.client.RoutePoiCacheKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableConfigurationProperties(FieldVisitRouteProperties.class)
public class FieldVisitRouteConfiguration {

    @Bean
    @Qualifier("fieldVisitRouteRestClient")
    RestClient fieldVisitRouteRestClient(FieldVisitRouteProperties properties) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getPoi().getConnectTimeout());
        requestFactory.setReadTimeout(properties.getPoi().getReadTimeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Bean
    @Qualifier("fieldVisitWalkingRestClient")
    RestClient fieldVisitWalkingRestClient(FieldVisitRouteProperties properties) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getWalking().getConnectTimeout());
        requestFactory.setReadTimeout(properties.getWalking().getReadTimeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Bean
    Cache<RoutePoiCacheKey, List<KakaoLocalPoi>> fieldVisitRoutePoiCache(
            FieldVisitRouteProperties properties
    ) {
        return Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(properties.getPoi().getCacheTtl())
                .build();
    }

    @Bean("fieldVisitRoutePoiExecutor")
    Executor fieldVisitRoutePoiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("field-route-poi-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

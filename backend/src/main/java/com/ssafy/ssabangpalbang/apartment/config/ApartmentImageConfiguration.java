package com.ssafy.ssabangpalbang.apartment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ApartmentImageProperties.class)
public class ApartmentImageConfiguration {
}

package com.ssafy.ssabangpalbang.apartment.dataload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dataload")
public class ApartmentDataLoadRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ApartmentDataLoadRunner.class);

    private final ApartmentDataLoadService service;
    private final ConfigurableApplicationContext context;
    private final String publicDataServiceKey;
    private final String kakaoRestApiKey;

    public ApartmentDataLoadRunner(
            ApartmentDataLoadService service,
            ConfigurableApplicationContext context,
            @Value("${public-data.service-key:}") String publicDataServiceKey,
            @Value("${kakao.rest-api-key:}") String kakaoRestApiKey
    ) {
        this.service = service;
        this.context = context;
        this.publicDataServiceKey = publicDataServiceKey;
        this.kakaoRestApiKey = kakaoRestApiKey;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (publicDataServiceKey.isBlank() || kakaoRestApiKey.isBlank()) {
                log.warn("[dataload] PUBLIC_DATA_SERVICE_KEY 또는 KAKAO_REST_API_KEY가 없어 종료합니다.");
                return;
            }
            service.load();
        } finally {
            SpringApplication.exit(context);
        }
    }
}

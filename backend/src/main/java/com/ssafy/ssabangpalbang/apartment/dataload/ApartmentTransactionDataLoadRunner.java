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
@Profile("dataload-tx")
public class ApartmentTransactionDataLoadRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ApartmentTransactionDataLoadRunner.class);

    private final ApartmentTransactionDataLoadService service;
    private final ConfigurableApplicationContext context;
    private final String publicDataServiceKey;

    public ApartmentTransactionDataLoadRunner(
            ApartmentTransactionDataLoadService service,
            ConfigurableApplicationContext context,
            @Value("${public-data.service-key:}") String publicDataServiceKey
    ) {
        this.service = service;
        this.context = context;
        this.publicDataServiceKey = publicDataServiceKey;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (publicDataServiceKey.isBlank()) {
                log.warn("[dataload-tx] PUBLIC_DATA_SERVICE_KEY가 없어 종료합니다.");
                return;
            }
            service.load();
        } finally {
            SpringApplication.exit(context);
        }
    }
}

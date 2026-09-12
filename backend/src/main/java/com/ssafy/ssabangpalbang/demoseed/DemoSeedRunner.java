package com.ssafy.ssabangpalbang.demoseed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 유저 테스트(시연)용 데이터 시더 진입점.
 *
 * <p>{@code demoseed} 프로파일 단발 실행이다. 프로파일이 없으면 이 빈은 등록조차 되지 않아
 * 평소 기동에 영향이 없다 — {@code ApartmentDataLoadRunner}와 같은 방식(CLAUDE.md §6).</p>
 *
 * <pre>
 * # 시딩(멱등 — 여러 번 돌려도 같은 상태). 계정 비밀번호는 반드시 외부에서 주입한다.
 * ./gradlew bootRun --args='--spring.profiles.active=local,demoseed \
 *     --demoseed.password=&lt;계정비밀번호&gt;'
 *
 * # 리허설 후 초기화 + 재시딩
 * ./gradlew bootRun --args='--spring.profiles.active=local,demoseed \
 *     --demoseed.password=&lt;계정비밀번호&gt; \
 *     --demoseed.reset=true --demoseed.reset-token=WIPE-AND-RESEED'
 * </pre>
 */
@Component
@Profile("demoseed")
@EnableConfigurationProperties(DemoSeedProperties.class)
public class DemoSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);

    private final DemoSeedService seedService;
    private final DemoSeedResetService resetService;
    private final DemoSeedProperties properties;
    private final ConfigurableApplicationContext context;

    public DemoSeedRunner(
            DemoSeedService seedService,
            DemoSeedResetService resetService,
            DemoSeedProperties properties,
            ConfigurableApplicationContext context
    ) {
        this.seedService = seedService;
        this.resetService = resetService;
        this.properties = properties;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        AtomicInteger exitCode = new AtomicInteger(0);
        try {
            String password = properties.getPassword();
            if (password == null || password.isBlank()) {
                log.error("[demoseed] demoseed.password가 비어 있어 시딩을 중단합니다. "
                        + "--demoseed.password=<값> 또는 DEMOSEED_PASSWORD 환경변수로 지정하세요.");
                exitCode.set(1);
                return;
            }
            if (properties.isReset()) {
                resetService.reset(properties.getResetToken());
            }

            List<Long> reportIds = seedService.seed();
            seedService.indexReports(reportIds);

            log.info("[demoseed] 시딩을 완료했습니다. 계정 비밀번호는 demoseed.password 값입니다.");
        } catch (RuntimeException exception) {
            log.error("[demoseed] 시딩에 실패했습니다.", exception);
            exitCode.set(1);
        } finally {
            SpringApplication.exit(context, exitCode::get);
        }
    }
}

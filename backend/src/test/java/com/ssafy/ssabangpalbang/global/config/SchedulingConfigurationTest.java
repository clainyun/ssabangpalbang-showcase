package com.ssafy.ssabangpalbang.global.config;

import com.ssafy.ssabangpalbang.study.service.ScheduleReminderProperties;
import com.ssafy.ssabangpalbang.study.service.StudyScheduleReminderScheduler;
import com.ssafy.ssabangpalbang.study.service.StudyScheduleReminderService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.support.CronExpression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class SchedulingConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            SchedulingConfiguration.class,
                            StudyScheduleReminderScheduler.class
                    )
                    .withBean(
                            StudyScheduleReminderService.class,
                            () -> mock(StudyScheduleReminderService.class)
                    );

    @Test
    void E1_enabled가_false면_스케줄러_빈이_없다() {
        contextRunner
                .withPropertyValues(
                        "sbpb.schedule-reminder.enabled=false"
                )
                .run(context -> assertThat(context)
                        .doesNotHaveBean(StudyScheduleReminderScheduler.class));
    }

    @Test
    void E2_enabled를_지정하지_않으면_스케줄러_빈이_등록된다() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(StudyScheduleReminderScheduler.class));
    }

    @Test
    void E3_기본_cron_표현식이_유효하다() {
        contextRunner.run(context -> {
            ScheduleReminderProperties properties =
                    context.getBean(ScheduleReminderProperties.class);

            assertThatCode(() -> CronExpression.parse(properties.cron()))
                    .doesNotThrowAnyException();
            assertThat(properties.cron()).isEqualTo("0 */10 * * * *");
        });
    }

}

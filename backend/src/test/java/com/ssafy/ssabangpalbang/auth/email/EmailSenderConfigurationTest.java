package com.ssafy.ssabangpalbang.auth.email;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class EmailSenderConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MailSenderAutoConfiguration.class
            ))
            .withUserConfiguration(
                    EmailSenderConfiguration.class,
                    LoggingEmailSender.class,
                    SmtpEmailSender.class
            );

    @Test
    void 메일_설정이_없고_local_test_프로파일도_아니면_NoOp_폴백이_등록된다() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(EmailSender.class);
            assertThat(context.getBean(EmailSender.class))
                    .isInstanceOf(NoOpEmailSender.class);
        });
    }

    @Test
    void spring_mail_host가_설정되면_SMTP_구현이_등록되고_폴백은_등록되지_않는다() {
        runner.withPropertyValues("spring.mail.host=localhost")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmailSender.class);
                    assertThat(context.getBean(EmailSender.class))
                            .isInstanceOf(SmtpEmailSender.class);
                });
    }
}

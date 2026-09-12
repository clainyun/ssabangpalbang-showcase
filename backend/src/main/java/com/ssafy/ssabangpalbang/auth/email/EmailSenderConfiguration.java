package com.ssafy.ssabangpalbang.auth.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 활성화된 이메일 발송 구현이 하나도 없을 때만 no-op 폴백을 등록한다.
 * LoggingEmailSender(local/test)나 SmtpEmailSender(spring.mail.host 설정 시)가 존재하면
 * {@link ConditionalOnMissingBean}에 의해 이 폴백은 등록되지 않는다.
 */
@Configuration
public class EmailSenderConfiguration {

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    public EmailSender noOpEmailSender() {
        return new NoOpEmailSender();
    }
}

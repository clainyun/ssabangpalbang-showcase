package com.ssafy.ssabangpalbang.auth.email;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP 기반 실제 이메일 발송 구현.
 * `spring.mail.host`가 설정된 환경에서만 빈으로 등록된다(값이 없으면 등록되지 않음).
 * 자격 증명·발신 주소 등 실제 값은 코드가 아니라 배포 환경 설정으로 주입한다.
 */
@Component
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private static final String SUBJECT = "[싸방팔방] 비밀번호 재설정 인증 코드";

    private final JavaMailSender mailSender;

    @Override
    public void sendPasswordResetCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject(SUBJECT);
        message.setText(
                "비밀번호 재설정 인증 코드는 " + code + " 입니다. "
                        + "10분 안에 입력해 주세요."
        );
        mailSender.send(message);
    }
}

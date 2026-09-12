package com.ssafy.ssabangpalbang.auth.email;

import lombok.extern.slf4j.Slf4j;

/**
 * 어떤 이메일 발송 구현도 등록되지 않은 환경(예: 운영에 SMTP 미설정)에서 사용하는 폴백 구현.
 * 실제 발송 대신 설정 누락 경고만 남겨 애플리케이션 전체 기동 실패를 막는다(기능만 비활성화).
 * 재설정 코드·비밀번호 등 시크릿은 로그에 남기지 않는다.
 */
@Slf4j
public class NoOpEmailSender implements EmailSender {

    @Override
    public void sendPasswordResetCode(String email, String code) {
        log.warn(
                "메일 발송이 설정되지 않아 비밀번호 재설정 코드를 발송하지 못했습니다. "
                        + "운영 환경에서는 spring.mail.host 설정이 필요합니다."
        );
    }
}

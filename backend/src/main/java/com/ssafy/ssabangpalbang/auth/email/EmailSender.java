package com.ssafy.ssabangpalbang.auth.email;

/**
 * 인증 관련 이메일 발송 추상화.
 * 프로파일/설정에 따라 로깅 기반 개발용 구현 또는 SMTP 구현이 주입된다.
 */
public interface EmailSender {

    void sendPasswordResetCode(String email, String code);
}

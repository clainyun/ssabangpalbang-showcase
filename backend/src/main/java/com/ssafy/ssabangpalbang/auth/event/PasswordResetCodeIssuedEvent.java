package com.ssafy.ssabangpalbang.auth.event;

/**
 * 비밀번호 재설정 코드가 발급되어 이메일 발송이 필요함을 알리는 이벤트.
 * 코드 원문은 인메모리 이벤트로만 전달하며 로그·응답 본문에는 노출하지 않는다.
 */
public record PasswordResetCodeIssuedEvent(
        String email,
        String code
) {
}

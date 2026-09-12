package com.ssafy.ssabangpalbang.auth.event;

/**
 * 비밀번호 재설정이 성공적으로 커밋되었음을 알리는 이벤트.
 * 커밋 이후 사용한 코드/시도 카운터 정리와 기존 세션 무효화를 트리거한다.
 */
public record PasswordResetCompletedEvent(
        String email,
        Long memberId
) {
}

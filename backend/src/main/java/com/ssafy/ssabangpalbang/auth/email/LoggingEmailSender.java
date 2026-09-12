package com.ssafy.ssabangpalbang.auth.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬/테스트 프로파일 전용 개발용 이메일 발송 대체 구현.
 * 실제 SMTP 발송 대신 재설정 코드를 로그로 출력해 로컬에서 confirm 흐름을 검증할 수 있게 한다.
 * prod 프로파일에서는 활성화되지 않으므로 재설정 코드가 운영 로그에 남지 않는다.
 */
@Slf4j
@Component
@Profile({"local", "test"})
public class LoggingEmailSender implements EmailSender {

    @Override
    public void sendPasswordResetCode(String email, String code) {
        log.info(
                "[비밀번호 재설정][개발용] email={} code={} (로컬 로그 발송, TTL 10분)",
                email,
                code
        );
    }
}

package com.ssafy.ssabangpalbang.auth.event;

import com.ssafy.ssabangpalbang.auth.config.AuthEmailAsyncConfig;
import com.ssafy.ssabangpalbang.auth.email.EmailSender;
import com.ssafy.ssabangpalbang.auth.token.PasswordResetCodeStore;
import com.ssafy.ssabangpalbang.auth.token.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 비밀번호 재설정 관련 부작용(이메일 발송, 커밋 이후 정리)을 요청 스레드/트랜잭션 밖에서 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetEventListener {

    private final EmailSender emailSender;
    private final PasswordResetCodeStore passwordResetCodeStore;
    private final RefreshTokenStore refreshTokenStore;

    /**
     * 코드 발급 이벤트를 비동기로 받아 이메일을 발송한다.
     * 발송 실패는 요청 결과에 영향을 주지 않으며, 코드 원문은 로그에 남기지 않는다.
     */
    @Async(AuthEmailAsyncConfig.EXECUTOR)
    @EventListener
    public void onCodeIssued(PasswordResetCodeIssuedEvent event) {
        try {
            emailSender.sendPasswordResetCode(event.email(), event.code());
        } catch (RuntimeException exception) {
            log.warn(
                    "비밀번호 재설정 코드 이메일 발송에 실패했습니다. reason=internal_error"
            );
        }
    }

    /**
     * 비밀번호 변경 커밋 이후에만 코드/시도 카운터를 정리하고 기존 세션을 무효화한다.
     * 커밋 실패 시에는 실행되지 않아 코드·세션이 먼저 정리되는 불일치 창을 없앤다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetCompleted(PasswordResetCompletedEvent event) {
        passwordResetCodeStore.deleteCode(event.email());
        passwordResetCodeStore.clearAttempts(event.email());
        refreshTokenStore.revokeAll(event.memberId());
    }
}

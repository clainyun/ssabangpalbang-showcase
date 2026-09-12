package com.ssafy.ssabangpalbang.auth.event;

import com.ssafy.ssabangpalbang.auth.email.EmailSender;
import com.ssafy.ssabangpalbang.auth.token.PasswordResetCodeStore;
import com.ssafy.ssabangpalbang.auth.token.RefreshTokenStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PasswordResetEventListenerTest {

    @Mock
    private EmailSender emailSender;

    @Mock
    private PasswordResetCodeStore passwordResetCodeStore;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @InjectMocks
    private PasswordResetEventListener listener;

    @Test
    void 코드_발급_이벤트를_받으면_이메일을_발송한다() {
        listener.onCodeIssued(
                new PasswordResetCodeIssuedEvent("dain@example.com", "123456")
        );

        verify(emailSender).sendPasswordResetCode("dain@example.com", "123456");
        verifyNoInteractions(passwordResetCodeStore, refreshTokenStore);
    }

    @Test
    void 이메일_발송이_실패해도_예외를_전파하지_않는다() {
        doThrow(new RuntimeException("smtp down"))
                .when(emailSender)
                .sendPasswordResetCode("dain@example.com", "123456");

        assertThatCode(() -> listener.onCodeIssued(
                new PasswordResetCodeIssuedEvent("dain@example.com", "123456")
        )).doesNotThrowAnyException();
    }

    @Test
    void 완료_이벤트를_받으면_코드와_시도_카운터를_지우고_세션을_무효화한다() {
        listener.onPasswordResetCompleted(
                new PasswordResetCompletedEvent("dain@example.com", 1L)
        );

        verify(passwordResetCodeStore).deleteCode("dain@example.com");
        verify(passwordResetCodeStore).clearAttempts("dain@example.com");
        verify(refreshTokenStore).revokeAll(1L);
    }
}

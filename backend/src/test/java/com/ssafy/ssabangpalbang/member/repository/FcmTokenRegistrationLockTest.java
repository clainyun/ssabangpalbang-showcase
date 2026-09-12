package com.ssafy.ssabangpalbang.member.repository;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmTokenRegistrationLockTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    private FcmTokenRegistrationLock registrationLock;

    @BeforeEach
    void setUp() {
        registrationLock = new FcmTokenRegistrationLock(entityManager);
        when(entityManager.createNativeQuery(anyString()))
                .thenReturn(query);
        when(query.setParameter(eq("lockKey"), anyString()))
                .thenReturn(query);
        when(query.setHint(eq("jakarta.persistence.query.timeout"), any()))
                .thenReturn(query);
        when(query.getSingleResult()).thenReturn(true);
    }

    @Test
    void 기기와_토큰_잠금을_결정적_순서로_획득한다() {
        registrationLock.acquire("device-A", "token-A");

        ArgumentCaptor<String> lockKeyCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(query, times(3))
                .setParameter(eq("lockKey"), lockKeyCaptor.capture());
        verify(query, times(3)).getSingleResult();
        assertThat(lockKeyCaptor.getAllValues())
                .containsExactly(
                        "fcm-registration:global",
                        "fcm-device:device-A",
                        "fcm-token:token-A"
                );
    }

    @Test
    void 삭제는_전역과_기기_잠금을_같은_순서로_획득한다() {
        registrationLock.acquireDevice("device-A");

        ArgumentCaptor<String> lockKeyCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(query, times(2))
                .setParameter(eq("lockKey"), lockKeyCaptor.capture());
        assertThat(lockKeyCaptor.getAllValues())
                .containsExactly(
                        "fcm-registration:global",
                        "fcm-device:device-A"
                );
    }

    @Test
    void 잠금_대기_시간을_초과하면_503_비즈니스_오류로_변환한다() {
        registrationLock = new FcmTokenRegistrationLock(
                entityManager,
                Duration.ZERO,
                Duration.ZERO
        );
        when(query.getSingleResult()).thenReturn(false);

        assertThatThrownBy(() -> registrationLock.acquireDevice("device-A"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_FCM_TOKEN_LOCK_TIMEOUT);
    }
}

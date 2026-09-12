package com.ssafy.ssabangpalbang.auth.service;

import com.ssafy.ssabangpalbang.auth.dto.request.PasswordResetCodeRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.PasswordResetConfirmRequest;
import com.ssafy.ssabangpalbang.auth.dto.request.PasswordResetVerifyRequest;
import com.ssafy.ssabangpalbang.auth.event.PasswordResetCodeIssuedEvent;
import com.ssafy.ssabangpalbang.auth.event.PasswordResetCompletedEvent;
import com.ssafy.ssabangpalbang.auth.token.PasswordResetCodeStore;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Validator VALIDATOR = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordResetCodeStore passwordResetCodeStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(
                memberRepository,
                passwordEncoder,
                passwordResetCodeStore,
                eventPublisher,
                VALIDATOR
        );
    }

    @Test
    void 재설정_가능_회원이면_6자리_코드를_저장하고_발송_이벤트를_발행한다() {
        PasswordResetCodeRequest request =
                new PasswordResetCodeRequest(" Dain@Example.COM ");
        Member member = member(1L, "encoded-password", MemberStatus.ACTIVE);

        when(memberRepository.findByEmail("dain@example.com"))
                .thenReturn(Optional.of(member));
        when(passwordResetCodeStore.isWithinCooldown("dain@example.com"))
                .thenReturn(false);

        passwordResetService.requestPasswordReset(request);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(
                String.class
        );
        verify(passwordResetCodeStore).saveCode(
                eq("dain@example.com"),
                codeCaptor.capture(),
                eq(Duration.ofMinutes(10))
        );
        verify(passwordResetCodeStore).markCooldown(
                "dain@example.com",
                Duration.ofSeconds(60)
        );

        ArgumentCaptor<PasswordResetCodeIssuedEvent> eventCaptor =
                ArgumentCaptor.forClass(PasswordResetCodeIssuedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
        assertThat(eventCaptor.getValue().email())
                .isEqualTo("dain@example.com");
        assertThat(eventCaptor.getValue().code())
                .isEqualTo(codeCaptor.getValue());
    }

    @Test
    void 존재하지_않는_이메일이면_아무것도_하지_않고_넘어간다() {
        PasswordResetCodeRequest request =
                new PasswordResetCodeRequest("unknown@example.com");

        when(memberRepository.findByEmail("unknown@example.com"))
                .thenReturn(Optional.empty());

        passwordResetService.requestPasswordReset(request);

        verifyNoInteractions(passwordResetCodeStore, eventPublisher);
    }

    @Test
    void 소셜_전용_계정이면_코드를_발송하지_않는다() {
        PasswordResetCodeRequest request =
                new PasswordResetCodeRequest("social@example.com");
        Member member = member(1L, null, MemberStatus.ACTIVE);

        when(memberRepository.findByEmail("social@example.com"))
                .thenReturn(Optional.of(member));

        passwordResetService.requestPasswordReset(request);

        verifyNoInteractions(passwordResetCodeStore, eventPublisher);
    }

    @Test
    void 탈퇴_회원이면_코드를_발송하지_않는다() {
        PasswordResetCodeRequest request =
                new PasswordResetCodeRequest("withdrawn@example.com");
        Member member = member(1L, "encoded-password", MemberStatus.WITHDRAWN);

        when(memberRepository.findByEmail("withdrawn@example.com"))
                .thenReturn(Optional.of(member));

        passwordResetService.requestPasswordReset(request);

        verifyNoInteractions(passwordResetCodeStore, eventPublisher);
    }

    @Test
    void 쿨다운_중이면_재발송을_억제한다() {
        PasswordResetCodeRequest request =
                new PasswordResetCodeRequest("dain@example.com");
        Member member = member(1L, "encoded-password", MemberStatus.ACTIVE);

        when(memberRepository.findByEmail("dain@example.com"))
                .thenReturn(Optional.of(member));
        when(passwordResetCodeStore.isWithinCooldown("dain@example.com"))
                .thenReturn(true);

        passwordResetService.requestPasswordReset(request);

        verify(passwordResetCodeStore, never()).saveCode(any(), any(), any());
        verify(passwordResetCodeStore, never()).markCooldown(any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 코드가_일치하면_비밀번호를_갱신하고_완료_이벤트를_발행한다() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(
                " Dain@Example.COM ",
                "123456",
                "Ssafy5678"
        );
        Member member = member(1L, "old-encoded", MemberStatus.ACTIVE);

        when(passwordResetCodeStore.matches("dain@example.com", "123456"))
                .thenReturn(true);
        when(memberRepository.findByEmail("dain@example.com"))
                .thenReturn(Optional.of(member));
        when(passwordEncoder.encode("Ssafy5678"))
                .thenReturn("new-encoded");

        passwordResetService.confirmPasswordReset(request);

        assertThat(member.getPasswordHash()).isEqualTo("new-encoded");

        ArgumentCaptor<PasswordResetCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(PasswordResetCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().email())
                .isEqualTo("dain@example.com");
        assertThat(eventCaptor.getValue().memberId()).isEqualTo(1L);

        // 코드 삭제·세션 무효화는 커밋 이후 리스너가 처리하므로 서비스에서 직접 호출하지 않는다.
        verify(passwordResetCodeStore, never()).deleteCode(any());
    }

    @Test
    void 코드가_일치하지_않으면_실패_횟수를_누적하고_거절한다() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(
                "dain@example.com",
                "000000",
                "Ssafy5678"
        );

        when(passwordResetCodeStore.matches("dain@example.com", "000000"))
                .thenReturn(false);
        when(passwordResetCodeStore.recordFailedAttempt(
                "dain@example.com",
                Duration.ofMinutes(10)
        )).thenReturn(1L);

        assertThatThrownBy(() ->
                passwordResetService.confirmPasswordReset(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_PASSWORD_RESET_CODE_INVALID);

        verify(passwordResetCodeStore, never()).deleteCode(any());
        verifyNoInteractions(
                memberRepository,
                passwordEncoder,
                eventPublisher
        );
    }

    @Test
    void 실패_횟수가_임계치를_초과하면_코드를_폐기한다() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(
                "dain@example.com",
                "000000",
                "Ssafy5678"
        );

        when(passwordResetCodeStore.matches("dain@example.com", "000000"))
                .thenReturn(false);
        when(passwordResetCodeStore.recordFailedAttempt(
                "dain@example.com",
                Duration.ofMinutes(10)
        )).thenReturn(5L);

        assertThatThrownBy(() ->
                passwordResetService.confirmPasswordReset(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_PASSWORD_RESET_CODE_INVALID);

        verify(passwordResetCodeStore).deleteCode("dain@example.com");
        verifyNoInteractions(
                memberRepository,
                passwordEncoder,
                eventPublisher
        );
    }

    @Test
    void 코드는_유효하지만_회원이_없으면_코드_오류로_거절한다() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(
                "dain@example.com",
                "123456",
                "Ssafy5678"
        );

        when(passwordResetCodeStore.matches("dain@example.com", "123456"))
                .thenReturn(true);
        when(memberRepository.findByEmail("dain@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                passwordResetService.confirmPasswordReset(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_PASSWORD_RESET_CODE_INVALID);

        verifyNoInteractions(passwordEncoder, eventPublisher);
    }

    @Test
    void 소셜_전용_계정은_코드가_유효해도_재설정할_수_없다() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(
                "social@example.com",
                "123456",
                "Ssafy5678"
        );
        Member member = member(1L, null, MemberStatus.ACTIVE);

        when(passwordResetCodeStore.matches("social@example.com", "123456"))
                .thenReturn(true);
        when(memberRepository.findByEmail("social@example.com"))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() ->
                passwordResetService.confirmPasswordReset(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_PASSWORD_LOGIN_NOT_AVAILABLE);

        verifyNoInteractions(passwordEncoder, eventPublisher);
    }

    @Test
    void 탈퇴_회원은_코드가_유효해도_재설정할_수_없다() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(
                "withdrawn@example.com",
                "123456",
                "Ssafy5678"
        );
        Member member = member(1L, "old-encoded", MemberStatus.WITHDRAWN);

        when(passwordResetCodeStore.matches("withdrawn@example.com", "123456"))
                .thenReturn(true);
        when(memberRepository.findByEmail("withdrawn@example.com"))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() ->
                passwordResetService.confirmPasswordReset(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_MEMBER_WITHDRAWN);

        verifyNoInteractions(passwordEncoder, eventPublisher);
    }

    @Test
    void 새_비밀번호가_정책을_위반하면_400으로_거절한다() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(
                "dain@example.com",
                "123456",
                "short"
        );
        Member member = member(1L, "old-encoded", MemberStatus.ACTIVE);

        when(passwordResetCodeStore.matches("dain@example.com", "123456"))
                .thenReturn(true);
        when(memberRepository.findByEmail("dain@example.com"))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() ->
                passwordResetService.confirmPasswordReset(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                    assertThat(businessException.getData())
                            .containsEntry("field", "newPassword")
                            .containsEntry(
                                    "reason",
                                    "비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다."
                            );
                });

        assertThat(member.getPasswordHash()).isEqualTo("old-encoded");
        verifyNoInteractions(passwordEncoder, eventPublisher);
    }

    @Test
    void 코드가_일치하면_valid_true를_반환하고_카운터와_코드를_건드리지_않는다() {
        PasswordResetVerifyRequest request = new PasswordResetVerifyRequest(
                " Dain@Example.COM ",
                "123456"
        );

        when(passwordResetCodeStore.matches("dain@example.com", "123456"))
                .thenReturn(true);

        boolean valid = passwordResetService.verifyCode(request);

        assertThat(valid).isTrue();
        verify(passwordResetCodeStore, never())
                .recordFailedAttempt(any(), any());
        verify(passwordResetCodeStore, never()).deleteCode(any());
        verifyNoInteractions(memberRepository, passwordEncoder, eventPublisher);
    }

    @Test
    void 코드가_일치하지_않고_임계치_이내면_실패_횟수를_누적하고_valid_false를_반환한다() {
        PasswordResetVerifyRequest request = new PasswordResetVerifyRequest(
                "dain@example.com",
                "000000"
        );

        when(passwordResetCodeStore.matches("dain@example.com", "000000"))
                .thenReturn(false);
        when(passwordResetCodeStore.recordFailedAttempt(
                "dain@example.com",
                Duration.ofMinutes(10)
        )).thenReturn(1L);

        boolean valid = passwordResetService.verifyCode(request);

        assertThat(valid).isFalse();
        verify(passwordResetCodeStore, never()).deleteCode(any());
        verifyNoInteractions(memberRepository, passwordEncoder, eventPublisher);
    }

    @Test
    void 검증_실패_횟수가_임계치를_초과하면_코드를_폐기하고_거절한다() {
        PasswordResetVerifyRequest request = new PasswordResetVerifyRequest(
                "dain@example.com",
                "000000"
        );

        when(passwordResetCodeStore.matches("dain@example.com", "000000"))
                .thenReturn(false);
        when(passwordResetCodeStore.recordFailedAttempt(
                "dain@example.com",
                Duration.ofMinutes(10)
        )).thenReturn(5L);

        assertThatThrownBy(() ->
                passwordResetService.verifyCode(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_PASSWORD_RESET_CODE_INVALID);

        verify(passwordResetCodeStore).deleteCode("dain@example.com");
        verifyNoInteractions(memberRepository, passwordEncoder, eventPublisher);
    }

    private Member member(Long id, String passwordHash, MemberStatus status) {
        Member member = new Member(
                "dain@example.com",
                passwordHash,
                "루돌푸"
        );
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }
}

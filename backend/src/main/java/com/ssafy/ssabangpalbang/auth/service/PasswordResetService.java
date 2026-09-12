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
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;

/**
 * 이메일 기반 비밀번호 재설정(코드 발송 요청 + 코드 검증·재설정)을 담당한다.
 * 비로그인 공개 기능이므로 대상 회원은 요청 본문의 이메일로 식별하고,
 * confirm 단계에서는 코드 소유 증명이 인증을 대체한다.
 *
 * 이메일 발송과 커밋 이후 정리(코드 삭제·세션 무효화)는 이벤트로 분리해
 * 트랜잭션·요청 스레드 밖에서 처리한다.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int CODE_BOUND = 1_000_000;
    private static final long MAX_FAILED_ATTEMPTS = 5;

    private final SecureRandom secureRandom = new SecureRandom();

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetCodeStore passwordResetCodeStore;
    private final ApplicationEventPublisher eventPublisher;
    private final Validator validator;

    /**
     * 재설정 코드 발송을 요청한다. 계정 존재 여부를 노출하지 않기 위해 대상이 없거나
     * 소셜 전용·탈퇴·쿨다운이면 조용히 종료하고, 발송은 트랜잭션 밖에서 비동기로 처리한다.
     */
    public void requestPasswordReset(PasswordResetCodeRequest request) {
        String email = request.normalizedEmail();

        if (!isResettableMember(email)) {
            return;
        }
        if (passwordResetCodeStore.isWithinCooldown(email)) {
            return;
        }

        String code = generateCode();
        passwordResetCodeStore.saveCode(email, code, CODE_TTL);
        passwordResetCodeStore.markCooldown(email, RESEND_COOLDOWN);
        eventPublisher.publishEvent(
                new PasswordResetCodeIssuedEvent(email, code)
        );
    }

    private boolean isResettableMember(String email) {
        return memberRepository.findByEmail(email)
                .map(this::canResetPassword)
                .orElse(false);
    }

    /**
     * 코드를 검증하고 새 비밀번호로 갱신한다.
     * 무차별 대입 방어를 위해 코드 불일치 시 실패 횟수를 누적하고 임계치 초과 시 코드를 폐기한다.
     * 코드/시도 카운터 정리와 세션 무효화는 커밋 이후 이벤트 리스너가 처리한다.
     */
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        String email = request.normalizedEmail();

        if (!passwordResetCodeStore.matches(email, request.code())) {
            long attempts = passwordResetCodeStore.recordFailedAttempt(
                    email,
                    CODE_TTL
            );
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                // 임계치 초과 시 코드를 폐기해 이후 시도는 재요청을 유도한다.
                passwordResetCodeStore.deleteCode(email);
            }
            throw new BusinessException(
                    ErrorCode.AUTH_PASSWORD_RESET_CODE_INVALID
            );
        }

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTH_PASSWORD_RESET_CODE_INVALID
                ));
        if (member.getPasswordHash() == null) {
            throw new BusinessException(
                    ErrorCode.AUTH_PASSWORD_LOGIN_NOT_AVAILABLE
            );
        }
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.AUTH_MEMBER_WITHDRAWN);
        }

        validateNewPassword(request.newPassword());

        member.changePassword(passwordEncoder.encode(request.newPassword()));

        eventPublisher.publishEvent(
                new PasswordResetCompletedEvent(email, member.getId())
        );
    }

    /**
     * 재설정 코드가 유효한지만 검증한다(회원가입·재설정 UX의 사전 확인 단계).
     * 비밀번호를 변경하지 않고 코드도 소비(삭제)하지 않으므로 이후 confirm 호출을 방해하지 않는다.
     *
     * 무차별 대입 방어를 위해 confirm과 동일한 시도 카운터를 공유한다. 코드가 일치하지 않으면
     * 실패 횟수를 누적하고, 임계치를 초과하면 confirm과 동일하게 코드를 폐기한 뒤
     * {@code AUTH_PASSWORD_RESET_CODE_INVALID}로 거절한다. 코드가 일치하면 카운터는 건드리지 않는다.
     * 대상 이메일 존재 여부에 따라 동작이 갈리지 않도록 코드 저장소(해시)만 조회한다.
     *
     * @return 코드가 유효하면 {@code true}, 임계치 이내의 불일치면 {@code false}
     */
    public boolean verifyCode(PasswordResetVerifyRequest request) {
        String email = request.normalizedEmail();

        if (passwordResetCodeStore.matches(email, request.code())) {
            return true;
        }

        long attempts = passwordResetCodeStore.recordFailedAttempt(
                email,
                CODE_TTL
        );
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            // 임계치 초과 시 confirm과 동일하게 코드를 폐기해 재요청을 유도한다.
            passwordResetCodeStore.deleteCode(email);
            throw new BusinessException(
                    ErrorCode.AUTH_PASSWORD_RESET_CODE_INVALID
            );
        }
        return false;
    }

    private boolean canResetPassword(Member member) {
        return member.getPasswordHash() != null
                && member.getStatus() == MemberStatus.ACTIVE;
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(CODE_BOUND));
    }

    private void validateNewPassword(String newPassword) {
        var violation = validator.validate(new NewPassword(newPassword))
                .stream()
                .min(Comparator.comparing(
                        constraint -> constraint.getMessage()
                ));
        if (violation.isPresent()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    Map.of(
                            "field",
                            "newPassword",
                            "reason",
                            violation.get().getMessage()
                    )
            );
        }
    }

    private record NewPassword(
            @NotBlank(
                    message = "비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다."
            )
            @Pattern(
                    regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",
                    message = "비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다."
            )
            String value
    ) {
    }
}

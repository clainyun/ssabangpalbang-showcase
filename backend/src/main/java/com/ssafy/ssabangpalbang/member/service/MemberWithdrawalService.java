package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.auth.token.JwtTokenProvider;
import com.ssafy.ssabangpalbang.auth.token.RefreshTokenStore;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.request.MemberWithdrawalRequest;
import com.ssafy.ssabangpalbang.member.dto.response.MemberWithdrawalResponse;
import com.ssafy.ssabangpalbang.member.repository.FcmTokenRepository;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.StudyApplicationStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberRole;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyApplicationRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {

    private static final String CONFIRMATION_TEXT = "회원탈퇴";

    private final MemberRepository memberRepository;
    private final StudyRepository studyRepository;
    private final StudyApplicationRepository studyApplicationRepository;
    private final StudyMemberRepository studyMemberRepository;
    private final FieldParticipantRepository fieldParticipantRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final Clock clock;

    @Transactional
    public MemberWithdrawalResponse withdraw(
            Long memberId,
            MemberWithdrawalRequest request
    ) {
        validateConfirmationText(request.confirmationText());
        validateRefreshTokenOwner(memberId, request.refreshToken());

        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
        if (member.getStatus() == MemberStatus.WITHDRAWN
                || member.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.MEMBER_ALREADY_WITHDRAWN);
        }

        validateNoInProgressFieldParticipation(memberId);
        validateNoBlockingLeaderStudy(memberId);

        Instant withdrawnAt = clock.instant();
        studyApplicationRepository
                .findAllForUpdateByApplicantIdAndStatus(
                        memberId,
                        StudyApplicationStatus.PENDING
                )
                .forEach(application -> application.reject(withdrawnAt));
        studyMemberRepository
                .findAllForUpdateByMemberIdAndRoleAndStatus(
                        memberId,
                        StudyMemberRole.MEMBER,
                        StudyMemberStatus.ACTIVE
                )
                .forEach(studyMember -> studyMember.remove(withdrawnAt));

        member.withdraw(withdrawnAt);
        fcmTokenRepository.deleteAllByMemberId(memberId);
        memberRepository.flush();
        refreshTokenStore.revokeAll(memberId);

        return MemberWithdrawalResponse.of(memberId, withdrawnAt);
    }

    private void validateConfirmationText(String confirmationText) {
        if (!CONFIRMATION_TEXT.equals(confirmationText)) {
            throw new BusinessException(
                    ErrorCode.MEMBER_WITHDRAW_CONFIRMATION_INVALID,
                    Map.of(
                            "field", "confirmationText",
                            "expectedValue", CONFIRMATION_TEXT
                    )
            );
        }
    }

    private void validateRefreshTokenOwner(
            Long memberId,
            String refreshToken
    ) {
        Long refreshTokenMemberId = jwtTokenProvider.parseRefreshToken(
                refreshToken
        );
        if (!memberId.equals(refreshTokenMemberId)) {
            throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }
    }

    private void validateNoInProgressFieldParticipation(Long memberId) {
        fieldParticipantRepository.findInProgressParticipation(memberId)
                .ifPresent(participation -> {
                    throw new BusinessException(
                            ErrorCode.MEMBER_WITHDRAWAL_FIELD_SESSION_IN_PROGRESS,
                            Map.of(
                                    "studyId", participation.getStudyId(),
                                    "sessionId", participation.getSessionId()
                            )
                    );
                });
    }

    private void validateNoBlockingLeaderStudy(Long memberId) {
        List<Long> studyIds = studyRepository.findBlockingLeaderStudyIds(
                memberId
        );
        if (!studyIds.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.MEMBER_WITHDRAWAL_ACTIVE_STUDY_LEADER,
                    Map.of("studyIds", studyIds)
            );
        }
    }
}

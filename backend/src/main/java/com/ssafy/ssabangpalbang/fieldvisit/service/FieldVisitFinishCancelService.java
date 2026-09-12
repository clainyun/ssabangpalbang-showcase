package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitFinishCancelRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitFinishCancelResponse;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldSessionRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 개인 임장 종료 취소(다시 진행 중으로) 처리다.
 *
 * <p>본인이 직접 종료(SELF_ENDED)했고 세션이 아직 IN_PROGRESS일 때에만 참여를
 * 다시 진행 중으로 되돌린다. 세션이 이미 종료되었다면 리포트 생성이 시작되었으므로
 * 되돌릴 수 없다.</p>
 */
@Service
@RequiredArgsConstructor
public class FieldVisitFinishCancelService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String SELF_ENDED = "SELF_ENDED";

    private final FieldVisitAccessService accessService;
    private final FieldSessionRepository fieldSessionRepository;
    private final FieldParticipantRepository fieldParticipantRepository;

    @Transactional
    public FinishCancelResult cancel(
            Long studyId,
            Long memberId,
            FieldVisitFinishCancelRequestBody request
    ) {
        accessService.requireStudy(studyId);
        accessService.requireActiveStudyMember(
                studyId,
                memberId,
                ErrorCode.FIELD_VISIT_FINISH_CANCEL_FORBIDDEN
        );

        FieldSession session = fieldSessionRepository
                .findByStudyIdForUpdate(studyId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FIELD_VISIT_NOT_FOUND
                ));
        FieldParticipant participant = fieldParticipantRepository
                .findBySessionIdAndMemberIdForUpdate(session.getId(), memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FIELD_VISIT_FINISH_CANCEL_FORBIDDEN
                ));

        if (session.getStatus() == FieldSessionStatus.ENDED) {
            // 세션 종료 = 리포트 생성이 시작된 상태이므로 되돌릴 수 없다.
            throw new BusinessException(ErrorCode.FIELD_VISIT_ALREADY_ENDED);
        }
        if (participant.getStatus() == FieldParticipantStatus.IN_PROGRESS) {
            return new FinishCancelResult(
                    FieldVisitResponseCode.FIELD_PARTICIPANT_ALREADY_IN_PROGRESS,
                    toResponse(studyId, session, participant)
            );
        }
        if (!SELF_ENDED.equals(participant.getEndReason())) {
            // 스터디장 강제 종료·과반수 종료는 본인이 되돌릴 수 없다.
            throw new BusinessException(
                    ErrorCode.FIELD_VISIT_FINISH_CANCEL_NOT_ALLOWED
            );
        }

        participant.reopenFromSelfEnded();
        fieldParticipantRepository.save(participant);

        return new FinishCancelResult(
                FieldVisitResponseCode.FIELD_VISIT_FINISH_CANCEL_SUCCESS,
                toResponse(studyId, session, participant)
        );
    }

    private FieldVisitFinishCancelResponse toResponse(
            Long studyId,
            FieldSession session,
            FieldParticipant participant
    ) {
        return new FieldVisitFinishCancelResponse(
                studyId,
                session.getId(),
                new FieldVisitFinishCancelResponse.ParticipantBody(
                        participant.getId(),
                        participant.getStatus().name(),
                        toSeoul(participant.getStartedAt()),
                        toSeoul(participant.getEndedAt()),
                        participant.getEndReason(),
                        participant.getStayDurationSec()
                )
        );
    }

    private static OffsetDateTime toSeoul(java.time.Instant instant) {
        return instant == null ? null : instant.atZone(SEOUL).toOffsetDateTime();
    }

    public record FinishCancelResult(
            FieldVisitResponseCode responseCode,
            FieldVisitFinishCancelResponse body
    ) {
    }
}

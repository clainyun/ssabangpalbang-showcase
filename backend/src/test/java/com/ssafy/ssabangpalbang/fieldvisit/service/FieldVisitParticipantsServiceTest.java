package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitCandidate;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.response.FieldVisitParticipantsResponse;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldSessionRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldVisitCandidateRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FieldVisitParticipantsServiceTest {

    private static final Long STUDY_ID = 7L;
    private static final Long LEADER_ID = 42L;
    private static final Long SESSION_ID = 100L;
    private static final Instant NOW = Instant.parse("2026-06-17T09:00:00Z");

    @Mock private StudyRepository studyRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private StudyMemberRepository studyMemberRepository;
    @Mock private FieldSessionRepository fieldSessionRepository;
    @Mock private FieldVisitCandidateRepository fieldVisitCandidateRepository;
    @Mock private FieldParticipantRepository fieldParticipantRepository;
    @Mock private Clock clock;

    @InjectMocks
    private FieldVisitParticipantsService service;

    private Study study;

    @BeforeEach
    void setUp() {
        study = Study.create(25L, LEADER_ID, "t", null, "g", 4, null);
        ReflectionTestUtils.setField(study, "id", STUDY_ID);
        when(clock.instant()).thenReturn(NOW);
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        when(memberRepository.findById(LEADER_ID))
                .thenReturn(Optional.of(member(LEADER_ID, "루돌푸", null, "PALBANG")));
        when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, LEADER_ID))
                .thenReturn(Optional.of(StudyMember.createLeader(STUDY_ID, LEADER_ID)));
    }

    @Test
    void 세션_없으면_NOT_STARTED이고_빈_목록에_count는_0이다() {
        when(fieldSessionRepository.findByStudyId(STUDY_ID)).thenReturn(Optional.empty());

        FieldVisitParticipantsService.ParticipantsResult result =
                service.getParticipants(STUDY_ID, LEADER_ID);

        assertThat(result.responseCode())
                .isEqualTo(FieldVisitResponseCode.FIELD_VISIT_PARTICIPANTS_SUCCESS);
        FieldVisitParticipantsResponse body = result.body();
        assertThat(body.status()).isEqualTo("NOT_STARTED");
        assertThat(body.sessionId()).isNull();
        assertThat(body.participantCount()).isZero();
        assertThat(body.inProgressCount()).isZero();
        assertThat(body.endedCount()).isZero();
        assertThat(body.notJoinedCount()).isZero();
        assertThat(body.participants()).isEmpty();
    }

    @Test
    void 진행중_세션의_혼합_상태를_정렬_count_권한과_함께_반환한다() {
        FieldSession session = FieldSession.start(STUDY_ID, NOW.minusSeconds(600));
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        when(fieldSessionRepository.findByStudyId(STUDY_ID))
                .thenReturn(Optional.of(session));

        // 고정 후보 5명 (memberId: 42 리더, 51, 63, 72, 80)
        when(fieldVisitCandidateRepository.findBySessionId(SESSION_ID))
                .thenReturn(List.of(
                        candidate(80L),
                        candidate(72L),
                        candidate(63L),
                        candidate(51L),
                        candidate(LEADER_ID)
                ));

        FieldParticipant leader = participantInProgress(LEADER_ID, NOW.minusSeconds(500));
        FieldParticipant selfEnded = participantEndedSelf(51L, NOW.minusSeconds(400), 2340);
        FieldParticipant sessionEnded =
                participantEndedMajority(63L, NOW.minusSeconds(300), 3000);
        FieldParticipant inProgress = participantInProgress(72L, NOW.minusSeconds(100));
        when(fieldParticipantRepository.findBySessionId(SESSION_ID))
                .thenReturn(List.of(inProgress, sessionEnded, selfEnded, leader));

        when(memberRepository.findAllById(any())).thenReturn(List.of(
                member(LEADER_ID, "루돌푸", null, "PALBANG"),
                member(51L, "집콩이", "https://cdn.example.com/51.jpg", "JIPKONG"),
                member(63L, "성동구탐방러", null, "DURI"),
                member(72L, "옥수현장", null, "PALBANG"),
                member(80L, "옥수초보", null, "PALBANG")
        ));

        FieldVisitParticipantsService.ParticipantsResult result =
                service.getParticipants(STUDY_ID, LEADER_ID);
        FieldVisitParticipantsResponse body = result.body();

        assertThat(body.status()).isEqualTo("IN_PROGRESS");
        assertThat(body.sessionId()).isEqualTo(SESSION_ID);
        assertThat(body.participantCount()).isEqualTo(5);
        assertThat(body.inProgressCount()).isEqualTo(2);
        assertThat(body.endedCount()).isEqualTo(2);
        assertThat(body.notJoinedCount()).isEqualTo(1);

        // 정렬: 리더 → 시작한 참여자 startedAt 오름차순 → NOT_JOINED
        assertThat(body.participants())
                .extracting(FieldVisitParticipantsResponse.ParticipantBody::memberId)
                .containsExactly(LEADER_ID, 51L, 63L, 72L, 80L);

        FieldVisitParticipantsResponse.ParticipantBody leaderRow = body.participants().get(0);
        assertThat(leaderRow.role()).isEqualTo("LEADER");
        assertThat(leaderRow.isLeader()).isTrue();
        assertThat(leaderRow.isMe()).isTrue();
        assertThat(leaderRow.status()).isEqualTo("IN_PROGRESS");
        assertThat(leaderRow.stayDurationSec()).isEqualTo(500);
        assertThat(leaderRow.canRequestFinish()).isFalse(); // 본인 항목은 false

        FieldVisitParticipantsResponse.ParticipantBody selfEndedRow = body.participants().get(1);
        assertThat(selfEndedRow.status()).isEqualTo("ENDED");
        assertThat(selfEndedRow.endReason()).isEqualTo("SELF_ENDED");
        assertThat(selfEndedRow.stayDurationSec()).isEqualTo(2340);
        assertThat(selfEndedRow.profileImageUrl()).isEqualTo("https://cdn.example.com/51.jpg");
        assertThat(selfEndedRow.canRequestFinish()).isFalse();

        FieldVisitParticipantsResponse.ParticipantBody sessionEndedRow = body.participants().get(2);
        assertThat(sessionEndedRow.status()).isEqualTo("ENDED");
        assertThat(sessionEndedRow.endReason()).isEqualTo("SESSION_ENDED"); // MAJORITY_FORCED 매핑
        assertThat(sessionEndedRow.stayDurationSec()).isEqualTo(3000);

        FieldVisitParticipantsResponse.ParticipantBody inProgressRow = body.participants().get(3);
        assertThat(inProgressRow.status()).isEqualTo("IN_PROGRESS");
        assertThat(inProgressRow.role()).isEqualTo("MEMBER");
        assertThat(inProgressRow.stayDurationSec()).isEqualTo(100);
        // 리더가 조회 중이고 세션 진행 중, 본인 아님 → 종료 요청 가능
        assertThat(inProgressRow.canRequestFinish()).isTrue();

        FieldVisitParticipantsResponse.ParticipantBody notJoinedRow = body.participants().get(4);
        assertThat(notJoinedRow.status()).isEqualTo("NOT_JOINED");
        assertThat(notJoinedRow.participantId()).isNull();
        assertThat(notJoinedRow.startedAt()).isNull();
        assertThat(notJoinedRow.endedAt()).isNull();
        assertThat(notJoinedRow.endReason()).isNull();
        assertThat(notJoinedRow.stayDurationSec()).isZero();
        assertThat(notJoinedRow.canRequestFinish()).isFalse();
    }

    @Test
    void 세션_종료_상태에서는_canRequestFinish가_모두_false다() {
        FieldSession session = FieldSession.start(STUDY_ID, NOW.minusSeconds(600));
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        ReflectionTestUtils.setField(session, "status", FieldSessionStatus.ENDED);
        ReflectionTestUtils.setField(session, "endedAt", NOW.minusSeconds(10));
        when(fieldSessionRepository.findByStudyId(STUDY_ID))
                .thenReturn(Optional.of(session));
        when(fieldVisitCandidateRepository.findBySessionId(SESSION_ID))
                .thenReturn(List.of(candidate(LEADER_ID), candidate(51L)));
        FieldParticipant inProgress = participantInProgress(51L, NOW.minusSeconds(300));
        when(fieldParticipantRepository.findBySessionId(SESSION_ID))
                .thenReturn(List.of(inProgress));
        when(memberRepository.findAllById(any())).thenReturn(List.of(
                member(LEADER_ID, "루돌푸", null, "PALBANG"),
                member(51L, "집콩이", null, "JIPKONG")
        ));

        FieldVisitParticipantsResponse body =
                service.getParticipants(STUDY_ID, LEADER_ID).body();

        assertThat(body.status()).isEqualTo("ENDED");
        assertThat(body.participants())
                .allSatisfy(p -> assertThat(p.canRequestFinish()).isFalse());
    }

    @Test
    void 비승인_회원은_PARTICIPANTS_ACCESS_DENIED다() {
        Long strangerId = 999L;
        when(memberRepository.findById(strangerId))
                .thenReturn(Optional.of(member(strangerId, "낯선이", null, "PALBANG")));
        when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, strangerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getParticipants(STUDY_ID, strangerId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_PARTICIPANTS_ACCESS_DENIED);
    }

    @Test
    void 탈퇴_계정은_PARTICIPANTS_ACCESS_DENIED다() {
        Member withdrawn = member(LEADER_ID, "루돌푸", null, "PALBANG");
        withdrawn.withdraw(NOW);
        when(memberRepository.findById(LEADER_ID)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.getParticipants(STUDY_ID, LEADER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_PARTICIPANTS_ACCESS_DENIED);
    }

    private static FieldVisitCandidate candidate(Long memberId) {
        return FieldVisitCandidate.create(SESSION_ID, memberId, NOW.minusSeconds(600));
    }

    private static FieldParticipant participantInProgress(Long memberId, Instant startedAt) {
        FieldParticipant participant = FieldParticipant.start(SESSION_ID, memberId, startedAt);
        ReflectionTestUtils.setField(participant, "id", 300L + memberId);
        return participant;
    }

    private static FieldParticipant participantEndedSelf(
            Long memberId, Instant startedAt, int stayDurationSec
    ) {
        FieldParticipant participant = FieldParticipant.start(SESSION_ID, memberId, startedAt);
        ReflectionTestUtils.setField(participant, "id", 300L + memberId);
        participant.endBySelf(startedAt.plusSeconds(stayDurationSec), stayDurationSec);
        return participant;
    }

    private static FieldParticipant participantEndedMajority(
            Long memberId, Instant startedAt, int stayDurationSec
    ) {
        FieldParticipant participant = FieldParticipant.start(SESSION_ID, memberId, startedAt);
        ReflectionTestUtils.setField(participant, "id", 300L + memberId);
        participant.endByMajority(startedAt.plusSeconds(stayDurationSec), stayDurationSec);
        return participant;
    }

    private static Member member(
            Long id, String nickname, String profileImageUrl, String characterId
    ) {
        Member member = new Member(id + "@example.com", "hash", nickname);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "profileImageUrl", profileImageUrl);
        ReflectionTestUtils.setField(member, "selectedCharacterId", characterId);
        return member;
    }
}

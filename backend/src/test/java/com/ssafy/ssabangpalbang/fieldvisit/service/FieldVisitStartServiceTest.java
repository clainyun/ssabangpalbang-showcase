package com.ssafy.ssabangpalbang.fieldvisit.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.fieldvisit.config.FieldVisitStartProperties;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipant;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSession;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldVisitStartRequest;
import com.ssafy.ssabangpalbang.fieldvisit.dto.FieldVisitResponseCode;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitStartRequestBody;
import com.ssafy.ssabangpalbang.fieldvisit.integration.FieldVisitSessionStartedEvent;
import com.ssafy.ssabangpalbang.fieldvisit.repository.ChecklistRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldVisitCandidateRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldSessionRepository;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldVisitStartRequestRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Schedule;
import com.ssafy.ssabangpalbang.study.domain.ScheduleStatus;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.ScheduleRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FieldVisitStartServiceTest {

    private static final Long STUDY_ID = 7L;
    private static final Long MEMBER_ID = 42L;
    private static final Long OTHER_MEMBER_ID = 43L;
    private static final Long APARTMENT_ID = 25L;
    private static final Instant NOW = Instant.parse("2026-06-17T09:00:00Z");
    private static final String CLIENT_REQUEST_ID =
            "a1b2c3d4-1234-5678-90ab-cdef12345678";

    @Mock private StudyRepository studyRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private StudyMemberRepository studyMemberRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private ApartmentRepository apartmentRepository;
    @Mock private FieldSessionRepository fieldSessionRepository;
    @Mock private FieldVisitCandidateRepository fieldVisitCandidateRepository;
    @Mock private FieldParticipantRepository fieldParticipantRepository;
    @Mock private FieldVisitStartRequestRepository startRequestRepository;
    @Mock private ChecklistRepository checklistRepository;
    @Mock private FieldVisitStartProperties startProperties;
    @Mock private Clock clock;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FieldVisitStartService service;

    private Study study;
    private Apartment apartment;

    @BeforeEach
    void setUp() {
        study = Study.create(APARTMENT_ID, MEMBER_ID, "t", null, "g", 4, null);
        ReflectionTestUtils.setField(study, "id", STUDY_ID);
        study.closeRecruitment(NOW);
        apartment = Apartment.create(
                "C1", "apt", "addr", null, null, null, null,
                127.0842, 37.5133, null, null, null
        );
        ReflectionTestUtils.setField(apartment, "id", APARTMENT_ID);
        when(startProperties.allowedRadiusMeters()).thenReturn(1000);
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void 최초_시작은_201과_세션_참여자를_생성한다() {
        stubMemberActive();
        StudyMember otherMember = activeStudyMember(OTHER_MEMBER_ID);
        when(studyMemberRepository.findByStudyIdAndStatus(
                STUDY_ID, StudyMemberStatus.ACTIVE
        )).thenReturn(List.of(activeStudyMember(MEMBER_ID), otherMember));
        when(memberRepository.findAllByIdForNoKeyUpdate(
                List.of(MEMBER_ID, OTHER_MEMBER_ID)
        )).thenReturn(List.of(activeMember(), activeMember(OTHER_MEMBER_ID)));
        when(startRequestRepository.findByMemberIdAndClientRequestId(MEMBER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.empty());
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.empty());
        stubScheduleReached();
        when(apartmentRepository.findById(APARTMENT_ID)).thenReturn(Optional.of(apartment));

        FieldSession savedSession = FieldSession.start(STUDY_ID, NOW);
        ReflectionTestUtils.setField(savedSession, "id", 100L);
        when(fieldSessionRepository.save(any())).thenReturn(savedSession);

        FieldParticipant savedParticipant = FieldParticipant.start(100L, MEMBER_ID, NOW);
        ReflectionTestUtils.setField(savedParticipant, "id", 301L);
        when(fieldParticipantRepository.save(any())).thenReturn(savedParticipant);
        when(startRequestRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        FieldVisitStartService.StartResult result = service.start(
                STUDY_ID, MEMBER_ID, body(37.5133, 127.0842)
        );

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.responseCode()).isEqualTo(FieldVisitResponseCode.FIELD_VISIT_START_SUCCESS);
        assertThat(study.getStatus()).isEqualTo(StudyStatus.IN_PROGRESS);
        assertThat(result.body().session().sessionId()).isEqualTo(100L);
        assertThat(result.body().participant().participantId()).isEqualTo(301L);
        assertThat(result.body().checklistGenerated()).isFalse();
        verify(fieldVisitCandidateRepository).saveAll(anyList());
        verify(startRequestRepository).saveAndFlush(any(FieldVisitStartRequest.class));
        ArgumentCaptor<FieldVisitSessionStartedEvent> eventCaptor =
                ArgumentCaptor.forClass(FieldVisitSessionStartedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().sessionId()).isEqualTo(100L);
        assertThat(eventCaptor.getValue().starterId()).isEqualTo(MEMBER_ID);
        assertThat(eventCaptor.getValue().recipientIds())
                .containsExactly(OTHER_MEMBER_ID);
    }

    @Test
    void 반경_밖이면_422이고_세션을_만들지_않는다() {
        stubMemberActive();
        when(startRequestRepository.findByMemberIdAndClientRequestId(MEMBER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.empty());
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.empty());
        stubScheduleReached();
        when(apartmentRepository.findById(APARTMENT_ID)).thenReturn(Optional.of(apartment));

        assertThatThrownBy(() -> service.start(
                STUDY_ID, MEMBER_ID, body(37.5400, 127.0842)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(ErrorCode.FIELD_VISIT_OUT_OF_RANGE);
                    assertThat(businessException.getMessage())
                            .isEqualTo("임장 지역이 아닙니다.");
                    assertThat(businessException.getData())
                            .containsEntry("allowedRadiusMeters", 1000);
                });

        verify(fieldSessionRepository, never()).save(any());
        verify(fieldParticipantRepository, never()).save(any());
        verify(startRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void 약속_시각_1초_전이면_409이고_세션을_만들지_않는다() {
        stubMemberActive();
        when(startRequestRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID, CLIENT_REQUEST_ID
        )).thenReturn(Optional.empty());
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.empty());
        when(scheduleRepository.findByStudyIdAndStatus(STUDY_ID, ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.of(Schedule.create(
                        STUDY_ID,
                        NOW.plusSeconds(1),
                        NOW.plusSeconds(3600),
                        "gate"
                )));

        assertThatThrownBy(() -> service.start(
                STUDY_ID, MEMBER_ID, body(37.5133, 127.0842)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode())
                            .isEqualTo(ErrorCode.FIELD_VISIT_START_NOT_AVAILABLE);
                    assertThat(businessException.getMessage())
                            .isEqualTo("임장 시작 시간이 아닙니다.");
                });

        verify(fieldSessionRepository, never()).save(any());
        verify(fieldParticipantRepository, never()).save(any());
        verify(fieldVisitCandidateRepository, never()).saveAll(anyList());
        verify(startRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void 일정이_없고_즉시_진행을_확인하지_않으면_기존처럼_거부한다() {
        stubFirstStartRequest();

        assertThatThrownBy(() -> service.start(
                STUDY_ID, MEMBER_ID, body(37.5133, 127.0842)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_START_FORBIDDEN);

        verify(scheduleRepository, never()).save(any());
        verify(fieldSessionRepository, never()).save(any());
    }

    @Test
    void 일정이_없고_즉시_진행을_확인하면_현재_시각_일정을_만들고_시작한다() {
        stubFirstStartRequest();
        when(scheduleRepository.findByStudyId(STUDY_ID))
                .thenReturn(Optional.empty());
        when(scheduleRepository.save(any(Schedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubSuccessfulFirstStartPersistence();

        FieldVisitStartService.StartResult result = service.start(
                STUDY_ID, MEMBER_ID, confirmedBody(37.5133, 127.0842)
        );

        ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository).save(scheduleCaptor.capture());
        assertThat(scheduleCaptor.getValue().getStartAt()).isEqualTo(NOW);
        assertThat(scheduleCaptor.getValue().getStatus()).isEqualTo(ScheduleStatus.SCHEDULED);
        assertThat(result.httpStatus()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void 약속_시각_전이고_즉시_진행을_확인하면_일정을_현재로_당기고_시작한다() {
        stubFirstStartRequest();
        Schedule schedule = Schedule.create(
                STUDY_ID,
                NOW.plusSeconds(3600),
                NOW.plusSeconds(7200),
                "gate"
        );
        when(scheduleRepository.findByStudyIdAndStatus(
                STUDY_ID, ScheduleStatus.SCHEDULED
        )).thenReturn(Optional.of(schedule));
        stubSuccessfulFirstStartPersistence();

        FieldVisitStartService.StartResult result = service.start(
                STUDY_ID, MEMBER_ID, confirmedBody(37.5133, 127.0842)
        );

        assertThat(schedule.getStartAt()).isEqualTo(NOW);
        assertThat(schedule.getEndAt()).isEqualTo(NOW.plusSeconds(7200));
        assertThat(result.httpStatus()).isEqualTo(HttpStatus.CREATED);
        verify(scheduleRepository).flush();
    }

    @Test
    void 취소된_일정에서_즉시_진행을_확인하면_현재_시각으로_다시_활성화한다() {
        stubFirstStartRequest();
        Schedule canceledSchedule = Schedule.create(
                STUDY_ID,
                NOW.minusSeconds(3600),
                NOW,
                "gate"
        );
        canceledSchedule.cancel();
        when(scheduleRepository.findByStudyIdAndStatus(
                STUDY_ID, ScheduleStatus.SCHEDULED
        )).thenReturn(Optional.empty());
        when(scheduleRepository.findByStudyId(STUDY_ID))
                .thenReturn(Optional.of(canceledSchedule));
        stubSuccessfulFirstStartPersistence();

        FieldVisitStartService.StartResult result = service.start(
                STUDY_ID, MEMBER_ID, confirmedBody(37.5133, 127.0842)
        );

        assertThat(canceledSchedule.getStatus()).isEqualTo(ScheduleStatus.SCHEDULED);
        assertThat(canceledSchedule.getStartAt()).isEqualTo(NOW);
        assertThat(canceledSchedule.getEndAt()).isNull();
        assertThat(canceledSchedule.getMeetingPlace()).isEqualTo("gate");
        assertThat(result.httpStatus()).isEqualTo(HttpStatus.CREATED);
        verify(scheduleRepository).flush();
    }

    @Test
    void 진행중_세션의_기존_참여자도_확인하면_미래_일정을_현재로_당긴다() {
        ReflectionTestUtils.setField(study, "status", StudyStatus.IN_PROGRESS);
        stubMemberActive();
        when(startRequestRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID, CLIENT_REQUEST_ID
        )).thenReturn(Optional.empty());
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));

        FieldSession session = FieldSession.start(STUDY_ID, NOW);
        ReflectionTestUtils.setField(session, "id", 100L);
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));
        FieldParticipant participant = FieldParticipant.start(100L, MEMBER_ID, NOW);
        ReflectionTestUtils.setField(participant, "id", 301L);
        when(fieldParticipantRepository.findBySessionIdAndMemberIdForUpdate(
                100L, MEMBER_ID
        )).thenReturn(Optional.of(participant));
        Schedule schedule = Schedule.create(
                STUDY_ID,
                NOW.plusSeconds(3600),
                NOW.plusSeconds(7200),
                "gate"
        );
        when(scheduleRepository.findByStudyIdAndStatus(
                STUDY_ID, ScheduleStatus.SCHEDULED
        )).thenReturn(Optional.of(schedule));
        when(apartmentRepository.findById(APARTMENT_ID))
                .thenReturn(Optional.of(apartment));
        when(checklistRepository.findBySessionIdAndMemberId(100L, MEMBER_ID))
                .thenReturn(Optional.empty());
        when(startRequestRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FieldVisitStartService.StartResult result = service.start(
                STUDY_ID, MEMBER_ID, confirmedBody(37.5133, 127.0842)
        );

        assertThat(schedule.getStartAt()).isEqualTo(NOW);
        assertThat(result.httpStatus()).isEqualTo(HttpStatus.OK);
        assertThat(result.responseCode())
                .isEqualTo(FieldVisitResponseCode.FIELD_VISIT_ALREADY_STARTED);
        verify(scheduleRepository).flush();
        verify(fieldParticipantRepository, never()).save(any());
    }

    @Test
    void 진행중_세션의_고정_후보도_확인하면_미래_일정을_현재로_당기고_참여한다() {
        ReflectionTestUtils.setField(study, "status", StudyStatus.IN_PROGRESS);
        stubMemberActive();
        when(startRequestRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID, CLIENT_REQUEST_ID
        )).thenReturn(Optional.empty());
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));

        FieldSession session = FieldSession.start(STUDY_ID, NOW);
        ReflectionTestUtils.setField(session, "id", 100L);
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberIdForUpdate(
                100L, MEMBER_ID
        )).thenReturn(Optional.empty());
        when(fieldVisitCandidateRepository.existsBySessionIdAndMemberId(
                100L, MEMBER_ID
        )).thenReturn(true);
        Schedule schedule = Schedule.create(
                STUDY_ID,
                NOW.plusSeconds(3600),
                NOW.plusSeconds(7200),
                "gate"
        );
        when(scheduleRepository.findByStudyIdAndStatus(
                STUDY_ID, ScheduleStatus.SCHEDULED
        )).thenReturn(Optional.of(schedule));
        when(apartmentRepository.findById(APARTMENT_ID))
                .thenReturn(Optional.of(apartment));
        FieldParticipant created = FieldParticipant.start(100L, MEMBER_ID, NOW);
        ReflectionTestUtils.setField(created, "id", 302L);
        when(fieldParticipantRepository.save(any())).thenReturn(created);
        when(checklistRepository.findBySessionIdAndMemberId(100L, MEMBER_ID))
                .thenReturn(Optional.empty());
        when(startRequestRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FieldVisitStartService.StartResult result = service.start(
                STUDY_ID, MEMBER_ID, confirmedBody(37.5133, 127.0842)
        );

        assertThat(schedule.getStartAt()).isEqualTo(NOW);
        assertThat(result.httpStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.body().participant().participantId()).isEqualTo(302L);
        verify(scheduleRepository).flush();
        verify(fieldParticipantRepository).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 약속_시각_정각이면_시작한다() {
        stubMemberActive();
        when(startRequestRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID, CLIENT_REQUEST_ID
        )).thenReturn(Optional.empty());
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.empty());
        when(scheduleRepository.findByStudyIdAndStatus(STUDY_ID, ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.of(Schedule.create(
                        STUDY_ID, NOW, NOW.plusSeconds(3600), "gate"
                )));
        when(apartmentRepository.findById(APARTMENT_ID)).thenReturn(Optional.of(apartment));
        FieldSession savedSession = FieldSession.start(STUDY_ID, NOW);
        ReflectionTestUtils.setField(savedSession, "id", 100L);
        when(fieldSessionRepository.save(any())).thenReturn(savedSession);
        FieldParticipant savedParticipant = FieldParticipant.start(100L, MEMBER_ID, NOW);
        ReflectionTestUtils.setField(savedParticipant, "id", 301L);
        when(fieldParticipantRepository.save(any())).thenReturn(savedParticipant);
        when(startRequestRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        FieldVisitStartService.StartResult result = service.start(
                STUDY_ID, MEMBER_ID, body(37.5133, 127.0842)
        );

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.body().allowedRadiusMeters()).isEqualTo(1000);
    }

    @Test
    void 약속_시각_1초_후면_시작한다() {
        stubMemberActive();
        when(startRequestRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID, CLIENT_REQUEST_ID
        )).thenReturn(Optional.empty());
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.empty());
        when(scheduleRepository.findByStudyIdAndStatus(STUDY_ID, ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.of(Schedule.create(
                        STUDY_ID,
                        NOW.minusSeconds(1),
                        NOW.plusSeconds(3600),
                        "gate"
                )));
        when(apartmentRepository.findById(APARTMENT_ID)).thenReturn(Optional.of(apartment));
        FieldSession savedSession = FieldSession.start(STUDY_ID, NOW);
        ReflectionTestUtils.setField(savedSession, "id", 100L);
        when(fieldSessionRepository.save(any())).thenReturn(savedSession);
        FieldParticipant savedParticipant = FieldParticipant.start(100L, MEMBER_ID, NOW);
        ReflectionTestUtils.setField(savedParticipant, "id", 301L);
        when(fieldParticipantRepository.save(any())).thenReturn(savedParticipant);
        when(startRequestRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        FieldVisitStartService.StartResult result = service.start(
                STUDY_ID, MEMBER_ID, body(37.5133, 127.0842)
        );

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void 반경_정확히_1000m이면_시작한다() {
        stubMemberActive();
        when(startRequestRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID, CLIENT_REQUEST_ID
        )).thenReturn(Optional.empty());
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.empty());
        stubScheduleReached();
        double apartmentLat = 37.52228871945116;
        Apartment farApartment = Apartment.create(
                "C2", "apt2", "addr", null, null, null, null,
                127.0842, apartmentLat, null, null, null
        );
        ReflectionTestUtils.setField(farApartment, "id", APARTMENT_ID);
        when(apartmentRepository.findById(APARTMENT_ID)).thenReturn(Optional.of(farApartment));
        FieldSession savedSession = FieldSession.start(STUDY_ID, NOW);
        ReflectionTestUtils.setField(savedSession, "id", 100L);
        when(fieldSessionRepository.save(any())).thenReturn(savedSession);
        FieldParticipant savedParticipant = FieldParticipant.start(100L, MEMBER_ID, NOW);
        ReflectionTestUtils.setField(savedParticipant, "id", 301L);
        when(fieldParticipantRepository.save(any())).thenReturn(savedParticipant);
        when(startRequestRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        int distance = com.ssafy.ssabangpalbang.fieldvisit.geo.GeoDistanceCalculator
                .distanceMeters(37.5133, 127.0842, apartmentLat, 127.0842);
        assertThat(distance).isEqualTo(1000);

        FieldVisitStartService.StartResult result = service.start(
                STUDY_ID, MEMBER_ID, body(37.5133, 127.0842)
        );

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.body().distanceMeters()).isEqualTo(1000);
        assertThat(result.body().allowedRadiusMeters()).isEqualTo(1000);
    }

    @Test
    void RECRUITING이면_최초_시작을_거부한다() {
        ReflectionTestUtils.setField(study, "status", StudyStatus.RECRUITING);
        stubMemberActive();
        when(startRequestRepository.findByMemberIdAndClientRequestId(MEMBER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.empty());
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(
                STUDY_ID, MEMBER_ID, body(37.5133, 127.0842)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_START_FORBIDDEN);
    }

    @Test
    void 이미_시작한_사용자_다른_clientRequestId는_200이다() {
        ReflectionTestUtils.setField(study, "status", StudyStatus.IN_PROGRESS);
        stubMemberActive();
        String newClientId = "b1b2c3d4-1234-5678-90ab-cdef12345678";
        when(startRequestRepository.findByMemberIdAndClientRequestId(MEMBER_ID, newClientId))
                .thenReturn(Optional.empty());
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));

        FieldSession session = FieldSession.start(STUDY_ID, Instant.now());
        ReflectionTestUtils.setField(session, "id", 100L);
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));

        FieldParticipant participant = FieldParticipant.start(100L, MEMBER_ID, Instant.now());
        ReflectionTestUtils.setField(participant, "id", 301L);
        when(fieldParticipantRepository.findBySessionIdAndMemberIdForUpdate(100L, MEMBER_ID))
                .thenReturn(Optional.of(participant));
        when(apartmentRepository.findById(APARTMENT_ID)).thenReturn(Optional.of(apartment));
        when(checklistRepository.findBySessionIdAndMemberId(100L, MEMBER_ID))
                .thenReturn(Optional.empty());
        when(startRequestRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        FieldVisitStartService.StartResult result = service.start(
                STUDY_ID, MEMBER_ID,
                new FieldVisitStartRequestBody(37.5133, 127.0842, newClientId)
        );

        assertThat(result.httpStatus()).isEqualTo(HttpStatus.OK);
        assertThat(result.responseCode())
                .isEqualTo(FieldVisitResponseCode.FIELD_VISIT_ALREADY_STARTED);
        verify(fieldParticipantRepository, never()).save(any());
        ArgumentCaptor<FieldVisitStartRequest> captor =
                ArgumentCaptor.forClass(FieldVisitStartRequest.class);
        verify(startRequestRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getClientRequestId()).isEqualTo(newClientId);
    }

    @Test
    void 종료된_참여자는_409다() {
        ReflectionTestUtils.setField(study, "status", StudyStatus.IN_PROGRESS);
        stubMemberActive();
        when(startRequestRepository.findByMemberIdAndClientRequestId(MEMBER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.empty());
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));

        FieldSession session = FieldSession.start(STUDY_ID, Instant.now());
        ReflectionTestUtils.setField(session, "id", 100L);
        ReflectionTestUtils.setField(session, "status", FieldSessionStatus.IN_PROGRESS);
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));

        FieldParticipant participant = FieldParticipant.start(100L, MEMBER_ID, Instant.now());
        ReflectionTestUtils.setField(participant, "id", 301L);
        ReflectionTestUtils.setField(participant, "status", FieldParticipantStatus.ENDED);
        when(fieldParticipantRepository.findBySessionIdAndMemberIdForUpdate(100L, MEMBER_ID))
                .thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> service.start(
                STUDY_ID, MEMBER_ID, body(37.5133, 127.0842)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_PARTICIPANT_ALREADY_ENDED);
    }

    @Test
    void 동일_키_다른_fingerprint는_멱등_충돌이다() {
        stubMemberActive();
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        String fingerprint = FieldVisitStartFingerprint.of(STUDY_ID, 37.5133, 127.0842);
        FieldVisitStartRequest history = FieldVisitStartRequest.create(
                MEMBER_ID, STUDY_ID, 100L, 301L, CLIENT_REQUEST_ID,
                "different-fingerprint", Instant.now()
        );
        when(startRequestRepository.findByMemberIdAndClientRequestId(MEMBER_ID, CLIENT_REQUEST_ID))
                .thenReturn(Optional.of(history));

        assertThatThrownBy(() -> service.start(
                STUDY_ID, MEMBER_ID, body(37.5133, 127.0842)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_START_IDEMPOTENCY_MISMATCH);
        assertThat(fingerprint).isNotEqualTo("different-fingerprint");
    }

    @Test
    void 멱등_충돌_로그에는_fingerprint_원문을_남기지_않는다() {
        stubMemberActive();
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        String requestFingerprint = FieldVisitStartFingerprint.of(
                STUDY_ID, 37.5133, 127.0842
        );
        String storedFingerprint = "stored-fingerprint-secret";
        FieldVisitStartRequest history = FieldVisitStartRequest.create(
                MEMBER_ID, STUDY_ID, 100L, 301L, CLIENT_REQUEST_ID,
                storedFingerprint, Instant.now()
        );
        when(startRequestRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID, CLIENT_REQUEST_ID
        )).thenReturn(Optional.of(history));
        Logger logger = (Logger) LoggerFactory.getLogger(FieldVisitStartService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> service.start(
                    STUDY_ID, MEMBER_ID, body(37.5133, 127.0842)
            )).isInstanceOf(BusinessException.class);

            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage())
                        .contains("FIELD_VISIT_START_IDEMPOTENCY_MISMATCH")
                        .contains("mismatchReasons=[requestFingerprint]")
                        .doesNotContain("mismatchReasons=[studyId,")
                        .doesNotContain(storedFingerprint)
                        .doesNotContain(requestFingerprint)
                        .doesNotContain("existingFingerprint=")
                        .doesNotContain("requestFingerprint=");
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void 진행중_세션의_고정_후보가_아니면_후발_참여를_거부한다() {
        ReflectionTestUtils.setField(study, "status", StudyStatus.IN_PROGRESS);
        stubMemberActive();
        when(startRequestRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID, CLIENT_REQUEST_ID
        )).thenReturn(Optional.empty());
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));

        FieldSession session = FieldSession.start(STUDY_ID, Instant.now());
        ReflectionTestUtils.setField(session, "id", 100L);
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));
        when(fieldParticipantRepository.findBySessionIdAndMemberIdForUpdate(
                100L, MEMBER_ID
        )).thenReturn(Optional.empty());
        when(fieldVisitCandidateRepository.existsBySessionIdAndMemberId(
                100L, MEMBER_ID
        )).thenReturn(false);

        assertThatThrownBy(() -> service.start(
                STUDY_ID, MEMBER_ID, body(37.5133, 127.0842)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_START_FORBIDDEN);

        verify(fieldParticipantRepository, never()).save(any());
    }

    @Test
    void 취소된_스터디의_진행중_세션에는_참여를_시작할_수_없다() {
        ReflectionTestUtils.setField(study, "status", StudyStatus.CANCELED);
        stubMemberActive();
        when(startRequestRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID, CLIENT_REQUEST_ID
        )).thenReturn(Optional.empty());
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));

        FieldSession session = FieldSession.start(STUDY_ID, Instant.now());
        ReflectionTestUtils.setField(session, "id", 100L);
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.start(
                STUDY_ID, MEMBER_ID, body(37.5133, 127.0842)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_START_FORBIDDEN);

        verify(fieldParticipantRepository, never())
                .findBySessionIdAndMemberIdForUpdate(any(), any());
    }

    @Test
    void 멱등_이력이_있어도_현재_ACTIVE_멤버가_아니면_거부한다() {
        Member member = activeMember();
        when(memberRepository.findByIdForNoKeyUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        FieldVisitStartRequest history = FieldVisitStartRequest.create(
                MEMBER_ID,
                STUDY_ID,
                100L,
                301L,
                CLIENT_REQUEST_ID,
                FieldVisitStartFingerprint.of(STUDY_ID, 37.5133, 127.0842),
                Instant.now()
        );
        when(startRequestRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID, CLIENT_REQUEST_ID
        )).thenReturn(Optional.of(history));
        when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(
                STUDY_ID, MEMBER_ID, body(37.5133, 127.0842)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_START_FORBIDDEN);

        verify(startRequestRepository, never())
                .findByMemberIdAndClientRequestId(any(), any());
    }

    @Test
    void 멱등_재요청이라도_종료된_세션이면_409다() {
        ReflectionTestUtils.setField(study, "status", StudyStatus.IN_PROGRESS);
        stubMemberActive();
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        FieldVisitStartRequest history = FieldVisitStartRequest.create(
                MEMBER_ID,
                STUDY_ID,
                100L,
                301L,
                CLIENT_REQUEST_ID,
                FieldVisitStartFingerprint.of(STUDY_ID, 37.5133, 127.0842),
                Instant.now()
        );
        when(startRequestRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID, CLIENT_REQUEST_ID
        )).thenReturn(Optional.of(history));
        FieldSession session = FieldSession.start(STUDY_ID, Instant.now());
        ReflectionTestUtils.setField(session, "id", 100L);
        ReflectionTestUtils.setField(session, "status", FieldSessionStatus.ENDED);
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.start(
                STUDY_ID, MEMBER_ID, body(37.5133, 127.0842)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FIELD_VISIT_ALREADY_ENDED);
    }

    @Test
    void UUID_대문자_표현은_소문자_표준형으로_저장한다() {
        ReflectionTestUtils.setField(study, "status", StudyStatus.IN_PROGRESS);
        stubMemberActive();
        String uppercaseRequestId = CLIENT_REQUEST_ID.toUpperCase(Locale.ROOT);
        when(startRequestRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID, CLIENT_REQUEST_ID
        )).thenReturn(Optional.empty());
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));

        FieldSession session = FieldSession.start(STUDY_ID, Instant.now());
        ReflectionTestUtils.setField(session, "id", 100L);
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.of(session));
        FieldParticipant participant = FieldParticipant.start(
                100L, MEMBER_ID, Instant.now()
        );
        ReflectionTestUtils.setField(participant, "id", 301L);
        when(fieldParticipantRepository.findBySessionIdAndMemberIdForUpdate(
                100L, MEMBER_ID
        )).thenReturn(Optional.of(participant));
        when(apartmentRepository.findById(APARTMENT_ID))
                .thenReturn(Optional.of(apartment));
        when(startRequestRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.start(
                STUDY_ID,
                MEMBER_ID,
                new FieldVisitStartRequestBody(
                        37.5133, 127.0842, uppercaseRequestId
                )
        );

        ArgumentCaptor<FieldVisitStartRequest> captor =
                ArgumentCaptor.forClass(FieldVisitStartRequest.class);
        verify(startRequestRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getClientRequestId())
                .isEqualTo(CLIENT_REQUEST_ID);
    }

    private void stubScheduleReached() {
        when(scheduleRepository.findByStudyIdAndStatus(STUDY_ID, ScheduleStatus.SCHEDULED))
                .thenReturn(Optional.of(Schedule.create(
                        STUDY_ID,
                        NOW.minusSeconds(60),
                        NOW.plusSeconds(3600),
                        "gate"
                )));
    }

    private void stubFirstStartRequest() {
        stubMemberActive();
        when(startRequestRepository.findByMemberIdAndClientRequestId(
                MEMBER_ID, CLIENT_REQUEST_ID
        )).thenReturn(Optional.empty());
        when(studyRepository.findForNoKeyUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        when(fieldSessionRepository.findByStudyIdForUpdate(STUDY_ID))
                .thenReturn(Optional.empty());
    }

    private void stubSuccessfulFirstStartPersistence() {
        when(apartmentRepository.findById(APARTMENT_ID))
                .thenReturn(Optional.of(apartment));
        FieldSession savedSession = FieldSession.start(STUDY_ID, NOW);
        ReflectionTestUtils.setField(savedSession, "id", 100L);
        when(fieldSessionRepository.save(any())).thenReturn(savedSession);
        FieldParticipant savedParticipant = FieldParticipant.start(
                100L, MEMBER_ID, NOW
        );
        ReflectionTestUtils.setField(savedParticipant, "id", 301L);
        when(fieldParticipantRepository.save(any())).thenReturn(savedParticipant);
        when(startRequestRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubMemberActive() {
        when(memberRepository.findByIdForNoKeyUpdate(MEMBER_ID))
                .thenReturn(Optional.of(activeMember()));
        when(memberRepository.findAllByIdForNoKeyUpdate(List.of(MEMBER_ID)))
                .thenReturn(List.of(activeMember()));
        StudyMember member = activeStudyMember(MEMBER_ID);
        when(studyMemberRepository.findByStudyIdAndMemberId(STUDY_ID, MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(studyMemberRepository.findByStudyIdAndStatus(
                STUDY_ID, StudyMemberStatus.ACTIVE
        )).thenReturn(List.of(member));
    }

    private Member activeMember() {
        return activeMember(MEMBER_ID);
    }

    private Member activeMember(Long memberId) {
        Member member = new Member(
                "member" + memberId + "@example.com", "hash", "member"
        );
        ReflectionTestUtils.setField(member, "id", memberId);
        return member;
    }

    private StudyMember activeStudyMember(Long memberId) {
        StudyMember member = StudyMember.createMember(STUDY_ID, memberId);
        ReflectionTestUtils.setField(member, "status", StudyMemberStatus.ACTIVE);
        return member;
    }

    private static FieldVisitStartRequestBody body(double lat, double lng) {
        return new FieldVisitStartRequestBody(lat, lng, CLIENT_REQUEST_ID);
    }

    private static FieldVisitStartRequestBody confirmedBody(
            double lat,
            double lng
    ) {
        return new FieldVisitStartRequestBody(
                lat, lng, CLIENT_REQUEST_ID, true
        );
    }
}

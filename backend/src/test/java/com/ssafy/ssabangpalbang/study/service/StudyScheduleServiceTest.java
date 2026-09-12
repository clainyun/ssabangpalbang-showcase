package com.ssafy.ssabangpalbang.study.service;

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
import com.ssafy.ssabangpalbang.study.dto.request.StudyScheduleCreateRequest;
import com.ssafy.ssabangpalbang.study.dto.request.StudyScheduleUpdateRequest;
import com.ssafy.ssabangpalbang.study.repository.ScheduleRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import com.ssafy.ssabangpalbang.study.service.port.StudyNotificationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudyScheduleServiceTest {

    private static final Long STUDY_ID = 10L;
    private static final Long LEADER_ID = 7L;
    private static final Long MEMBER_ID = 8L;
    private static final Instant START_AT = Instant.parse("2099-08-15T06:00:00Z");
    private static final Instant END_AT = Instant.parse("2099-08-15T09:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-07-30T00:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-07-30T00:01:00Z");

    @Mock
    private StudyRepository studyRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private StudyMemberRepository studyMemberRepository;
    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private StudyNotificationPort notificationPort;
    @InjectMocks
    private StudyScheduleService service;

    private Study study;
    private Member leader;
    private Schedule schedule;

    @BeforeEach
    void setUp() {
        study = study(StudyStatus.RECRUITING);
        leader = member(LEADER_ID);
        schedule = schedule(ScheduleStatus.SCHEDULED);

        lenient().when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        lenient().when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        lenient().when(memberRepository.findById(LEADER_ID))
                .thenReturn(Optional.of(leader));
        lenient().when(memberRepository.findAllById(anyList()))
                .thenReturn(List.of());
        lenient().when(studyMemberRepository.findByStudyIdAndStatus(
                        STUDY_ID,
                        StudyMemberStatus.ACTIVE
                ))
                .thenReturn(List.of());
        lenient().when(scheduleRepository.findByStudyId(STUDY_ID))
                .thenReturn(Optional.empty());
        lenient().when(scheduleRepository.save(any(Schedule.class)))
                .thenAnswer(invocation -> {
                    Schedule saved = invocation.getArgument(0);
                    initializeSchedule(saved, 1L);
                    return saved;
                });
    }

    @Nested
    class Create {

        @Test
        void 스터디장이_RECRUITING_스터디에_등록한다() {
            var response = service.createSchedule(LEADER_ID, STUDY_ID, createRequest());

            assertThat(response.status()).isEqualTo("SCHEDULED");
            verify(studyRepository)
                    .findForUpdateByIdAndDeletedAtIsNull(STUDY_ID);
            verify(scheduleRepository).save(any(Schedule.class));
        }

        @Test
        void 스터디장이_CLOSED_스터디에_등록한다() {
            setStudyStatus(StudyStatus.CLOSED);

            assertThatCode(() -> service.createSchedule(
                    LEADER_ID, STUDY_ID, createRequest()
            )).doesNotThrowAnyException();
        }

        @Test
        void CANCELED_행을_같은_ID로_재활성화한다() {
            setScheduleStatus(ScheduleStatus.CANCELED);
            when(scheduleRepository.findByStudyId(STUDY_ID))
                    .thenReturn(Optional.of(schedule));

            var response = service.createSchedule(
                    LEADER_ID,
                    STUDY_ID,
                    new StudyScheduleCreateRequest(
                            START_AT.plusSeconds(60),
                            END_AT.plusSeconds(60),
                            "새 장소"
                    )
            );

            assertThat(response.scheduleId()).isEqualTo(1L);
            assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.SCHEDULED);
            assertThat(schedule.getMeetingPlace()).isEqualTo("새 장소");
            verify(scheduleRepository, never()).save(any());
        }

        @Test
        void SCHEDULED_행이_있으면_중복_에러다() {
            when(scheduleRepository.findByStudyId(STUDY_ID))
                    .thenReturn(Optional.of(schedule));

            assertError(
                    () -> service.createSchedule(LEADER_ID, STUDY_ID, createRequest()),
                    ErrorCode.STUDY_SCHEDULE_ALREADY_EXISTS
            );
        }

        @Test
        void COMPLETED_행이_있으면_중복_에러다() {
            setScheduleStatus(ScheduleStatus.COMPLETED);
            when(scheduleRepository.findByStudyId(STUDY_ID))
                    .thenReturn(Optional.of(schedule));

            assertError(
                    () -> service.createSchedule(LEADER_ID, STUDY_ID, createRequest()),
                    ErrorCode.STUDY_SCHEDULE_ALREADY_EXISTS
            );
        }

        @Test
        void 스터디장이_아니면_등록할_수_없다() {
            when(memberRepository.findById(MEMBER_ID))
                    .thenReturn(Optional.of(member(MEMBER_ID)));

            assertError(
                    () -> service.createSchedule(MEMBER_ID, STUDY_ID, createRequest()),
                    ErrorCode.STUDY_SCHEDULE_CREATE_FORBIDDEN
            );
        }

        @Test
        void IN_PROGRESS에서는_등록할_수_없고_상태를_담는다() {
            setStudyStatus(StudyStatus.IN_PROGRESS);

            assertThatThrownBy(() -> service.createSchedule(
                    LEADER_ID, STUDY_ID, createRequest()
            )).isInstanceOfSatisfying(BusinessException.class, exception -> {
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.STUDY_SCHEDULE_CREATE_NOT_ALLOWED);
                assertThat(exception.getData())
                        .containsEntry("studyStatus", "IN_PROGRESS");
            });
        }

        @Test
        void COMPLETED에서는_등록할_수_없다() {
            setStudyStatus(StudyStatus.COMPLETED);

            assertError(
                    () -> service.createSchedule(LEADER_ID, STUDY_ID, createRequest()),
                    ErrorCode.STUDY_SCHEDULE_CREATE_NOT_ALLOWED
            );
        }

        @Test
        void CANCELED에서는_등록할_수_없다() {
            setStudyStatus(StudyStatus.CANCELED);

            assertError(
                    () -> service.createSchedule(LEADER_ID, STUDY_ID, createRequest()),
                    ErrorCode.STUDY_SCHEDULE_CREATE_NOT_ALLOWED
            );
        }

        @Test
        void 존재하지_않는_스터디면_404다() {
            when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                    .thenReturn(Optional.empty());

            assertError(
                    () -> service.createSchedule(LEADER_ID, STUDY_ID, createRequest()),
                    ErrorCode.STUDY_NOT_FOUND
            );
        }

        @Test
        void 삭제된_스터디면_404다() {
            when(studyRepository.findForUpdateByIdAndDeletedAtIsNull(STUDY_ID))
                    .thenReturn(Optional.empty());

            assertError(
                    () -> service.createSchedule(LEADER_ID, STUDY_ID, createRequest()),
                    ErrorCode.STUDY_NOT_FOUND
            );
        }

        @Test
        void 과거_시작_시각은_거부한다() {
            var request = new StudyScheduleCreateRequest(
                    Instant.parse("2020-01-01T00:00:00Z"),
                    null,
                    "장소"
            );

            assertError(
                    () -> service.createSchedule(LEADER_ID, STUDY_ID, request),
                    ErrorCode.STUDY_SCHEDULE_TIME_INVALID
            );
        }

        @Test
        void 종료가_시작보다_빠르면_거부한다() {
            var request = new StudyScheduleCreateRequest(
                    START_AT,
                    START_AT.minusSeconds(1),
                    "장소"
            );

            assertError(
                    () -> service.createSchedule(LEADER_ID, STUDY_ID, request),
                    ErrorCode.STUDY_SCHEDULE_TIME_INVALID
            );
        }

        @Test
        void 종료와_시작이_같으면_성공한다() {
            var request = new StudyScheduleCreateRequest(
                    START_AT,
                    START_AT,
                    "장소"
            );

            assertThatCode(() -> service.createSchedule(
                    LEADER_ID, STUDY_ID, request
            )).doesNotThrowAnyException();
        }

        @Test
        void 종료_시각이_null이어도_성공한다() {
            var request = new StudyScheduleCreateRequest(
                    START_AT,
                    null,
                    "장소"
            );

            assertThat(service.createSchedule(LEADER_ID, STUDY_ID, request).endAt())
                    .isNull();
        }

        @Test
        void 집결_장소의_앞뒤_공백을_제거한다() {
            var request = new StudyScheduleCreateRequest(
                    START_AT,
                    END_AT,
                    "  옥수역  "
            );

            assertThat(service.createSchedule(
                    LEADER_ID, STUDY_ID, request
            ).meetingPlace()).isEqualTo("옥수역");
        }

        @Test
        void 동시_등록_UNIQUE_충돌을_409로_변환한다() {
            doThrow(new DataIntegrityViolationException("duplicate"))
                    .when(scheduleRepository).flush();

            assertError(
                    () -> service.createSchedule(LEADER_ID, STUDY_ID, createRequest()),
                    ErrorCode.STUDY_SCHEDULE_ALREADY_EXISTS
            );
        }
    }

    @Nested
    class Get {

        @Test
        void 스터디장_조회는_관리_권한이_있다() {
            when(scheduleRepository.findByStudyId(STUDY_ID))
                    .thenReturn(Optional.of(schedule));

            var response = service.getSchedule(LEADER_ID, STUDY_ID);

            assertThat(response.isLeader()).isTrue();
            assertThat(response.canManageSchedule()).isTrue();
            verify(studyRepository).findByIdAndDeletedAtIsNull(STUDY_ID);
            verify(studyRepository, never())
                    .findForUpdateByIdAndDeletedAtIsNull(STUDY_ID);
        }

        @Test
        void ACTIVE_멤버는_조회하되_관리_권한은_없다() {
            allowActiveMember();

            var response = service.getSchedule(MEMBER_ID, STUDY_ID);

            assertThat(response.isLeader()).isFalse();
            assertThat(response.canManageSchedule()).isFalse();
        }

        @Test
        void PENDING_신청자는_조회할_수_없다() {
            when(memberRepository.findById(MEMBER_ID))
                    .thenReturn(Optional.of(member(MEMBER_ID)));

            assertError(
                    () -> service.getSchedule(MEMBER_ID, STUDY_ID),
                    ErrorCode.STUDY_SCHEDULE_ACCESS_DENIED
            );
        }

        @Test
        void 비참여_회원은_조회할_수_없다() {
            when(memberRepository.findById(MEMBER_ID))
                    .thenReturn(Optional.of(member(MEMBER_ID)));

            assertError(
                    () -> service.getSchedule(MEMBER_ID, STUDY_ID),
                    ErrorCode.STUDY_SCHEDULE_ACCESS_DENIED
            );
        }

        @Test
        void 일정_행이_없으면_schedule은_null이다() {
            assertThat(service.getSchedule(LEADER_ID, STUDY_ID).schedule()).isNull();
        }

        @Test
        void 취소된_일정은_schedule이_null이다() {
            setScheduleStatus(ScheduleStatus.CANCELED);
            when(scheduleRepository.findByStudyId(STUDY_ID))
                    .thenReturn(Optional.of(schedule));

            assertThat(service.getSchedule(LEADER_ID, STUDY_ID).schedule()).isNull();
        }

        @Test
        void 취소된_스터디의_일정_상태도_CANCELED다() {
            setStudyStatus(StudyStatus.CANCELED);
            when(scheduleRepository.findByStudyId(STUDY_ID))
                    .thenReturn(Optional.of(schedule));

            var response = service.getSchedule(LEADER_ID, STUDY_ID);

            assertThat(response.schedule().status()).isEqualTo("CANCELED");
            assertThat(response.canManageSchedule()).isFalse();
        }

        @Test
        void 완료된_스터디도_조회할_수_있다() {
            setStudyStatus(StudyStatus.COMPLETED);
            when(scheduleRepository.findByStudyId(STUDY_ID))
                    .thenReturn(Optional.of(schedule));

            assertThat(service.getSchedule(
                    LEADER_ID, STUDY_ID
            ).canManageSchedule()).isFalse();
        }

        @Test
        void 진행중인_스터디장은_일정을_관리할_수_없다() {
            setStudyStatus(StudyStatus.IN_PROGRESS);

            assertThat(service.getSchedule(
                    LEADER_ID, STUDY_ID
            ).canManageSchedule()).isFalse();
        }

        @Test
        void calendarEvent는_제목과_장소를_매핑한다() {
            when(scheduleRepository.findByStudyId(STUDY_ID))
                    .thenReturn(Optional.of(schedule));

            var event = service.getSchedule(
                    LEADER_ID, STUDY_ID
            ).schedule().calendarEvent();

            assertThat(event.title()).isEqualTo("검증 스터디");
            assertThat(event.location()).isEqualTo("옥수역");
        }
    }

    @Nested
    class Update {

        @BeforeEach
        void useSchedule() {
            when(scheduleRepository.findByStudyId(STUDY_ID))
                    .thenReturn(Optional.of(schedule));
        }

        @Test
        void 집결_장소만_수정한다() {
            var response = service.updateSchedule(
                    LEADER_ID,
                    STUDY_ID,
                    new StudyScheduleUpdateRequest(null, null, "새 장소")
            );

            assertThat(response.startAt()).endsWith("+09:00");
            assertThat(response.meetingPlace()).isEqualTo("새 장소");
            verify(studyRepository)
                    .findForUpdateByIdAndDeletedAtIsNull(STUDY_ID);
        }

        @Test
        void 모든_필드가_null이면_거부한다() {
            assertError(
                    () -> service.updateSchedule(
                            LEADER_ID,
                            STUDY_ID,
                            new StudyScheduleUpdateRequest(null, null, null)
                    ),
                    ErrorCode.STUDY_SCHEDULE_UPDATE_EMPTY
            );
        }

        @Test
        void 공백_장소는_공통_입력_에러다() {
            assertError(
                    () -> service.updateSchedule(
                            LEADER_ID,
                            STUDY_ID,
                            new StudyScheduleUpdateRequest(null, null, " ")
                    ),
                    ErrorCode.INVALID_INPUT_VALUE
            );
        }

        @Test
        void 일정_행이_없으면_404다() {
            when(scheduleRepository.findByStudyId(STUDY_ID))
                    .thenReturn(Optional.empty());

            assertError(
                    () -> service.updateSchedule(
                            LEADER_ID,
                            STUDY_ID,
                            updateRequest()
                    ),
                    ErrorCode.STUDY_SCHEDULE_NOT_FOUND
            );
        }

        @Test
        void 취소된_일정은_404다() {
            setScheduleStatus(ScheduleStatus.CANCELED);

            assertError(
                    () -> service.updateSchedule(
                            LEADER_ID, STUDY_ID, updateRequest()
                    ),
                    ErrorCode.STUDY_SCHEDULE_NOT_FOUND
            );
        }

        @Test
        void 완료된_일정은_수정할_수_없다() {
            setScheduleStatus(ScheduleStatus.COMPLETED);

            assertError(
                    () -> service.updateSchedule(
                            LEADER_ID, STUDY_ID, updateRequest()
                    ),
                    ErrorCode.STUDY_SCHEDULE_UPDATE_NOT_ALLOWED
            );
        }

        @Test
        void 스터디장이_아니면_수정할_수_없다() {
            when(memberRepository.findById(MEMBER_ID))
                    .thenReturn(Optional.of(member(MEMBER_ID)));

            assertError(
                    () -> service.updateSchedule(
                            MEMBER_ID, STUDY_ID, updateRequest()
                    ),
                    ErrorCode.STUDY_SCHEDULE_UPDATE_FORBIDDEN
            );
        }

        @Test
        void 진행중인_스터디는_수정할_수_없다() {
            setStudyStatus(StudyStatus.IN_PROGRESS);

            assertError(
                    () -> service.updateSchedule(
                            LEADER_ID, STUDY_ID, updateRequest()
                    ),
                    ErrorCode.STUDY_SCHEDULE_UPDATE_NOT_ALLOWED
            );
        }

        @Test
        void 과거_시작_시각은_거부한다() {
            assertError(
                    () -> service.updateSchedule(
                            LEADER_ID,
                            STUDY_ID,
                            new StudyScheduleUpdateRequest(
                                    Instant.parse("2020-01-01T00:00:00Z"),
                                    null,
                                    null
                            )
                    ),
                    ErrorCode.STUDY_SCHEDULE_TIME_INVALID
            );
        }

        @Test
        void 최종_시작이_기존_종료보다_뒤면_거부한다() {
            assertError(
                    () -> service.updateSchedule(
                            LEADER_ID,
                            STUDY_ID,
                            new StudyScheduleUpdateRequest(
                                    END_AT.plusSeconds(1),
                                    null,
                                    null
                            )
                    ),
                    ErrorCode.STUDY_SCHEDULE_TIME_INVALID
            );
        }

        @Test
        void 수정_응답에는_createdAt_프로퍼티가_없다() {
            assertThat(com.ssafy.ssabangpalbang.study.dto.response
                    .StudyScheduleUpdateResponse.class.getRecordComponents())
                    .extracting(component -> component.getName())
                    .doesNotContain("createdAt");
        }

        @Test
        void 수정하면_updatedAt은_바뀌고_createdAt은_유지된다() {
            Instant originalCreatedAt = schedule.getCreatedAt();
            doAnswer(invocation -> {
                ReflectionTestUtils.setField(
                        schedule,
                        "updatedAt",
                        UPDATED_AT.plusSeconds(60)
                );
                return null;
            }).when(scheduleRepository).flush();

            var response = service.updateSchedule(
                    LEADER_ID, STUDY_ID, updateRequest()
            );

            assertThat(response.updatedAt())
                    .isEqualTo("2026-07-30T09:02:00+09:00");
            assertThat(schedule.getCreatedAt()).isEqualTo(originalCreatedAt);
        }
    }

    @Nested
    class Delete {

        @BeforeEach
        void useSchedule() {
            when(scheduleRepository.findByStudyId(STUDY_ID))
                    .thenReturn(Optional.of(schedule));
        }

        @Test
        void 삭제는_행을_취소_상태로_바꾼다() {
            var response = service.deleteSchedule(LEADER_ID, STUDY_ID);

            assertThat(response.status()).isEqualTo("CANCELED");
            verify(studyRepository)
                    .findForUpdateByIdAndDeletedAtIsNull(STUDY_ID);
            verify(scheduleRepository, never()).delete(any());
        }

        @Test
        void 삭제_후_조회하면_schedule이_null이다() {
            service.deleteSchedule(LEADER_ID, STUDY_ID);

            assertThat(service.getSchedule(LEADER_ID, STUDY_ID).schedule()).isNull();
        }

        @Test
        void 삭제_후_재등록하면_같은_행을_재활성화한다() {
            service.deleteSchedule(LEADER_ID, STUDY_ID);

            var response = service.createSchedule(
                    LEADER_ID, STUDY_ID, createRequest()
            );

            assertThat(response.scheduleId()).isEqualTo(1L);
            assertThat(response.status()).isEqualTo("SCHEDULED");
        }

        @Test
        void 이미_취소된_일정은_404다() {
            setScheduleStatus(ScheduleStatus.CANCELED);

            assertError(
                    () -> service.deleteSchedule(LEADER_ID, STUDY_ID),
                    ErrorCode.STUDY_SCHEDULE_NOT_FOUND
            );
        }

        @Test
        void 완료된_일정은_삭제할_수_없다() {
            setScheduleStatus(ScheduleStatus.COMPLETED);

            assertError(
                    () -> service.deleteSchedule(LEADER_ID, STUDY_ID),
                    ErrorCode.STUDY_SCHEDULE_DELETE_NOT_ALLOWED
            );
        }

        @Test
        void 스터디장이_아니면_삭제할_수_없다() {
            when(memberRepository.findById(MEMBER_ID))
                    .thenReturn(Optional.of(member(MEMBER_ID)));

            assertError(
                    () -> service.deleteSchedule(MEMBER_ID, STUDY_ID),
                    ErrorCode.STUDY_SCHEDULE_DELETE_FORBIDDEN
            );
        }

        @Test
        void 진행중인_스터디는_삭제할_수_없다() {
            setStudyStatus(StudyStatus.IN_PROGRESS);

            assertError(
                    () -> service.deleteSchedule(LEADER_ID, STUDY_ID),
                    ErrorCode.STUDY_SCHEDULE_DELETE_NOT_ALLOWED
            );
        }

        @Test
        void 삭제_응답_필드는_정확히_4개다() {
            assertThat(com.ssafy.ssabangpalbang.study.dto.response
                    .StudyScheduleDeleteResponse.class.getRecordComponents())
                    .extracting(component -> component.getName())
                    .containsExactly("studyId", "scheduleId", "status", "updatedAt");
        }
    }

    @Nested
    class Notification {

        @Test
        void A1_등록은_ACTIVE_멤버_3명에게_알린다() {
            recipients(8L, 9L, 10L);

            service.createSchedule(LEADER_ID, STUDY_ID, createRequest());

            verify(notificationPort, times(3)).notifyScheduleCreated(any());
        }

        @Test
        void A2_PENDING_신청자는_수신자_조회에_포함되지_않는다() {
            recipients(8L);

            service.createSchedule(LEADER_ID, STUDY_ID, createRequest());

            verify(notificationPort, times(1)).notifyScheduleCreated(any());
            verify(studyMemberRepository).findByStudyIdAndStatus(
                    STUDY_ID,
                    StudyMemberStatus.ACTIVE
            );
        }

        @Test
        void A3_스터디장은_수신자에서_제외한다() {
            recipients(LEADER_ID, MEMBER_ID);

            service.createSchedule(LEADER_ID, STUDY_ID, createRequest());

            ArgumentCaptor<StudyNotificationPort.ScheduleNotification> captor =
                    ArgumentCaptor.forClass(
                            StudyNotificationPort.ScheduleNotification.class
                    );
            verify(notificationPort).notifyScheduleCreated(captor.capture());
            assertThat(captor.getValue().recipientId()).isEqualTo(MEMBER_ID);
        }

        @Test
        void A4_시작_시각_변경은_변경_알림을_만든다() {
            useScheduleAndRecipient();

            service.updateSchedule(
                    LEADER_ID,
                    STUDY_ID,
                    new StudyScheduleUpdateRequest(
                            START_AT.plusSeconds(60),
                            END_AT.plusSeconds(120),
                            null
                    )
            );

            verify(notificationPort).notifyScheduleChanged(any());
        }

        @Test
        void A5_집결_장소_변경은_변경_알림을_만든다() {
            useScheduleAndRecipient();

            service.updateSchedule(LEADER_ID, STUDY_ID, updateRequest());

            verify(notificationPort).notifyScheduleChanged(any());
        }

        @Test
        void A6_종료_시각만_바뀌면_알리지_않는다() {
            useScheduleAndRecipient();

            service.updateSchedule(
                    LEADER_ID,
                    STUDY_ID,
                    new StudyScheduleUpdateRequest(
                            null,
                            END_AT.plusSeconds(60),
                            null
                    )
            );

            verify(notificationPort, never()).notifyScheduleChanged(any());
        }

        @Test
        void A7_기존과_같은_값이면_알리지_않는다() {
            useScheduleAndRecipient();

            service.updateSchedule(
                    LEADER_ID,
                    STUDY_ID,
                    new StudyScheduleUpdateRequest(null, null, "옥수역")
            );

            verify(notificationPort, never()).notifyScheduleChanged(any());
        }

        @Test
        void A8_삭제는_취소_알림을_만든다() {
            useScheduleAndRecipient();

            service.deleteSchedule(LEADER_ID, STUDY_ID);

            verify(notificationPort).notifyScheduleCanceled(any());
        }

        @Test
        void A9_알림에_멱등키_구성요소를_모두_넘긴다() {
            recipients(MEMBER_ID);

            service.createSchedule(LEADER_ID, STUDY_ID, createRequest());

            ArgumentCaptor<StudyNotificationPort.ScheduleNotification> captor =
                    ArgumentCaptor.forClass(
                            StudyNotificationPort.ScheduleNotification.class
                    );
            verify(notificationPort).notifyScheduleCreated(captor.capture());
            var notification = captor.getValue();
            assertThat(notification.scheduleId()).isEqualTo(1L);
            assertThat(notification.recipientId()).isEqualTo(MEMBER_ID);
            assertThat(notification.versionEpochMilli())
                    .isEqualTo(UPDATED_AT.toEpochMilli());
        }

        @Test
        void A10_포트의_멱등_충돌이_서비스_밖으로_나오지_않는다() {
            recipients(MEMBER_ID);

            assertThatCode(() -> {
                service.createSchedule(LEADER_ID, STUDY_ID, createRequest());
                setScheduleStatus(ScheduleStatus.CANCELED);
                when(scheduleRepository.findByStudyId(STUDY_ID))
                        .thenReturn(Optional.of(schedule));
                service.createSchedule(LEADER_ID, STUDY_ID, createRequest());
            }).doesNotThrowAnyException();
        }

        @Test
        void A11_ACTIVE_멤버가_없어도_API는_성공한다() {
            assertThatCode(() -> service.createSchedule(
                    LEADER_ID, STUDY_ID, createRequest()
            )).doesNotThrowAnyException();
            verify(notificationPort, never()).notifyScheduleCreated(any());
        }

        @Test
        void A12_긴_제목도_포트까지_전달한다() {
            ReflectionTestUtils.setField(study, "title", "가".repeat(500));
            recipients(MEMBER_ID);

            assertThatCode(() -> service.createSchedule(
                    LEADER_ID, STUDY_ID, createRequest()
            )).doesNotThrowAnyException();
            verify(notificationPort).notifyScheduleCreated(any());
        }
    }

    private StudyScheduleCreateRequest createRequest() {
        return new StudyScheduleCreateRequest(START_AT, END_AT, "옥수역");
    }

    private StudyScheduleUpdateRequest updateRequest() {
        return new StudyScheduleUpdateRequest(null, null, "새 장소");
    }

    private Study study(StudyStatus status) {
        Study result = Study.create(
                1L,
                LEADER_ID,
                "검증 스터디",
                "소개",
                "목표",
                10,
                null
        );
        ReflectionTestUtils.setField(result, "id", STUDY_ID);
        ReflectionTestUtils.setField(result, "status", status);
        return result;
    }

    private Member member(Long id) {
        Member result = new Member(
                "member" + id + "@example.com",
                "password",
                "member" + id
        );
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    private Schedule schedule(ScheduleStatus status) {
        Schedule result = Schedule.create(
                STUDY_ID,
                START_AT,
                END_AT,
                "옥수역"
        );
        initializeSchedule(result, 1L);
        ReflectionTestUtils.setField(result, "status", status);
        return result;
    }

    private void initializeSchedule(Schedule target, Long id) {
        ReflectionTestUtils.setField(target, "id", id);
        ReflectionTestUtils.setField(target, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(target, "updatedAt", UPDATED_AT);
    }

    private void setStudyStatus(StudyStatus status) {
        ReflectionTestUtils.setField(study, "status", status);
    }

    private void setScheduleStatus(ScheduleStatus status) {
        ReflectionTestUtils.setField(schedule, "status", status);
    }

    private void allowActiveMember() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member(MEMBER_ID)));
        when(studyMemberRepository.findByStudyIdAndStatus(
                STUDY_ID,
                StudyMemberStatus.ACTIVE
        )).thenReturn(List.of(StudyMember.createMember(STUDY_ID, MEMBER_ID)));
    }

    private void recipients(Long... ids) {
        List<StudyMember> recipients = java.util.Arrays.stream(ids)
                .map(id -> id.equals(LEADER_ID)
                        ? StudyMember.createLeader(STUDY_ID, id)
                        : StudyMember.createMember(STUDY_ID, id))
                .toList();
        when(studyMemberRepository.findByStudyIdAndStatus(
                STUDY_ID,
                StudyMemberStatus.ACTIVE
        )).thenReturn(recipients);
        when(memberRepository.findAllById(anyList()))
                .thenAnswer(invocation -> invocation.<List<Long>>getArgument(0)
                        .stream()
                        .map(this::member)
                        .toList());
    }

    private void useScheduleAndRecipient() {
        when(scheduleRepository.findByStudyId(STUDY_ID))
                .thenReturn(Optional.of(schedule));
        recipients(MEMBER_ID);
    }

    private void assertError(Runnable invocation, ErrorCode expected) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}

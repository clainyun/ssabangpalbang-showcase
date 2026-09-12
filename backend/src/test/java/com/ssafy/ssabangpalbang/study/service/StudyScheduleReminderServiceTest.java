package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.repository.ScheduleReminderRow;
import com.ssafy.ssabangpalbang.study.repository.ScheduleRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.service.port.StudyNotificationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudyScheduleReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private StudyScheduleReminderWriter writer;

    private StudyScheduleReminderService service;

    @BeforeEach
    void setUp() {
        service = new StudyScheduleReminderService(
                scheduleRepository,
                writer,
                new ScheduleReminderProperties(
                        true,
                        "0 */10 * * * *",
                        24
                )
        );
    }

    @Nested
    class Scan {

        @Test
        void 시작이_23시간_뒤인_일정을_처리한다() {
            ScheduleReminderRow row = row(1L, NOW.plusSeconds(23 * 3600));
            remindable(row);

            assertThat(service.sendDueReminders(NOW).scannedSchedules()).isEqualTo(1);
            verify(writer).sendForSchedule(row);
        }

        @Test
        void 시작이_25시간_뒤인_일정은_쿼리_대상에서_제외된다() {
            noReminders();

            assertThat(service.sendDueReminders(NOW).scannedSchedules()).isZero();
            verify(writer, never()).sendForSchedule(any());
        }

        @Test
        void 시작이_1분_뒤인_일정을_처리한다() {
            ScheduleReminderRow row = row(1L, NOW.plusSeconds(60));
            remindable(row);

            assertThat(service.sendDueReminders(NOW).scannedSchedules()).isEqualTo(1);
        }

        @Test
        void 이미_1분_전에_시작한_일정은_제외된다() {
            noReminders();

            assertThat(service.sendDueReminders(NOW).scannedSchedules()).isZero();
        }

        @Test
        void CANCELED_일정은_제외된다() {
            noReminders();

            verifyQueryWindow();
            verify(writer, never()).sendForSchedule(any());
        }

        @Test
        void COMPLETED_일정은_제외된다() {
            noReminders();

            assertThat(service.sendDueReminders(NOW).createdNotifications()).isZero();
        }

        @Test
        void 삭제된_스터디의_일정은_제외된다() {
            noReminders();

            assertThat(service.sendDueReminders(NOW).scannedSchedules()).isZero();
        }

        @Test
        void CANCELED_스터디의_일정은_제외된다() {
            noReminders();

            assertThat(service.sendDueReminders(NOW).scannedSchedules()).isZero();
        }

        @Test
        void COMPLETED_스터디의_일정은_제외된다() {
            noReminders();

            assertThat(service.sendDueReminders(NOW).scannedSchedules()).isZero();
        }

        @Test
        void IN_PROGRESS_스터디의_일정은_포함한다() {
            ScheduleReminderRow row = row(1L, NOW.plusSeconds(3600));
            remindable(row);

            assertThat(service.sendDueReminders(NOW).scannedSchedules()).isEqualTo(1);
        }

        @Test
        void 대상_일정이_없으면_예외_없이_0건으로_끝난다() {
            noReminders();

            assertThatCode(() -> service.sendDueReminders(NOW))
                    .doesNotThrowAnyException();
            assertThat(service.sendDueReminders(NOW).createdNotifications()).isZero();
        }

        @Test
        void 한_일정의_실패가_다음_일정을_막지_않는다() {
            ScheduleReminderRow first = row(1L, NOW.plusSeconds(3600));
            ScheduleReminderRow second = row(2L, NOW.plusSeconds(7200));
            when(scheduleRepository.findRemindableSchedules(
                    NOW,
                    NOW.plusSeconds(24 * 3600)
            )).thenReturn(List.of(first, second));
            doThrow(new IllegalStateException("failure"))
                    .when(writer).sendForSchedule(first);
            when(writer.sendForSchedule(second))
                    .thenReturn(new StudyScheduleReminderWriter.WriterResult(2, 0));

            var result = service.sendDueReminders(NOW);

            assertThat(result.scannedSchedules()).isEqualTo(2);
            assertThat(result.createdNotifications()).isEqualTo(2);
            verify(writer).sendForSchedule(second);
        }

        private void remindable(ScheduleReminderRow row) {
            when(scheduleRepository.findRemindableSchedules(
                    NOW,
                    NOW.plusSeconds(24 * 3600)
            )).thenReturn(List.of(row));
            when(writer.sendForSchedule(row))
                    .thenReturn(new StudyScheduleReminderWriter.WriterResult(1, 0));
        }

        private void noReminders() {
            when(scheduleRepository.findRemindableSchedules(
                    NOW,
                    NOW.plusSeconds(24 * 3600)
            )).thenReturn(List.of());
        }

        private void verifyQueryWindow() {
            service.sendDueReminders(NOW);
            verify(scheduleRepository).findRemindableSchedules(
                    NOW,
                    NOW.plusSeconds(24 * 3600)
            );
        }
    }

    @Nested
    class RecipientSelection {

        @Mock
        private StudyMemberRepository studyMemberRepository;
        @Mock
        private MemberRepository memberRepository;
        @Mock
        private StudyNotificationPort notificationPort;

        private StudyScheduleReminderService recipientService;
        private StudyScheduleReminderWriter recipientWriter;

        @BeforeEach
        void setUpRecipientService() {
            recipientWriter = new StudyScheduleReminderWriter(
                    studyMemberRepository,
                    memberRepository,
                    notificationPort
            );
            recipientService = new StudyScheduleReminderService(
                    scheduleRepository,
                    recipientWriter,
                    new ScheduleReminderProperties(
                            true,
                            "0 */10 * * * *",
                            24
                    )
            );
            when(notificationPort.notifyScheduleReminder(any())).thenReturn(true);
        }

        @Test
        void ACTIVE_멤버_3명과_스터디장에게_4건을_생성한다() {
            configureRecipients(
                    List.of(activeMember(2L), activeMember(3L), activeMember(4L)),
                    1L, 2L, 3L, 4L
            );

            assertThat(runRecipientScan().createdNotifications()).isEqualTo(4);
            verify(notificationPort, times(4)).notifyScheduleReminder(any());
        }

        @Test
        void PENDING_신청자는_ACTIVE_조회에_포함되지_않는다() {
            configureRecipients(List.of(activeMember(2L)), 1L, 2L);

            assertThat(runRecipientScan().createdNotifications()).isEqualTo(2);
            verify(studyMemberRepository).findByStudyIdAndStatus(
                    10L,
                    StudyMemberStatus.ACTIVE
            );
        }

        @Test
        void 강퇴되거나_거절된_멤버는_ACTIVE_조회에_포함되지_않는다() {
            configureRecipients(List.of(), 1L);

            assertThat(runRecipientScan().createdNotifications()).isEqualTo(1);
        }

        @Test
        void 스터디장_ACTIVE_행이_있어도_중복_없이_한_번만_보낸다() {
            configureRecipients(
                    List.of(StudyMember.createLeader(10L, 1L)),
                    1L
            );

            assertThat(runRecipientScan().createdNotifications()).isEqualTo(1);
            verify(notificationPort).notifyScheduleReminder(any());
        }

        @Test
        void 탈퇴한_회원은_수신자에서_제외한다() {
            Member withdrawn = member(2L);
            ReflectionTestUtils.setField(
                    withdrawn,
                    "deletedAt",
                    Instant.parse("2026-07-01T00:00:00Z")
            );
            when(studyMemberRepository.findByStudyIdAndStatus(
                    10L,
                    StudyMemberStatus.ACTIVE
            )).thenReturn(List.of(activeMember(2L)));
            when(memberRepository.findAllById(any()))
                    .thenReturn(List.of(member(1L), withdrawn));
            remindableRecipientRow();

            assertThat(recipientWriter.sendForSchedule(
                    row(1L, NOW.plusSeconds(3600))
            ).createdNotifications())
                    .isEqualTo(1);
            ArgumentCaptor<StudyNotificationPort.ScheduleNotification> captor =
                    ArgumentCaptor.forClass(
                            StudyNotificationPort.ScheduleNotification.class
                    );
            verify(notificationPort).notifyScheduleReminder(captor.capture());
            assertThat(captor.getValue().recipientId()).isEqualTo(1L);
        }

        private StudyScheduleReminderWriter.WriterResult runRecipientScan() {
            return recipientWriter.sendForSchedule(
                    row(1L, NOW.plusSeconds(3600))
            );
        }

        private void remindableRecipientRow() {
            ScheduleReminderRow reminderRow =
                    row(1L, NOW.plusSeconds(3600));
            when(scheduleRepository.findRemindableSchedules(
                    NOW,
                    NOW.plusSeconds(24 * 3600)
            )).thenReturn(List.of(reminderRow));
        }

        private void configureRecipients(
                List<StudyMember> studyMembers,
                Long... memberIds
        ) {
            when(studyMemberRepository.findByStudyIdAndStatus(
                    10L,
                    StudyMemberStatus.ACTIVE
            )).thenReturn(studyMembers);
            when(memberRepository.findAllById(any()))
                    .thenReturn(Arrays.stream(memberIds).map(this::member).toList());
        }

        private StudyMember activeMember(Long memberId) {
            return StudyMember.createMember(10L, memberId);
        }

        private Member member(Long id) {
            Member member = new Member(
                    "member" + id + "@example.com",
                    "password",
                    "member" + id
            );
            ReflectionTestUtils.setField(member, "id", id);
            return member;
        }
    }

    private ScheduleReminderRow row(Long scheduleId, Instant startAt) {
        ScheduleReminderRow row = mock(ScheduleReminderRow.class);
        when(row.getScheduleId()).thenReturn(scheduleId);
        when(row.getStudyId()).thenReturn(10L);
        when(row.getStudyTitle()).thenReturn("검증 스터디");
        when(row.getLeaderId()).thenReturn(1L);
        when(row.getStartAt()).thenReturn(startAt);
        when(row.getMeetingPlace()).thenReturn("옥수역");
        return row;
    }
}

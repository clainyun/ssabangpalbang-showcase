package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.response.MemberVisitCalendarResponse;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.repository.ScheduleRepository;
import com.ssafy.ssabangpalbang.study.repository.VisitCalendarRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberVisitCalendarServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    private MemberVisitCalendarService service;

    @BeforeEach
    void setUp() {
        service = new MemberVisitCalendarService(
                memberRepository,
                scheduleRepository
        );
    }

    @Test
    void 서울_시간_월_범위로_조회하고_같은_날짜의_일정을_묶는다() {
        Member member = activeMember();
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        List<VisitCalendarRow> visits = List.of(
                visitRow(
                        7L,
                        10L,
                        "옥수동 주말 임장",
                        "래미안 옥수 리버젠",
                        "2026-07-27T06:00:00Z",
                        "SCHEDULED"
                ),
                visitRow(
                        8L,
                        11L,
                        "성수동 저녁 임장",
                        "성수 트리마제",
                        "2026-07-27T10:00:00Z",
                        "COMPLETED"
                ),
                visitRow(
                        9L,
                        12L,
                        "한남동 임장",
                        "한남더힐",
                        "2026-07-28T01:00:00Z",
                        "SCHEDULED"
                )
        );
        when(scheduleRepository.findVisitCalendar(
                1L,
                Instant.parse("2026-06-30T15:00:00Z"),
                Instant.parse("2026-07-31T15:00:00Z")
        )).thenReturn(visits);

        MemberVisitCalendarResponse response = service.getVisitCalendar(
                1L,
                2026,
                7
        );

        assertThat(response.monthlyVisitCount()).isEqualTo(3);
        assertThat(response.dates()).hasSize(2);
        assertThat(response.dates().get(0).date().toString())
                .isEqualTo("2026-07-27");
        assertThat(response.dates().get(0).visits())
                .extracting(MemberVisitCalendarResponse.VisitResponse::scheduleId)
                .containsExactly(7L, 8L);
        assertThat(response.dates().get(0).visits().get(0).startAt()
                .getOffset().toString()).isEqualTo("+09:00");
        assertThat(response.dates().get(1).date().toString())
                .isEqualTo("2026-07-28");
    }

    @Test
    void 일정이_없으면_빈_dates와_0건을_반환한다() {
        Member member = activeMember();
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));
        when(scheduleRepository.findVisitCalendar(
                1L,
                Instant.parse("2026-06-30T15:00:00Z"),
                Instant.parse("2026-07-31T15:00:00Z")
        )).thenReturn(List.of());

        MemberVisitCalendarResponse response = service.getVisitCalendar(
                1L,
                2026,
                7
        );

        assertThat(response.monthlyVisitCount()).isZero();
        assertThat(response.dates()).isEmpty();
    }

    @Test
    void 잘못된_연도와_월은_각각_명세_오류를_반환한다() {
        assertThatThrownBy(() -> service.getVisitCalendar(1L, 1999, 7))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ErrorCode.MEMBER_VISIT_CALENDAR_YEAR_INVALID
                                )
                );
        assertThatThrownBy(() -> service.getVisitCalendar(1L, 2026, 13))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ErrorCode.MEMBER_VISIT_CALENDAR_MONTH_INVALID
                                )
                );

        verify(memberRepository, never()).findById(1L);
        verify(scheduleRepository, never()).findVisitCalendar(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void 존재하지_않거나_탈퇴한_회원은_404로_처리한다() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVisitCalendar(99L, 2026, 7))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND)
                );

        verify(scheduleRepository, never()).findVisitCalendar(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private Member activeMember() {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(member.getDeletedAt()).thenReturn(null);
        return member;
    }

    private VisitCalendarRow visitRow(
            Long scheduleId,
            Long studyId,
            String studyTitle,
            String apartmentName,
            String startAt,
            String scheduleStatus
    ) {
        VisitCalendarRow row = mock(VisitCalendarRow.class);
        when(row.getScheduleId()).thenReturn(scheduleId);
        when(row.getStudyId()).thenReturn(studyId);
        when(row.getStudyTitle()).thenReturn(studyTitle);
        when(row.getApartmentName()).thenReturn(apartmentName);
        when(row.getStartAt()).thenReturn(Instant.parse(startAt));
        when(row.getScheduleStatus()).thenReturn(scheduleStatus);
        return row;
    }
}

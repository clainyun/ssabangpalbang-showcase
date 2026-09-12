package com.ssafy.ssabangpalbang.member.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.response.MemberVisitCalendarResponse;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.repository.ScheduleRepository;
import com.ssafy.ssabangpalbang.study.repository.VisitCalendarRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemberVisitCalendarService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private final MemberRepository memberRepository;
    private final ScheduleRepository scheduleRepository;

    @Transactional(readOnly = true)
    public MemberVisitCalendarResponse getVisitCalendar(
            Long memberId,
            int year,
            int month
    ) {
        validatePeriod(year, month);
        validateActiveMember(memberId);

        YearMonth requestedMonth = YearMonth.of(year, month);
        Instant startInclusive = requestedMonth.atDay(1)
                .atStartOfDay(SEOUL)
                .toInstant();
        Instant endExclusive = requestedMonth.plusMonths(1)
                .atDay(1)
                .atStartOfDay(SEOUL)
                .toInstant();

        List<VisitCalendarRow> visits = scheduleRepository.findVisitCalendar(
                memberId,
                startInclusive,
                endExclusive
        );
        return MemberVisitCalendarResponse.from(year, month, visits);
    }

    private void validatePeriod(int year, int month) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new BusinessException(
                    ErrorCode.MEMBER_VISIT_CALENDAR_YEAR_INVALID,
                    Map.of(
                            "field", "year",
                            "reason", "연도는 2000 이상 2100 이하이어야 합니다."
                    )
            );
        }
        if (month < 1 || month > 12) {
            throw new BusinessException(
                    ErrorCode.MEMBER_VISIT_CALENDAR_MONTH_INVALID,
                    Map.of(
                            "field", "month",
                            "reason", "월은 1 이상 12 이하이어야 합니다."
                    )
            );
        }
    }

    private void validateActiveMember(Long memberId) {
        memberRepository.findById(memberId)
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .filter(member -> member.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
    }
}

package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.study.domain.ScheduleStatus;
import com.ssafy.ssabangpalbang.study.repository.VisitCalendarRow;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MemberVisitCalendarResponse(
        int year,
        int month,
        int monthlyVisitCount,
        List<VisitDateResponse> dates
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static MemberVisitCalendarResponse from(
            int year,
            int month,
            List<VisitCalendarRow> rows
    ) {
        Map<LocalDate, List<VisitResponse>> visitsByDate =
                new LinkedHashMap<>();

        for (VisitCalendarRow row : rows) {
            LocalDate visitDate = row.getStartAt()
                    .atZone(SEOUL)
                    .toLocalDate();
            visitsByDate.computeIfAbsent(
                    visitDate,
                    ignored -> new ArrayList<>()
            ).add(VisitResponse.from(row));
        }

        List<VisitDateResponse> dates = visitsByDate.entrySet()
                .stream()
                .map(entry -> new VisitDateResponse(
                        entry.getKey(),
                        List.copyOf(entry.getValue())
                ))
                .toList();

        return new MemberVisitCalendarResponse(
                year,
                month,
                rows.size(),
                dates
        );
    }

    public record VisitDateResponse(
            LocalDate date,
            List<VisitResponse> visits
    ) {
    }

    public record VisitResponse(
            Long scheduleId,
            Long studyId,
            String studyTitle,
            String apartmentName,
            OffsetDateTime startAt,
            ScheduleStatus scheduleStatus
    ) {

        private static VisitResponse from(VisitCalendarRow row) {
            return new VisitResponse(
                    row.getScheduleId(),
                    row.getStudyId(),
                    row.getStudyTitle(),
                    row.getApartmentName(),
                    OffsetDateTime.ofInstant(row.getStartAt(), SEOUL),
                    ScheduleStatus.valueOf(row.getScheduleStatus())
            );
        }
    }
}

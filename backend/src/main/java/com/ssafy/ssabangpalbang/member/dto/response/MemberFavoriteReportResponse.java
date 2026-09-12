package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.report.repository.FavoriteReportRow;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record MemberFavoriteReportResponse(
        Long reportId,
        String title,
        String summary,
        List<String> analysisTags,
        ApartmentSummary apartment,
        boolean favoritedByMe,
        OffsetDateTime completedAt,
        OffsetDateTime favoritedAt
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static MemberFavoriteReportResponse from(
            FavoriteReportRow row,
            String title,
            String summary,
            List<String> analysisTags,
            String apartmentImageUrl
    ) {
        return new MemberFavoriteReportResponse(
                row.getReportId(),
                title,
                summary,
                analysisTags,
                new ApartmentSummary(
                        row.getApartmentId(),
                        row.getApartmentName(),
                        row.getApartmentAddress(),
                        row.getDongName(),
                        apartmentImageUrl
                ),
                true,
                toOffsetDateTime(row.getCompletedAt()),
                toOffsetDateTime(row.getFavoritedAt())
        );
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null
                ? null
                : instant.atZone(SEOUL).toOffsetDateTime();
    }

    public record ApartmentSummary(
            Long apartmentId,
            String name,
            String address,
            /** 법정동 이름(예: 역삼동). 공공데이터 미매칭이면 null. */
            String dongName,
            /** 단지 대표 이미지 공개 URL. 매칭 이미지가 없거나 미설정이면 null. */
            String imageUrl
    ) {
    }
}

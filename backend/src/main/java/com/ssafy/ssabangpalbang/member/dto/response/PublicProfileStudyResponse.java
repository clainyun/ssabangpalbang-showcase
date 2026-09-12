package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.study.repository.PublicProfileStudyRow;

public record PublicProfileStudyResponse(
        Long studyId,
        String title,
        String status,
        String role,
        ApartmentSummary apartment
) {

    public static PublicProfileStudyResponse from(
            PublicProfileStudyRow row
    ) {
        return new PublicProfileStudyResponse(
                row.getStudyId(),
                row.getTitle(),
                row.getStatus(),
                row.getRole(),
                new ApartmentSummary(
                        row.getApartmentId(),
                        row.getApartmentName()
                )
        );
    }

    public record ApartmentSummary(
            Long apartmentId,
            String name
    ) {
    }
}

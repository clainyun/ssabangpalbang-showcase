package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.apartment.dto.response.ApartmentLatestTransaction;

import java.time.OffsetDateTime;

public record MemberFavoriteApartmentResponse(
        Long apartmentId,
        String name,
        String address,
        String districtName,
        String dongName,
        Integer householdCount,
        ApartmentLatestTransaction latestTransaction,
        int recruitingStudyCount,
        int completedReportCount,
        OffsetDateTime favoritedAt,
        /** 단지 대표 이미지 공개 URL. 매칭 이미지가 없거나 미설정이면 null. */
        String imageUrl
) {
}

package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberPreference;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewSummaryResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record MemberProfileResponse(
        Long memberId,
        String email,
        String nickname,
        String profileImageUrl,
        String selectedCharacterId,
        String ageGroup,
        boolean ageGroupPublicAgreed,
        boolean serviceNotificationAgreed,
        boolean adNotificationAgreed,
        Preference preference,
        boolean onboardingCompleted,
        long joinedDays,
        long fieldVisitCompletedCount,
        Summary summary,
        MemberReviewSummaryResponse reviewSummary,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static MemberProfileResponse from(
            Member member,
            MemberPreference preference,
            long studyCount,
            long reportCount,
            long followingCount,
            long fieldVisitCompletedCount,
            MemberReviewSummaryResponse reviewSummary,
            Instant now
    ) {
        return new MemberProfileResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getProfileImageUrl(),
                member.getSelectedCharacterId(),
                member.getAgeGroup(),
                member.isAgeGroupPublicAgreed(),
                member.isServiceNotificationAgreed(),
                member.isAdNotificationAgreed(),
                Preference.from(preference),
                preference != null,
                joinedDays(member.getCreatedAt(), now),
                fieldVisitCompletedCount,
                new Summary(studyCount, reportCount, followingCount),
                reviewSummary,
                toSeoulDateTime(member.getCreatedAt()),
                toSeoulDateTime(member.getUpdatedAt())
        );
    }

    private static long joinedDays(Instant createdAt, Instant now) {
        LocalDate joinedDate = LocalDate.ofInstant(createdAt, SEOUL_ZONE_ID);
        LocalDate currentDate = LocalDate.ofInstant(now, SEOUL_ZONE_ID);
        return ChronoUnit.DAYS.between(joinedDate, currentDate);
    }

    private static OffsetDateTime toSeoulDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, SEOUL_ZONE_ID);
    }

    public record Preference(
            String purpose,
            String maritalStatus,
            Boolean hasVehicle,
            Boolean hasChildren,
            List<String> priorities
    ) {

        private static Preference from(MemberPreference preference) {
            if (preference == null) {
                return null;
            }
            return new Preference(
                    preference.getPurpose(),
                    preference.getMaritalStatus(),
                    preference.getHasVehicle(),
                    preference.getHasChildren(),
                    preference.getPriorities()
            );
        }
    }

    public record Summary(
            long studyCount,
            long reportCount,
            long followingCount
    ) {
    }
}

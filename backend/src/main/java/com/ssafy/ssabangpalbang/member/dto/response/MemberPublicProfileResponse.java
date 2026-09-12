package com.ssafy.ssabangpalbang.member.dto.response;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberPreference;
import com.ssafy.ssabangpalbang.member.dto.request.MemberPublicProfileSection;
import com.ssafy.ssabangpalbang.global.response.PageResponse;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewSummaryResponse;

public record MemberPublicProfileResponse(
        Long memberId,
        String nickname,
        String profileImageUrl,
        String selectedCharacterId,
        String ageGroup,
        String interestRegion,
        long participatingStudyCount,
        long reportCount,
        long followingCount,
        long fieldVisitCompletedCount,
        MemberReviewSummaryResponse reviewSummary,
        boolean isMe,
        boolean isFollowing,
        boolean canFollow,
        boolean canSendMessage,
        MemberPublicProfileSection section,
        PageResponse<PublicProfileStudyResponse> studies,
        PageResponse<PublicProfileReportResponse> reports,
        PageResponse<PublicProfileFollowingResponse> followings
) {

    public static MemberPublicProfileResponse from(
            Member member,
            MemberPreference preference,
            long participatingStudyCount,
            long reportCount,
            long followingCount,
            long fieldVisitCompletedCount,
            MemberReviewSummaryResponse reviewSummary,
            boolean isMe,
            boolean isFollowing,
            MemberPublicProfileSection section,
            PageResponse<PublicProfileStudyResponse> studies,
            PageResponse<PublicProfileReportResponse> reports,
            PageResponse<PublicProfileFollowingResponse> followings
    ) {
        String publicAgeGroup = member.isAgeGroupPublicAgreed()
                ? member.getAgeGroup()
                : null;
        String publicInterestRegion = preference != null
                && preference.isInterestRegionPublicAgreed()
                ? preference.getInterestRegion()
                : null;

        return new MemberPublicProfileResponse(
                member.getId(),
                member.getNickname(),
                member.getProfileImageUrl(),
                member.getSelectedCharacterId(),
                publicAgeGroup,
                publicInterestRegion,
                participatingStudyCount,
                reportCount,
                followingCount,
                fieldVisitCompletedCount,
                reviewSummary,
                isMe,
                isFollowing,
                !isMe && !isFollowing,
                !isMe && isFollowing,
                section,
                studies,
                reports,
                followings
        );
    }
}

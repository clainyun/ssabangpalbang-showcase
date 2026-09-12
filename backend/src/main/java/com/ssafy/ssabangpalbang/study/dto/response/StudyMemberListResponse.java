package com.ssafy.ssabangpalbang.study.dto.response;

import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record StudyMemberListResponse(
        Long studyId,
        boolean isLeader,
        int currentMemberCount,
        int capacity,
        List<MemberItem> members
) {
    public record MemberItem(
            Long memberId,
            String nickname,
            String profileImageUrl,
            String selectedCharacterId,
            String role,
            boolean canKick,
            boolean reviewedByMe,
            String joinedAt
    ) {
        private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

        public static MemberItem from(
                StudyMember studyMember,
                Member member,
                boolean canKick,
                boolean reviewedByMe
        ) {
            return new MemberItem(
                    studyMember.getMemberId(),
                    member.getNickname(),
                    member.getProfileImageUrl(),
                    member.getSelectedCharacterId(),
                    studyMember.getRole().name(),
                    canKick,
                    reviewedByMe,
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                            studyMember.getJoinedAt().atZone(SEOUL_ZONE_ID))
            );
        }
    }
}

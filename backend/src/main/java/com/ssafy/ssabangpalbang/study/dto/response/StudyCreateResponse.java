package com.ssafy.ssabangpalbang.study.dto.response;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.study.domain.Study;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record StudyCreateResponse(
        Long studyId,
        String title,
        String intro,
        String goal,
        String purpose,
        String status,
        Integer capacity,
        Integer currentMemberCount,
        ApartmentSummary apartment,
        LeaderSummary leader,
        OffsetDateTime createdAt
) {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static StudyCreateResponse from(
            Study study,
            Apartment apartment,
            Member leader
    ) {
        return new StudyCreateResponse(
                study.getId(),
                study.getTitle(),
                study.getIntro(),
                study.getGoal(),
                study.getPurpose().name(),
                study.getStatus().name(),
                study.getCapacity(),
                1,
                new ApartmentSummary(
                        apartment.getId(),
                        apartment.getName(),
                        apartment.getAddress()
                ),
                new LeaderSummary(
                        leader.getId(),
                        leader.getNickname(),
                        leader.getSelectedCharacterId()
                ),
                OffsetDateTime.ofInstant(study.getCreatedAt(), SEOUL_ZONE_ID)
        );
    }

    public record ApartmentSummary(
            Long apartmentId,
            String name,
            String address
    ) {
    }

    public record LeaderSummary(
            Long memberId,
            String nickname,
            String selectedCharacterId
    ) {
    }
}

package com.ssafy.ssabangpalbang.fieldvisit.dto;

import java.util.List;

/**
 * AI-002가 FastAPI에 전달할 개인화 원본 데이터다.
 *
 * <p>필드 범위는 사용자 확정 사항(2026-07-29 AI-002 개인화 기준 승인)을 그대로
 * 따른다. 저장소에 실제로 존재하지 않는 필드({@code budget},
 * {@code interestRegion}, {@code interestRegionPublicAgreed},
 * {@code householdType})는 포함하지 않는다.</p>
 *
 * <p>회원의 {@code purpose}(임장을 하는 일반적 목적)와 스터디의 {@code purpose}
 * (이번 스터디의 공통 목적)는 의미가 다르므로, 내부 계약에서도
 * {@code memberPurpose}·{@code studyPurpose}로 이름을 구분한다.</p>
 */
public record ChecklistPersonalizationInput(
        MemberOnboardingSection member,
        ApartmentSection apartment,
        StudySection study
) {

    /**
     * 회원 온보딩 정보 중 AI 개인화에 사용하는 필드만 담는다.
     *
     * <p>{@code purpose}는 {@link com.ssafy.ssabangpalbang.member.domain.MemberPreference}에,
     * {@code ageGroup}은 {@link com.ssafy.ssabangpalbang.member.domain.Member}에 저장되어
     * 있다(서로 다른 Entity). {@code ageGroupPublicAgreed}(공개 프로필 노출 동의)와
     * {@code selectedCharacterId}(UI 캐릭터 선택값)는 AI 입력 의미와 무관하므로
     * 의도적으로 제외한다.</p>
     *
     * <p>{@code priorities}는 회원이 정한 중요도 순서이므로, 이 레코드를 만드는
     * 쪽({@link com.ssafy.ssabangpalbang.fieldvisit.service.ChecklistPersonalizationReader})은
     * 원본 배열 순서를 그대로 보존해서 담아야 한다.</p>
     */
    public record MemberOnboardingSection(
            String memberPurpose,
            String maritalStatus,
            Boolean hasVehicle,
            Boolean hasChildren,
            List<String> priorities,
            String ageGroup
    ) {
    }

    /**
     * 대상 아파트 정보다. {@link com.ssafy.ssabangpalbang.apartment.domain.Apartment}에
     * 실제로 존재하는 필드만 담는다.
     */
    public record ApartmentSection(
            Long apartmentId,
            String name,
            String address,
            String districtName,
            String dongName,
            Integer householdCount,
            String completionYearMonth,
            Integer parkingSpaceCount
    ) {
    }

    /**
     * 스터디 정보다. {@code studyPurpose}는 스터디의 공통 목적
     * ({@link com.ssafy.ssabangpalbang.study.domain.Study#getPurpose()}), {@code goal}은
     * 스터디 생성 시 입력한 목표 텍스트다.
     */
    public record StudySection(
            Long studyId,
            String studyPurpose,
            String goal
    ) {
    }
}

package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.fieldvisit.dto.ChecklistPersonalizationInput;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberPreference;
import com.ssafy.ssabangpalbang.member.repository.MemberPreferenceRepository;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * AI 체크리스트 생성을 위한 개인화 원본 데이터를 조립한다.
 *
 * <p>{@code ageGroup}은 {@link Member}에, 나머지 온보딩 필드는
 * {@link MemberPreference}에 있다. {@code priorities} 순서는
 * {@link MemberPreference#getPriorities()}가 보존한 그대로 전달한다.</p>
 *
 * <p>허용값 목록은 {@code MemberService}의 온보딩 검증과 동일해야 한다.
 * member 바운디드 컨텍스트의 private 상수를 직접 참조할 수 없어 여기서
 * 미러링하며, 계약은 단위 테스트로 고정한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ChecklistPersonalizationReader {

    static final List<String> ALLOWED_PURPOSES = List.of(
            "RESIDENCE", "INVESTMENT", "STUDY"
    );
    static final List<String> ALLOWED_MARITAL_STATUSES = List.of(
            "SINGLE", "MARRIED"
    );
    static final List<String> ALLOWED_AGE_GROUPS = List.of(
            "TEENS", "TWENTIES", "THIRTIES",
            "FORTIES", "FIFTIES", "SIXTIES_PLUS"
    );
    static final List<String> ALLOWED_PRIORITIES = List.of(
            "TRANSPORT", "SAFETY", "EDUCATION", "COMMERCIAL",
            "WALKABILITY", "GREEN_SPACE", "PARKING", "NOISE"
    );
    static final int PRIORITIES_MIN = 1;
    static final int PRIORITIES_MAX = 4;

    private final MemberRepository memberRepository;
    private final MemberPreferenceRepository memberPreferenceRepository;
    private final ApartmentRepository apartmentRepository;
    private final StudyRepository studyRepository;

    public ChecklistPersonalizationResult read(Long memberId, Long studyId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Study study = studyRepository.findByIdAndDeletedAtIsNull(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDY_NOT_FOUND));
        Apartment apartment = apartmentRepository.findById(study.getApartmentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.APARTMENT_NOT_FOUND));

        Optional<MemberPreference> preferenceOpt = memberPreferenceRepository
                .findByMemberId(memberId);

        String ageGroup = member.getAgeGroup();
        String memberPurpose = preferenceOpt.map(MemberPreference::getPurpose).orElse(null);
        String maritalStatus = preferenceOpt.map(MemberPreference::getMaritalStatus)
                .orElse(null);
        Boolean hasVehicle = preferenceOpt.map(MemberPreference::getHasVehicle).orElse(null);
        Boolean hasChildren = preferenceOpt.map(MemberPreference::getHasChildren).orElse(null);
        List<String> priorities = preferenceOpt.map(MemberPreference::getPriorities)
                .orElse(List.of());

        ChecklistPersonalizationInput.MemberOnboardingSection memberSection =
                new ChecklistPersonalizationInput.MemberOnboardingSection(
                        memberPurpose,
                        maritalStatus,
                        hasVehicle,
                        hasChildren,
                        priorities,
                        ageGroup
                );

        ChecklistPersonalizationInput.ApartmentSection apartmentSection =
                new ChecklistPersonalizationInput.ApartmentSection(
                        apartment.getId(),
                        apartment.getName(),
                        apartment.getAddress(),
                        apartment.getDistrictName(),
                        apartment.getDongName(),
                        apartment.getHouseholdCount(),
                        apartment.getCompletionYearMonth(),
                        apartment.getParkingSpaceCount()
                );

        ChecklistPersonalizationInput.StudySection studySection =
                new ChecklistPersonalizationInput.StudySection(
                        study.getId(),
                        study.getPurpose() == null ? null : study.getPurpose().name(),
                        study.getGoal()
                );

        ChecklistPersonalizationInput input = new ChecklistPersonalizationInput(
                memberSection,
                apartmentSection,
                studySection
        );

        boolean insufficient = isInsufficient(preferenceOpt.isEmpty(), memberSection);
        return new ChecklistPersonalizationResult(input, insufficient);
    }

    private boolean isInsufficient(
            boolean preferenceMissing,
            ChecklistPersonalizationInput.MemberOnboardingSection member
    ) {
        if (preferenceMissing) {
            return true;
        }
        if (!ALLOWED_PURPOSES.contains(member.memberPurpose())) {
            return true;
        }
        if (!ALLOWED_MARITAL_STATUSES.contains(member.maritalStatus())) {
            return true;
        }
        if (!ALLOWED_AGE_GROUPS.contains(member.ageGroup())) {
            return true;
        }
        if (member.hasVehicle() == null || member.hasChildren() == null) {
            return true;
        }
        return !isValidPriorities(member.priorities());
    }

    private boolean isValidPriorities(List<String> priorities) {
        if (priorities == null
                || priorities.size() < PRIORITIES_MIN
                || priorities.size() > PRIORITIES_MAX) {
            return false;
        }
        Set<String> unique = new HashSet<>();
        for (String priority : priorities) {
            if (priority == null || !ALLOWED_PRIORITIES.contains(priority)) {
                return false;
            }
            if (!unique.add(priority)) {
                return false;
            }
        }
        return true;
    }
}

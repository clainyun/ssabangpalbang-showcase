package com.ssafy.ssabangpalbang.fieldvisit.service;

import com.ssafy.ssabangpalbang.apartment.domain.Apartment;
import com.ssafy.ssabangpalbang.apartment.repository.ApartmentRepository;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberPreference;
import com.ssafy.ssabangpalbang.member.repository.MemberPreferenceRepository;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyPurpose;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChecklistPersonalizationReaderTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long STUDY_ID = 2L;
    private static final Long APARTMENT_ID = 3L;

    private MemberRepository memberRepository;
    private MemberPreferenceRepository memberPreferenceRepository;
    private ApartmentRepository apartmentRepository;
    private StudyRepository studyRepository;
    private ChecklistPersonalizationReader reader;
    private Member member;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        memberPreferenceRepository = mock(MemberPreferenceRepository.class);
        apartmentRepository = mock(ApartmentRepository.class);
        studyRepository = mock(StudyRepository.class);
        reader = new ChecklistPersonalizationReader(
                memberRepository,
                memberPreferenceRepository,
                apartmentRepository,
                studyRepository
        );

        member = mock(Member.class);
        when(member.getAgeGroup()).thenReturn("THIRTIES");
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

        Study study = mock(Study.class);
        when(study.getId()).thenReturn(STUDY_ID);
        when(study.getApartmentId()).thenReturn(APARTMENT_ID);
        when(study.getPurpose()).thenReturn(StudyPurpose.INVESTMENT);
        when(study.getGoal()).thenReturn("내 집 마련");
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));

        Apartment apartment = mock(Apartment.class);
        when(apartment.getId()).thenReturn(APARTMENT_ID);
        when(apartment.getName()).thenReturn("테스트 아파트");
        when(apartment.getAddress()).thenReturn("서울시 어딘가");
        when(apartment.getDistrictName()).thenReturn("강남구");
        when(apartment.getDongName()).thenReturn("역삼동");
        when(apartment.getHouseholdCount()).thenReturn(500);
        when(apartment.getCompletionYearMonth()).thenReturn("2020-05");
        when(apartment.getParkingSpaceCount()).thenReturn(600);
        when(apartmentRepository.findById(APARTMENT_ID)).thenReturn(Optional.of(apartment));
    }

    private MemberPreference validPreference() {
        MemberPreference preference = mock(MemberPreference.class);
        when(preference.getPurpose()).thenReturn("RESIDENCE");
        when(preference.getMaritalStatus()).thenReturn("MARRIED");
        when(preference.getHasVehicle()).thenReturn(true);
        when(preference.getHasChildren()).thenReturn(false);
        when(preference.getPriorities())
                .thenReturn(List.of("SAFETY", "EDUCATION", "TRANSPORT"));
        return preference;
    }

    private void stubPreference(MemberPreference preference) {
        when(memberPreferenceRepository.findByMemberId(MEMBER_ID))
                .thenReturn(Optional.of(preference));
    }

    @Test
    void 온보딩이_모두_채워져있으면_insufficient는_false다() {
        stubPreference(validPreference());
        assertThat(reader.read(MEMBER_ID, STUDY_ID).insufficient()).isFalse();
    }

    @Test
    void memberPurpose와_studyPurpose는_서로_다른_필드에_담긴다() {
        stubPreference(validPreference());
        var input = reader.read(MEMBER_ID, STUDY_ID).input();
        assertThat(input.member().memberPurpose()).isEqualTo("RESIDENCE");
        assertThat(input.study().studyPurpose()).isEqualTo("INVESTMENT");
    }

    @Test
    void priorities는_저장된_순서_그대로_보존된다() {
        MemberPreference preference = validPreference();
        when(preference.getPriorities())
                .thenReturn(List.of("PARKING", "NOISE", "SAFETY"));
        stubPreference(preference);
        assertThat(reader.read(MEMBER_ID, STUDY_ID).input().member().priorities())
                .containsExactly("PARKING", "NOISE", "SAFETY");
    }

    @Test
    void 잘못된_purpose이면_insufficient다() {
        MemberPreference preference = validPreference();
        when(preference.getPurpose()).thenReturn("WRONG_VALUE");
        stubPreference(preference);
        assertThat(reader.read(MEMBER_ID, STUDY_ID).insufficient()).isTrue();
    }

    @Test
    void 잘못된_maritalStatus이면_insufficient다() {
        MemberPreference preference = validPreference();
        when(preference.getMaritalStatus()).thenReturn("UNKNOWN");
        stubPreference(preference);
        assertThat(reader.read(MEMBER_ID, STUDY_ID).insufficient()).isTrue();
    }

    @Test
    void 잘못된_ageGroup이면_insufficient다() {
        when(member.getAgeGroup()).thenReturn("SEVENTIES");
        stubPreference(validPreference());
        assertThat(reader.read(MEMBER_ID, STUDY_ID).insufficient()).isTrue();
    }

    @Test
    void priorities가_0개이면_insufficient다() {
        MemberPreference preference = validPreference();
        when(preference.getPriorities()).thenReturn(List.of());
        stubPreference(preference);
        assertThat(reader.read(MEMBER_ID, STUDY_ID).insufficient()).isTrue();
    }

    @Test
    void priorities가_5개이면_insufficient다() {
        MemberPreference preference = validPreference();
        when(preference.getPriorities()).thenReturn(List.of(
                "TRANSPORT", "SAFETY", "EDUCATION", "COMMERCIAL", "PARKING"
        ));
        stubPreference(preference);
        assertThat(reader.read(MEMBER_ID, STUDY_ID).insufficient()).isTrue();
    }

    @Test
    void priorities가_중복이면_insufficient다() {
        MemberPreference preference = validPreference();
        when(preference.getPriorities())
                .thenReturn(List.of("TRANSPORT", "TRANSPORT", "SAFETY"));
        stubPreference(preference);
        assertThat(reader.read(MEMBER_ID, STUDY_ID).insufficient()).isTrue();
    }

    @Test
    void priorities에_허용되지_않은_값이_있으면_insufficient다() {
        MemberPreference preference = validPreference();
        when(preference.getPriorities()).thenReturn(List.of("INVALID_VALUE"));
        stubPreference(preference);
        assertThat(reader.read(MEMBER_ID, STUDY_ID).insufficient()).isTrue();
    }

    @Test
    void MemberPreference_행이_없으면_insufficient다() {
        when(memberPreferenceRepository.findByMemberId(MEMBER_ID))
                .thenReturn(Optional.empty());
        assertThat(reader.read(MEMBER_ID, STUDY_ID).insufficient()).isTrue();
    }

    @Test
    void 회원이_없으면_MEMBER_NOT_FOUND() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> reader.read(MEMBER_ID, STUDY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }
}

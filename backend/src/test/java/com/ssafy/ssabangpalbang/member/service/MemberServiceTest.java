package com.ssafy.ssabangpalbang.member.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberPreference;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.dto.request.MemberProfileUpdateRequest;
import com.ssafy.ssabangpalbang.member.dto.request.MemberPublicProfileSection;
import com.ssafy.ssabangpalbang.member.dto.request.MemberPublicStudyStatus;
import com.ssafy.ssabangpalbang.member.dto.request.OnboardingRequest;
import com.ssafy.ssabangpalbang.member.dto.response.MemberProfileResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberProfileUpdateResponse;
import com.ssafy.ssabangpalbang.member.dto.response.MemberPublicProfileResponse;
import com.ssafy.ssabangpalbang.member.dto.response.OnboardingResponse;
import com.ssafy.ssabangpalbang.member.repository.MemberPreferenceRepository;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.member.repository.PublicProfileFollowingRow;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.repository.FieldParticipantRepository;
import com.ssafy.ssabangpalbang.report.repository.PublicProfileReportRow;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewSummaryResponse;
import com.ssafy.ssabangpalbang.review.dto.response.ReviewTagCountView;
import com.ssafy.ssabangpalbang.review.service.MemberReviewSummaryReader;
import com.ssafy.ssabangpalbang.study.repository.PublicProfileStudyRow;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final Instant NOW = Instant.parse(
            "2026-07-28T06:00:00Z"
    );
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberPreferenceRepository memberPreferenceRepository;

    @Mock
    private StudyRepository studyRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private MemberReviewSummaryReader memberReviewSummaryReader;

    @Mock
    private FieldParticipantRepository fieldParticipantRepository;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(
                memberRepository,
                memberPreferenceRepository,
                studyRepository,
                reportRepository,
                memberReviewSummaryReader,
                fieldParticipantRepository,
                new ObjectMapper(),
                CLOCK
        );
    }

    @Test
    void 내_프로필과_선호정보_요약수를_조회한다() {
        Member member = activeMember(1L);
        member.updateOnboardingProfile("FIFTIES", false, "PALBANG_DOG");
        ReflectionTestUtils.setField(
                member,
                "profileImageUrl",
                "https://example.com/profile.png"
        );
        ReflectionTestUtils.setField(member, "ageGroupPublicAgreed", true);
        ReflectionTestUtils.setField(
                member,
                "adNotificationAgreed",
                true
        );
        ReflectionTestUtils.setField(
                member,
                "createdAt",
                Instant.parse("2026-06-25T15:00:00Z")
        );
        ReflectionTestUtils.setField(
                member,
                "updatedAt",
                Instant.parse("2026-07-28T05:00:00Z")
        );
        MemberPreference preference = new MemberPreference(1L);
        preference.update(
                "RESIDENCE",
                "NEWLYWED",
                "MARRIED",
                true,
                false,
                "500M_TO_700M",
                "서울특별시 성동구",
                List.of("TRANSPORT", "SAFETY", "PARKING")
        );
        ReflectionTestUtils.setField(
                preference,
                "interestRegionPublicAgreed",
                true
        );
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberPreferenceRepository.findByMemberId(1L))
                .thenReturn(Optional.of(preference));
        when(memberRepository.countAccessibleStudies(1L)).thenReturn(3L);
        when(memberRepository.countAccessibleDoneReports(1L)).thenReturn(2L);
        when(memberRepository.countFollowing(1L)).thenReturn(5L);
        when(fieldParticipantRepository.countByMemberIdAndStatus(
                1L,
                FieldParticipantStatus.ENDED
        )).thenReturn(7L);
        when(memberReviewSummaryReader.read(1L))
                .thenReturn(new MemberReviewSummaryResponse(
                        List.of(new ReviewTagCountView(
                                "PUNCTUAL",
                                "시간 약속을 잘 지켜요",
                                "⏰",
                                "PERSON",
                                8
                        )),
                        9L,
                        12L
                ));

        MemberProfileResponse response = memberService.getMyProfile(1L);

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.profileImageUrl())
                .isEqualTo("https://example.com/profile.png");
        assertThat(response.selectedCharacterId()).isEqualTo("PALBANG_DOG");
        assertThat(response.ageGroup()).isEqualTo("FIFTIES");
        assertThat(response.ageGroupPublicAgreed()).isTrue();
        assertThat(response.adNotificationAgreed()).isTrue();
        assertThat(response.preference().maritalStatus()).isEqualTo("MARRIED");
        assertThat(response.preference().hasVehicle()).isTrue();
        assertThat(response.preference().hasChildren()).isFalse();
        assertThat(response.preference().priorities()).containsExactly(
                "TRANSPORT",
                "SAFETY",
                "PARKING"
        );
        assertThat(response.onboardingCompleted()).isTrue();
        assertThat(response.joinedDays()).isEqualTo(32L);
        assertThat(response.fieldVisitCompletedCount()).isEqualTo(7L);
        assertThat(response.summary().studyCount()).isEqualTo(3L);
        assertThat(response.summary().reportCount()).isEqualTo(2L);
        assertThat(response.summary().followingCount()).isEqualTo(5L);
        assertThat(response.reviewSummary().topTags()).extracting("code")
                .containsExactly("PUNCTUAL");
        assertThat(response.reviewSummary().likeReceivedCount())
                .isEqualTo(9L);
        assertThat(response.reviewSummary().reviewCount()).isEqualTo(12L);
        assertThat(response.createdAt().getOffset())
                .isEqualTo(ZoneOffset.ofHours(9));
        assertThat(response.updatedAt().getOffset())
                .isEqualTo(ZoneOffset.ofHours(9));
    }

    @Test
    void 선호정보가_없으면_온보딩_미완료로_조회한다() {
        Member member = activeMember(1L);
        ReflectionTestUtils.setField(
                member,
                "createdAt",
                Instant.parse("2026-07-28T06:00:00Z")
        );
        ReflectionTestUtils.setField(
                member,
                "updatedAt",
                Instant.parse("2026-07-28T06:00:00Z")
        );
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberPreferenceRepository.findByMemberId(1L))
                .thenReturn(Optional.empty());

        MemberProfileResponse response = memberService.getMyProfile(1L);

        assertThat(response.preference()).isNull();
        assertThat(response.onboardingCompleted()).isFalse();
        assertThat(response.joinedDays()).isZero();
        assertThat(response.summary().studyCount()).isZero();
        assertThat(response.summary().reportCount()).isZero();
        assertThat(response.summary().followingCount()).isZero();
    }

    @Test
    void 존재하지_않는_회원의_프로필_조회를_거절한다() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.getMyProfile(99L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.MEMBER_NOT_FOUND
        );
        verifyNoInteractions(memberPreferenceRepository);
    }

    @Test
    void 탈퇴_회원의_프로필_조회를_거절한다() {
        Member member = activeMember(1L);
        ReflectionTestUtils.setField(
                member,
                "status",
                MemberStatus.WITHDRAWN
        );
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.getMyProfile(1L)
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.AUTH_MEMBER_WITHDRAWN
        );
        verifyNoInteractions(memberPreferenceRepository);
    }

    @Test
    void 공개_동의와_팔로우_관계를_반영해_공개_프로필을_조회한다() {
        Member viewer = activeMember(1L);
        Member target = activeMember(2L);
        target.updateOnboardingProfile("THIRTIES", false, "PALBANG_DOG");
        ReflectionTestUtils.setField(target, "nickname", "옥수탐방러");
        ReflectionTestUtils.setField(
                target,
                "profileImageUrl",
                "https://example.com/profile.png"
        );
        ReflectionTestUtils.setField(target, "ageGroupPublicAgreed", true);
        MemberPreference preference = preference(2L);
        ReflectionTestUtils.setField(
                preference,
                "interestRegionPublicAgreed",
                true
        );
        when(memberRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(target));
        when(memberPreferenceRepository.findByMemberId(2L))
                .thenReturn(Optional.of(preference));
        when(studyRepository.countPublicProfileStudies(2L)).thenReturn(4L);
        when(reportRepository.countPublicProfileReports(2L)).thenReturn(3L);
        when(memberRepository.countPublicProfileFollowings(2L))
                .thenReturn(5L);
        when(memberRepository.existsFollow(1L, 2L)).thenReturn(true);
        when(fieldParticipantRepository.countByMemberIdAndStatus(
                2L,
                FieldParticipantStatus.ENDED
        )).thenReturn(6L);
        when(memberReviewSummaryReader.read(2L))
                .thenReturn(new MemberReviewSummaryResponse(
                        List.of(new ReviewTagCountView(
                                "SHARES_INFO",
                                "정보 공유를 잘해요",
                                "📣",
                                "VISIT",
                                5
                        )),
                        4L,
                        4L
                ));
        PublicProfileStudyRow studyRow = mock(PublicProfileStudyRow.class);
        when(studyRow.getStudyId()).thenReturn(10L);
        when(studyRow.getTitle()).thenReturn("옥수동 주말 임장");
        when(studyRow.getStatus()).thenReturn("IN_PROGRESS");
        when(studyRow.getRole()).thenReturn("MEMBER");
        when(studyRow.getApartmentId()).thenReturn(15L);
        when(studyRow.getApartmentName())
                .thenReturn("래미안 옥수 리버젠");
        when(studyRepository.findPublicProfileStudies(
                2L,
                "ACTIVE",
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(
                List.of(studyRow),
                PageRequest.of(0, 20),
                1
        ));

        MemberPublicProfileResponse response = memberService
                .getPublicProfile(
                        1L,
                        2L,
                        MemberPublicProfileSection.STUDIES,
                        MemberPublicStudyStatus.ACTIVE,
                        0,
                        20
                );

        assertThat(response.memberId()).isEqualTo(2L);
        assertThat(response.nickname()).isEqualTo("옥수탐방러");
        assertThat(response.profileImageUrl())
                .isEqualTo("https://example.com/profile.png");
        assertThat(response.selectedCharacterId()).isEqualTo("PALBANG_DOG");
        assertThat(response.ageGroup()).isEqualTo("THIRTIES");
        assertThat(response.interestRegion())
                .isEqualTo("서울특별시 송파구");
        assertThat(response.participatingStudyCount()).isEqualTo(4L);
        assertThat(response.reportCount()).isEqualTo(3L);
        assertThat(response.followingCount()).isEqualTo(5L);
        assertThat(response.fieldVisitCompletedCount()).isEqualTo(6L);
        assertThat(response.reviewSummary().topTags()).extracting("code")
                .containsExactly("SHARES_INFO");
        assertThat(response.reviewSummary().likeReceivedCount())
                .isEqualTo(4L);
        assertThat(response.reviewSummary().reviewCount()).isEqualTo(4L);
        assertThat(response.isMe()).isFalse();
        assertThat(response.isFollowing()).isTrue();
        assertThat(response.canFollow()).isFalse();
        assertThat(response.canSendMessage()).isTrue();
        assertThat(response.section())
                .isEqualTo(MemberPublicProfileSection.STUDIES);
        assertThat(response.studies()).isNotNull();
        assertThat(response.studies().content()).hasSize(1);
        assertThat(response.studies().content().get(0).studyId())
                .isEqualTo(10L);
        assertThat(response.studies().content().get(0).apartment().name())
                .isEqualTo("래미안 옥수 리버젠");
        assertThat(response.reports()).isNull();
        assertThat(response.followings()).isNull();
    }

    @Test
    void 공개_프로필의_완료_리포트_목록을_조회한다() {
        Member viewer = activeMember(1L);
        Member target = activeMember(2L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(target));
        when(memberPreferenceRepository.findByMemberId(2L))
                .thenReturn(Optional.empty());

        PublicProfileReportRow row = mock(PublicProfileReportRow.class);
        when(row.getReportId()).thenReturn(48L);
        when(row.getResultJson()).thenReturn("""
                {
                  "title": "옥수동 임장 리포트",
                  "summary": "교통 접근성이 좋습니다.",
                  "analysisTags": ["교통 우수", "단지 경사"]
                }
                """);
        when(row.getFavoritedByMe()).thenReturn(true);
        when(row.getApartmentId()).thenReturn(15L);
        when(row.getApartmentName()).thenReturn("래미안 옥수 리버젠");
        when(row.getStudyId()).thenReturn(10L);
        when(row.getStudyTitle()).thenReturn("옥수동 주말 임장");
        when(row.getParticipantCount()).thenReturn(4L);
        when(row.getCompletedAt()).thenReturn(
                Instant.parse("2026-07-22T09:07:00Z")
        );
        when(reportRepository.findPublicProfileReports(
                1L,
                2L,
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(
                List.of(row),
                PageRequest.of(0, 20),
                1
        ));

        MemberPublicProfileResponse response = memberService
                .getPublicProfile(
                        1L,
                        2L,
                        MemberPublicProfileSection.REPORTS,
                        MemberPublicStudyStatus.ACTIVE,
                        0,
                        20
                );

        assertThat(response.section())
                .isEqualTo(MemberPublicProfileSection.REPORTS);
        assertThat(response.studies()).isNull();
        assertThat(response.reports().content()).hasSize(1);
        assertThat(response.reports().content().get(0).reportId())
                .isEqualTo(48L);
        assertThat(response.reports().content().get(0).title())
                .isEqualTo("옥수동 임장 리포트");
        assertThat(response.reports().content().get(0).analysisTags())
                .containsExactly("교통 우수", "단지 경사");
        assertThat(response.reports().content().get(0).favoritedByMe())
                .isTrue();
        assertThat(response.reports().content().get(0).completedAt()
                .getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(response.followings()).isNull();
    }

    @Test
    void 공개_프로필의_팔로잉_목록은_조회자의_관계를_반영한다() {
        Member viewer = activeMember(1L);
        Member target = activeMember(2L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(target));
        when(memberPreferenceRepository.findByMemberId(2L))
                .thenReturn(Optional.empty());

        PublicProfileFollowingRow row = mock(
                PublicProfileFollowingRow.class
        );
        when(row.getMemberId()).thenReturn(3L);
        when(row.getNickname()).thenReturn("성수탐방러");
        when(row.getSelectedCharacterId()).thenReturn("PALBANG_RABBIT");
        when(row.getAgeGroup()).thenReturn("TWENTIES");
        when(row.getAgeGroupPublicAgreed()).thenReturn(true);
        when(row.getInterestRegionPublicAgreed()).thenReturn(false);
        when(row.getParticipatingStudyCount()).thenReturn(2L);
        when(row.getIsFollowing()).thenReturn(true);
        when(row.getFollowedAt()).thenReturn(
                Instant.parse("2026-07-20T05:00:00Z")
        );
        when(memberRepository.findPublicProfileFollowings(
                1L,
                2L,
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(
                List.of(row),
                PageRequest.of(0, 20),
                1
        ));

        MemberPublicProfileResponse response = memberService
                .getPublicProfile(
                        1L,
                        2L,
                        MemberPublicProfileSection.FOLLOWINGS,
                        MemberPublicStudyStatus.ACTIVE,
                        0,
                        20
                );

        assertThat(response.section())
                .isEqualTo(MemberPublicProfileSection.FOLLOWINGS);
        assertThat(response.studies()).isNull();
        assertThat(response.reports()).isNull();
        assertThat(response.followings().content()).hasSize(1);
        assertThat(response.followings().content().get(0).ageGroup())
                .isEqualTo("TWENTIES");
        assertThat(response.followings().content().get(0).interestRegion())
                .isNull();
        assertThat(response.followings().content().get(0).isFollowing())
                .isTrue();
        assertThat(response.followings().content().get(0).canFollow())
                .isFalse();
        assertThat(response.followings().content().get(0).canSendMessage())
                .isTrue();
    }

    @Test
    void 공개_동의가_없으면_연령대와_관심지역을_숨긴다() {
        Member viewer = activeMember(1L);
        Member target = activeMember(2L);
        target.updateOnboardingProfile(
                "TWENTIES",
                false,
                "PALBANG_RABBIT"
        );
        MemberPreference preference = preference(2L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(target));
        when(memberPreferenceRepository.findByMemberId(2L))
                .thenReturn(Optional.of(preference));
        when(studyRepository.findPublicProfileStudies(
                2L,
                "ACTIVE",
                PageRequest.of(0, 20)
        )).thenReturn(Page.empty(PageRequest.of(0, 20)));

        MemberPublicProfileResponse response = memberService
                .getPublicProfile(
                        1L,
                        2L,
                        MemberPublicProfileSection.STUDIES,
                        MemberPublicStudyStatus.ACTIVE,
                        0,
                        20
                );

        assertThat(response.ageGroup()).isNull();
        assertThat(response.interestRegion()).isNull();
        assertThat(response.isFollowing()).isFalse();
        assertThat(response.canFollow()).isTrue();
        assertThat(response.canSendMessage()).isFalse();
    }

    @Test
    void 자기_프로필이면_팔로우와_쪽지를_모두_비활성화한다() {
        Member viewer = activeMember(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(memberPreferenceRepository.findByMemberId(1L))
                .thenReturn(Optional.empty());
        when(studyRepository.findPublicProfileStudies(
                1L,
                "ACTIVE",
                PageRequest.of(0, 20)
        )).thenReturn(Page.empty(PageRequest.of(0, 20)));

        MemberPublicProfileResponse response = memberService
                .getPublicProfile(
                        1L,
                        1L,
                        MemberPublicProfileSection.STUDIES,
                        MemberPublicStudyStatus.ACTIVE,
                        0,
                        20
                );

        assertThat(response.isMe()).isTrue();
        assertThat(response.isFollowing()).isFalse();
        assertThat(response.canFollow()).isFalse();
        assertThat(response.canSendMessage()).isFalse();
        verify(memberRepository, never()).existsFollow(any(), any());
    }

    @Test
    void 탈퇴한_조회_대상은_존재하지_않는_회원처럼_처리한다() {
        Member viewer = activeMember(1L);
        Member target = activeMember(2L);
        ReflectionTestUtils.setField(target, "status", MemberStatus.WITHDRAWN);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(target));

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.getPublicProfile(
                        1L,
                        2L,
                        MemberPublicProfileSection.STUDIES,
                        MemberPublicStudyStatus.ACTIVE,
                        0,
                        20
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.MEMBER_NOT_FOUND
        );
        verifyNoInteractions(memberPreferenceRepository);
    }

    @Test
    void 요청에_포함된_프로필과_생활조건만_수정한다() {
        Member member = activeMember(1L);
        member.updateOnboardingProfile(
                "TWENTIES",
                false,
                "PALBANG_RABBIT"
        );
        MemberPreference preference = preference(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberPreferenceRepository.findByMemberId(1L))
                .thenReturn(Optional.of(preference));
        when(memberRepository.existsByNicknameAndIdNot(
                "성동구탐방러",
                1L
        )).thenReturn(false);
        MemberProfileUpdateRequest request = fullProfileUpdateRequest();

        MemberProfileUpdateResponse response = memberService.updateMyProfile(
                1L,
                request
        );

        assertThat(member.getNickname()).isEqualTo("성동구탐방러");
        assertThat(member.getAgeGroup()).isEqualTo("FIFTIES");
        assertThat(member.isAgeGroupPublicAgreed()).isTrue();
        assertThat(member.getSelectedCharacterId()).isEqualTo("PALBANG_DOG");
        assertThat(preference.getPurpose()).isEqualTo("INVESTMENT");
        assertThat(preference.getHouseholdType()).isNull();
        assertThat(preference.getBudget()).isNull();
        assertThat(preference.getInterestRegion()).isNull();
        assertThat(preference.isInterestRegionPublicAgreed()).isFalse();
        assertThat(preference.getPriorities()).containsExactly(
                "PARKING",
                "TRANSPORT",
                "SAFETY"
        );
        assertThat(preference.getMaritalStatus()).isEqualTo("MARRIED");
        assertThat(preference.getHasVehicle()).isTrue();
        assertThat(preference.getHasChildren()).isFalse();
        assertThat(response.nickname()).isEqualTo("성동구탐방러");
        assertThat(response.purpose()).isEqualTo("INVESTMENT");
        assertThat(response.priorities()).containsExactly(
                "PARKING",
                "TRANSPORT",
                "SAFETY"
        );
        assertThat(response.hasChildren()).isFalse();
        assertThat(response.updatedAt().getOffset())
                .isEqualTo(ZoneOffset.ofHours(9));
        verify(memberPreferenceRepository).save(preference);
        verify(memberRepository).flush();
    }

    @Test
    void false는_수정값으로_처리하고_선호정보가_없으면_생성한다() {
        Member member = activeMember(1L);
        member.updateOnboardingProfile(
                "TWENTIES",
                false,
                "PALBANG_RABBIT"
        );
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberPreferenceRepository.findByMemberId(1L))
                .thenReturn(Optional.empty());
        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest();
        request.setHasVehicle(false);

        MemberProfileUpdateResponse response = memberService.updateMyProfile(
                1L,
                request
        );

        ArgumentCaptor<MemberPreference> captor = ArgumentCaptor.forClass(
                MemberPreference.class
        );
        verify(memberPreferenceRepository).save(captor.capture());
        assertThat(captor.getValue().getHasVehicle()).isFalse();
        assertThat(captor.getValue().getHasChildren()).isNull();
        assertThat(member.getAgeGroup()).isEqualTo("TWENTIES");
        assertThat(member.getSelectedCharacterId())
                .isEqualTo("PALBANG_RABBIT");
        assertThat(response.hasVehicle()).isFalse();
        assertThat(response.hasChildren()).isNull();
    }

    @Test
    void 현재_닉네임만_다시_보내면_중복검사없이_유지한다() {
        Member member = activeMember(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberPreferenceRepository.findByMemberId(1L))
                .thenReturn(Optional.empty());
        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest();
        request.setNickname("  루돌푸  ");

        MemberProfileUpdateResponse response = memberService.updateMyProfile(
                1L,
                request
        );

        assertThat(response.nickname()).isEqualTo("루돌푸");
        verify(memberRepository, never())
                .existsByNicknameAndIdNot(any(), any());
        verify(memberPreferenceRepository, never()).save(any());
    }

    @Test
    void 수정_필드가_없으면_거절한다() {
        Member member = activeMember(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.updateMyProfile(
                        1L,
                        new MemberProfileUpdateRequest()
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.MEMBER_PROFILE_UPDATE_EMPTY
        );
        verifyNoInteractions(memberPreferenceRepository);
        verify(memberRepository, never()).flush();
    }

    @Test
    void 명시적으로_null을_보낸_필드는_거절한다() {
        Member member = activeMember(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberPreferenceRepository.findByMemberId(1L))
                .thenReturn(Optional.empty());
        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest();
        request.setHasChildren(null);

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.updateMyProfile(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
        );
        assertThat(exception.getData())
                .containsEntry("field", "hasChildren")
                .containsEntry("reason", "null로 수정할 수 없습니다.");
        verify(memberRepository, never()).flush();
    }

    @Test
    void 공백_닉네임을_거절한다() {
        mockActiveMember(1L);
        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest();
        request.setNickname("   ");

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.updateMyProfile(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
        );
        assertThat(exception.getData()).containsEntry("field", "nickname");
    }

    @Test
    void 이미_사용중인_닉네임으로_변경할_수_없다() {
        mockActiveMember(1L);
        when(memberRepository.existsByNicknameAndIdNot("중복닉네임", 1L))
                .thenReturn(true);
        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest();
        request.setNickname("중복닉네임");

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.updateMyProfile(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.AUTH_NICKNAME_DUPLICATED
        );
        verify(memberRepository, never()).flush();
    }

    @Test
    void 닉네임_중복_경합도_명세의_409_오류로_변환한다() {
        mockActiveMember(1L);
        when(memberRepository.existsByNicknameAndIdNot("경합닉네임", 1L))
                .thenReturn(false);
        doThrow(
                new DataIntegrityViolationException(
                        "duplicate nickname",
                        new ConstraintViolationException(
                                "duplicate nickname",
                                new SQLException("duplicate nickname"),
                                "member_nickname_key"
                        )
                )
        ).when(memberRepository).flush();
        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest();
        request.setNickname("경합닉네임");

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.updateMyProfile(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.AUTH_NICKNAME_DUPLICATED
        );
    }

    @Test
    void 허용되지_않은_프로필_enum을_거절한다() {
        mockActiveMember(1L);
        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest();
        request.setAgeGroup("FIFTIES_PLUS");

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.updateMyProfile(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.MEMBER_AGE_GROUP_INVALID
        );
        assertThat(exception.getData())
                .containsEntry("field", "ageGroup")
                .containsKey("allowedValues");
    }

    @Test
    void 프로필_수정에서도_우선순위_중복을_거절한다() {
        mockActiveMember(1L);
        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest();
        request.setPriorities(List.of(
                "TRANSPORT",
                "SAFETY",
                "TRANSPORT"
        ));

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.updateMyProfile(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.MEMBER_PRIORITY_DUPLICATED
        );
        verify(memberPreferenceRepository, never()).save(any());
    }

    @Test
    void 프로필_수정에서_빈_우선순위는_거절한다() {
        mockActiveMember(1L);
        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest();
        request.setPriorities(List.of());

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.updateMyProfile(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
        );
        assertThat(exception.getData())
                .containsEntry("field", "priorities")
                .containsEntry(
                        "reason",
                        "우선순위는 1개 이상 4개 이하로 선택해 주세요."
                );
    }

    @Test
    void 탈퇴_회원의_프로필_수정을_거절한다() {
        Member member = activeMember(1L);
        ReflectionTestUtils.setField(
                member,
                "status",
                MemberStatus.WITHDRAWN
        );
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest();
        request.setNickname("새닉네임");

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.updateMyProfile(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.AUTH_MEMBER_WITHDRAWN
        );
        verifyNoInteractions(memberPreferenceRepository);
    }

    @Test
    void 온보딩_정보와_우선순위_순서를_저장한다() {
        Member member = activeMember(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberPreferenceRepository.findByMemberId(1L))
                .thenReturn(Optional.empty());
        when(memberPreferenceRepository.save(any(MemberPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingResponse response = memberService.saveOnboarding(
                1L,
                completedRequest("PALBANG_RABBIT")
        );

        ArgumentCaptor<MemberPreference> captor = ArgumentCaptor.forClass(
                MemberPreference.class
        );
        verify(memberPreferenceRepository).save(captor.capture());
        MemberPreference savedPreference = captor.getValue();

        assertThat(savedPreference.getMemberId()).isEqualTo(1L);
        assertThat(savedPreference.getPurpose()).isEqualTo("RESIDENCE");
        assertThat(savedPreference.getHouseholdType()).isNull();
        assertThat(savedPreference.getMaritalStatus()).isEqualTo("MARRIED");
        assertThat(savedPreference.getHasVehicle()).isTrue();
        assertThat(savedPreference.getHasChildren()).isTrue();
        assertThat(savedPreference.getBudget()).isNull();
        assertThat(savedPreference.getInterestRegion()).isNull();
        assertThat(savedPreference.isInterestRegionPublicAgreed()).isFalse();
        assertThat(savedPreference.getPriorities()).containsExactly(
                "TRANSPORT",
                "WALKABILITY",
                "PARKING",
                "GREEN_SPACE"
        );
        assertThat(member.getAgeGroup()).isEqualTo("FIFTIES");
        assertThat(member.isAgeGroupPublicAgreed()).isTrue();
        assertThat(member.getSelectedCharacterId())
                .isEqualTo("PALBANG_RABBIT");
        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.onboardingCompleted()).isTrue();
    }

    @Test
    void 캐릭터가_누락되면_필수값_오류를_반환한다() {
        mockActiveMember(1L);

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.saveOnboarding(1L, completedRequest(null))
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.MEMBER_ONBOARDING_REQUIRED_FIELD_MISSING
        );
        assertThat(exception.getData())
                .containsEntry("field", "selectedCharacterId");
    }

    @Test
    void 육십대_이상과_개_버전_캐릭터를_저장한다() {
        Member member = activeMember(1L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(memberPreferenceRepository.findByMemberId(1L))
                .thenReturn(Optional.empty());
        when(memberPreferenceRepository.save(any(MemberPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingResponse response = memberService.saveOnboarding(
                1L,
                new OnboardingRequest(
                        "RESIDENCE",
                        "SINGLE",
                        false,
                        false,
                        List.of("NOISE", "GREEN_SPACE"),
                        "SIXTIES_PLUS",
                        true,
                        "PALBANG_DOG"
                )
        );

        ArgumentCaptor<MemberPreference> captor = ArgumentCaptor.forClass(
                MemberPreference.class
        );
        verify(memberPreferenceRepository).save(captor.capture());
        assertThat(captor.getValue().getHouseholdType())
                .isEqualTo("SINGLE_PERSON");
        assertThat(response.ageGroup()).isEqualTo("SIXTIES_PLUS");
        assertThat(response.selectedCharacterId())
                .isEqualTo("PALBANG_DOG");
        assertThat(response.maritalStatus()).isEqualTo("SINGLE");
        assertThat(response.hasVehicle()).isFalse();
        assertThat(response.hasChildren()).isFalse();
        assertThat(response.ageGroupPublicAgreed()).isTrue();
    }

    @Test
    void 연령대_공개동의가_누락되면_필수값_오류를_반환한다() {
        mockActiveMember(1L);

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.saveOnboarding(
                        1L,
                        new OnboardingRequest(
                                "RESIDENCE",
                                "MARRIED",
                                true,
                                false,
                                List.of("TRANSPORT"),
                                "TWENTIES",
                                null,
                                "PALBANG"
                        )
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.MEMBER_ONBOARDING_REQUIRED_FIELD_MISSING
        );
        assertThat(exception.getData())
                .containsEntry("field", "ageGroupPublicAgreed");
        verify(memberPreferenceRepository, never()).save(any());
    }

    @Test
    void 완료_요청의_첫_필수항목이_누락되면_필드정보를_반환한다() {
        mockActiveMember(1L);

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.saveOnboarding(
                        1L,
                        new OnboardingRequest(
                                null,
                                "MARRIED",
                                true,
                                true,
                                List.of("TRANSPORT"),
                                "TWENTIES",
                                false,
                                "PALBANG_RABBIT"
                        )
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.MEMBER_ONBOARDING_REQUIRED_FIELD_MISSING
        );
        assertThat(exception.getData())
                .containsEntry("field", "purpose")
                .containsEntry("reason", "필수 값입니다.");
    }

    @Test
    void 차량_소유_여부가_누락되면_필수값_오류를_반환한다() {
        mockActiveMember(1L);
        OnboardingRequest request = new OnboardingRequest(
                "RESIDENCE",
                "MARRIED",
                null,
                true,
                List.of("TRANSPORT"),
                "THIRTIES",
                false,
                "PALBANG"
        );

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.saveOnboarding(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.MEMBER_ONBOARDING_REQUIRED_FIELD_MISSING
        );
        assertThat(exception.getData()).containsEntry("field", "hasVehicle");
    }

    @Test
    void 허용되지_않은_혼인상태를_거절한다() {
        mockActiveMember(1L);
        OnboardingRequest request = new OnboardingRequest(
                "RESIDENCE",
                "INVALID",
                true,
                true,
                List.of("TRANSPORT"),
                "THIRTIES",
                false,
                "PALBANG"
        );

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.saveOnboarding(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
        );
        assertThat(exception.getData()).containsEntry(
                "field",
                "maritalStatus"
        );
    }

    @Test
    void 허용되지_않은_캐릭터를_거절한다() {
        mockActiveMember(1L);

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.saveOnboarding(
                        1L,
                        completedRequest("INVALID")
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.MEMBER_CHARACTER_INVALID
        );
        assertThat(exception.getData().get("allowedValues"))
                .isEqualTo(List.of(
                        "PALBANG",
                        "PALBANG_RABBIT",
                        "PALBANG_DOG"
                ));
    }

    @Test
    void 허용되지_않은_연령대를_거절한다() {
        mockActiveMember(1L);
        OnboardingRequest request = new OnboardingRequest(
                "RESIDENCE",
                "MARRIED",
                true,
                false,
                List.of("TRANSPORT"),
                "FIFTIES_PLUS",
                false,
                "PALBANG"
        );

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.saveOnboarding(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.MEMBER_AGE_GROUP_INVALID
        );
    }

    @Test
    void 같은_우선순위를_중복해서_선택할_수_없다() {
        mockActiveMember(1L);
        OnboardingRequest request = new OnboardingRequest(
                "RESIDENCE",
                "SINGLE",
                false,
                false,
                List.of("TRANSPORT", "SAFETY", "TRANSPORT"),
                "TWENTIES",
                false,
                "PALBANG"
        );

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.saveOnboarding(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.MEMBER_PRIORITY_DUPLICATED
        );
    }

    @Test
    void 허용되지_않은_목적을_공통_입력값_오류로_거절한다() {
        mockActiveMember(1L);
        OnboardingRequest request = new OnboardingRequest(
                "INVALID",
                "MARRIED",
                true,
                true,
                List.of("TRANSPORT"),
                "TWENTIES",
                false,
                "PALBANG"
        );

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.saveOnboarding(1L, request)
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
        );
        assertThat(exception.getData()).containsEntry("field", "purpose");
    }

    @Test
    void 탈퇴_회원의_온보딩_요청을_거절한다() {
        Member member = activeMember(1L);
        ReflectionTestUtils.setField(
                member,
                "status",
                MemberStatus.WITHDRAWN
        );
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> memberService.saveOnboarding(
                        1L,
                        new OnboardingRequest(
                                "RESIDENCE",
                                "MARRIED",
                                true,
                                false,
                                List.of("TRANSPORT"),
                                "TWENTIES",
                                false,
                                "PALBANG"
                        )
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(
                ErrorCode.AUTH_MEMBER_WITHDRAWN
        );
        verifyNoInteractions(memberPreferenceRepository);
    }

    private OnboardingRequest completedRequest(String selectedCharacterId) {
        return new OnboardingRequest(
                "RESIDENCE",
                "MARRIED",
                true,
                true,
                List.of(
                        "TRANSPORT",
                        "WALKABILITY",
                        "PARKING",
                        "GREEN_SPACE"
                ),
                "FIFTIES",
                true,
                selectedCharacterId
        );
    }

    private MemberProfileUpdateRequest fullProfileUpdateRequest() {
        MemberProfileUpdateRequest request = new MemberProfileUpdateRequest();
        request.setNickname("  성동구탐방러  ");
        request.setAgeGroup("FIFTIES");
        request.setAgeGroupPublicAgreed(true);
        request.setPurpose("  INVESTMENT  ");
        request.setPriorities(List.of(
                "PARKING",
                "TRANSPORT",
                "SAFETY"
        ));
        request.setSelectedCharacterId("PALBANG_DOG");
        request.setMaritalStatus("MARRIED");
        request.setHasVehicle(true);
        request.setHasChildren(false);
        return request;
    }

    private MemberPreference preference(Long memberId) {
        MemberPreference preference = new MemberPreference(memberId);
        preference.update(
                "RESIDENCE",
                "NEWLYWED",
                "SINGLE",
                false,
                true,
                "500M_TO_700M",
                "서울특별시 송파구",
                List.of("TRANSPORT", "SAFETY")
        );
        return preference;
    }

    private Member activeMember(Long id) {
        Member member = new Member(
                "dain@example.com",
                "encoded-password",
                "루돌푸"
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private void mockActiveMember(Long id) {
        Member member = activeMember(id);
        when(memberRepository.findById(id)).thenReturn(Optional.of(member));
        when(memberPreferenceRepository.findByMemberId(id))
                .thenReturn(Optional.empty());
    }
}

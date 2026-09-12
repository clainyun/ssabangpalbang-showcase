package com.ssafy.ssabangpalbang.review.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.review.domain.MemberReview;
import com.ssafy.ssabangpalbang.review.domain.MemberReviewTag;
import com.ssafy.ssabangpalbang.review.dto.request.MemberReviewCreateRequest;
import com.ssafy.ssabangpalbang.review.dto.response.MemberReviewCreateResponse;
import com.ssafy.ssabangpalbang.review.repository.MemberReviewRepository;
import com.ssafy.ssabangpalbang.review.repository.MemberReviewTagRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MemberReviewServiceTest {

    @Mock MemberRepository memberRepository;
    @Mock StudyRepository studyRepository;
    @Mock StudyMemberRepository studyMemberRepository;
    @Mock MemberReviewRepository memberReviewRepository;
    @Mock MemberReviewTagRepository memberReviewTagRepository;

    private MemberReviewService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new MemberReviewService(
                memberRepository,
                studyRepository,
                studyMemberRepository,
                memberReviewRepository,
                memberReviewTagRepository
        );
        when(memberRepository.findById(7L))
                .thenReturn(Optional.of(member(7L, MemberStatus.ACTIVE, null)));
        when(memberRepository.findById(9L))
                .thenReturn(Optional.of(member(9L, MemberStatus.ACTIVE, null)));
        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.IN_PROGRESS)));
        when(studyMemberRepository.findByStudyIdAndMemberId(10L, 7L))
                .thenReturn(Optional.of(StudyMember.createLeader(10L, 7L)));
        when(studyMemberRepository.findByStudyIdAndMemberId(10L, 9L))
                .thenReturn(Optional.of(StudyMember.createMember(10L, 9L)));
        when(memberReviewRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));
        when(memberReviewTagRepository.saveAll(any()))
                .thenAnswer(invocation -> {
                    List<MemberReviewTag> tags = invocation.getArgument(0);
                    return List.copyOf(tags);
                });
    }

    @Test
    void 태그와_좋아요로_같은_스터디_멤버를_익명_평가한다() {
        MemberReviewCreateResponse response = service.createReview(
                7L,
                10L,
                9L,
                new MemberReviewCreateRequest(
                        List.of("PUNCTUAL", "GOOD_RECORDS"),
                        true,
                        "  현장 기록을 꼼꼼히 공유했어요.  "
                )
        );

        assertThat(response.reviewId()).isEqualTo(21L);
        assertThat(response.studyId()).isEqualTo(10L);
        assertThat(response.reviewedMemberId()).isEqualTo(9L);
        assertThat(response.liked()).isTrue();
        assertThat(response.tags()).extracting("code")
                .containsExactly("PUNCTUAL", "GOOD_RECORDS");
        assertThat(response.tags().get(0).label())
                .isEqualTo("시간 약속을 잘 지켜요");
        assertThat(response.tags().get(0).emoji()).isEqualTo("⏰");
        assertThat(response.content()).isEqualTo("현장 기록을 꼼꼼히 공유했어요.");
        assertThat(response.createdAt().toString())
                .isEqualTo("2026-08-03T15:00+09:00");
    }

    @Test
    void 태그만_남긴_평가도_등록한다() {
        MemberReviewCreateResponse response = service.createReview(
                7L,
                10L,
                9L,
                new MemberReviewCreateRequest(
                        List.of("THOROUGH"),
                        false,
                        null
                )
        );

        assertThat(response.liked()).isFalse();
        assertThat(response.tags()).extracting("code")
                .containsExactly("THOROUGH");
    }

    @Test
    void 좋아요만_남긴_평가도_등록한다() {
        MemberReviewCreateResponse response = service.createReview(
                7L,
                10L,
                9L,
                new MemberReviewCreateRequest(List.of(), true, null)
        );

        assertThat(response.liked()).isTrue();
        assertThat(response.tags()).isEmpty();
    }

    @Test
    void 태그도_좋아요도_없으면_거절한다() {
        assertCode(
                () -> service.createReview(
                        7L,
                        10L,
                        9L,
                        new MemberReviewCreateRequest(List.of(), false, "메모만")
                ),
                ErrorCode.MEMBER_REVIEW_SIGNAL_REQUIRED
        );
        verifyNoInteractions(
                memberReviewRepository,
                memberReviewTagRepository
        );
    }

    @Test
    void 유효하지_않은_태그는_거절한다() {
        assertCode(
                () -> service.createReview(
                        7L,
                        10L,
                        9L,
                        new MemberReviewCreateRequest(
                                List.of("UNKNOWN_TAG"),
                                false,
                                null
                        )
                ),
                ErrorCode.MEMBER_REVIEW_TAG_INVALID
        );
        verifyNoInteractions(
                memberReviewRepository,
                memberReviewTagRepository
        );
    }

    @Test
    void 중복_태그는_거절한다() {
        assertCode(
                () -> service.createReview(
                        7L,
                        10L,
                        9L,
                        new MemberReviewCreateRequest(
                                List.of("PUNCTUAL", "PUNCTUAL"),
                                false,
                                null
                        )
                ),
                ErrorCode.MEMBER_REVIEW_TAG_INVALID
        );
        verifyNoInteractions(
                memberReviewRepository,
                memberReviewTagRepository
        );
    }

    @Test
    void 공백_리뷰는_null로_저장한다() {
        MemberReviewCreateResponse response = service.createReview(
                7L,
                10L,
                9L,
                new MemberReviewCreateRequest(List.of("WANT_AGAIN"), false, "   ")
        );

        assertThat(response.content()).isNull();
    }

    @Test
    void 본인은_평가할_수_없다() {
        assertCode(
                () -> service.createReview(
                        7L,
                        10L,
                        7L,
                        new MemberReviewCreateRequest(List.of(), true, null)
                ),
                ErrorCode.MEMBER_REVIEW_SELF_NOT_ALLOWED
        );
    }

    @Test
    void 스터디_외부_회원은_평가할_수_없다() {
        when(studyMemberRepository.findByStudyIdAndMemberId(10L, 7L))
                .thenReturn(Optional.empty());

        assertCode(
                () -> service.createReview(
                        7L,
                        10L,
                        9L,
                        new MemberReviewCreateRequest(List.of(), true, null)
                ),
                ErrorCode.MEMBER_REVIEW_CREATE_FORBIDDEN
        );
        verifyNoInteractions(memberReviewRepository, memberReviewTagRepository);
    }

    @Test
    void 탈퇴한_로그인_회원은_평가할_수_없다() {
        when(memberRepository.findById(7L)).thenReturn(Optional.of(
                member(7L, MemberStatus.WITHDRAWN, Instant.parse("2026-08-01T00:00:00Z"))
        ));

        assertCode(
                () -> service.createReview(
                        7L,
                        10L,
                        9L,
                        new MemberReviewCreateRequest(List.of(), true, null)
                ),
                ErrorCode.MEMBER_NOT_FOUND
        );
        verifyNoInteractions(memberReviewRepository, memberReviewTagRepository);
    }

    @Test
    void 활성_스터디원이_아닌_대상은_찾을_수_없는_것처럼_처리한다() {
        when(studyMemberRepository.findByStudyIdAndMemberId(10L, 9L))
                .thenReturn(Optional.empty());

        assertCode(
                () -> service.createReview(
                        7L,
                        10L,
                        9L,
                        new MemberReviewCreateRequest(List.of(), true, null)
                ),
                ErrorCode.MEMBER_REVIEW_TARGET_NOT_FOUND
        );
    }

    @Test
    void 탈퇴한_평가_대상은_찾을_수_없는_것처럼_처리한다() {
        when(memberRepository.findById(9L)).thenReturn(Optional.of(
                member(9L, MemberStatus.WITHDRAWN, Instant.parse("2026-08-01T00:00:00Z"))
        ));

        assertCode(
                () -> service.createReview(
                        7L,
                        10L,
                        9L,
                        new MemberReviewCreateRequest(List.of(), true, null)
                ),
                ErrorCode.MEMBER_REVIEW_TARGET_NOT_FOUND
        );
    }

    @Test
    void 취소된_스터디에는_평가를_등록할_수_없다() {
        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.CANCELED)));

        assertCode(
                () -> service.createReview(
                        7L,
                        10L,
                        9L,
                        new MemberReviewCreateRequest(List.of(), true, null)
                ),
                ErrorCode.MEMBER_REVIEW_STUDY_CANCELED
        );
    }

    @Test
    void 이미_등록한_대상은_중복_평가할_수_없다() {
        when(memberReviewRepository
                .existsByStudyIdAndReviewerIdAndRevieweeId(10L, 7L, 9L))
                .thenReturn(true);

        assertCode(
                () -> service.createReview(
                        7L,
                        10L,
                        9L,
                        new MemberReviewCreateRequest(List.of(), true, null)
                ),
                ErrorCode.MEMBER_REVIEW_ALREADY_EXISTS
        );
    }

    @Test
    void 동시_등록의_DB_유니크_충돌도_도메인_중복으로_변환한다() {
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(memberReviewRepository).saveAndFlush(any());

        assertCode(
                () -> service.createReview(
                        7L,
                        10L,
                        9L,
                        new MemberReviewCreateRequest(List.of(), true, null)
                ),
                ErrorCode.MEMBER_REVIEW_ALREADY_EXISTS
        );
    }

    @Test
    void 태그_저장의_무결성_오류는_중복으로_변환하지_않고_그대로_전파한다() {
        DataIntegrityViolationException violation =
                new DataIntegrityViolationException("tag constraint");
        doThrow(violation).when(memberReviewTagRepository).saveAll(any());

        assertThatThrownBy(() -> service.createReview(
                7L,
                10L,
                9L,
                new MemberReviewCreateRequest(List.of("PUNCTUAL"), false, null)
        )).isSameAs(violation);
    }

    private MemberReview persisted(MemberReview review) {
        ReflectionTestUtils.setField(review, "id", 21L);
        ReflectionTestUtils.setField(
                review,
                "createdAt",
                Instant.parse("2026-08-03T06:00:00Z")
        );
        return review;
    }

    private Study study(StudyStatus status) {
        Study study = Study.create(1L, 7L, "스터디", null, "목표", 6, null);
        ReflectionTestUtils.setField(study, "id", 10L);
        ReflectionTestUtils.setField(study, "status", status);
        return study;
    }

    private Member member(Long id, MemberStatus status, Instant deletedAt) {
        Member member = new Member(id + "@test.com", "hash", "회원" + id);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "status", status);
        ReflectionTestUtils.setField(member, "deletedAt", deletedAt);
        return member;
    }

    private void assertCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(errorCode)
                );
    }
}

package com.ssafy.ssabangpalbang.study.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyNotice;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.dto.request.StudyNoticeCreateRequest;
import com.ssafy.ssabangpalbang.study.dto.request.StudyNoticeUpdateRequest;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyNoticeRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudyNoticeServiceTest {
    @Mock StudyRepository studyRepository;
    @Mock MemberRepository memberRepository;
    @Mock StudyMemberRepository studyMemberRepository;
    @Mock StudyNoticeRepository noticeRepository;
    @Mock StudyNoticeNotificationWriter notificationWriter;
    StudyNoticeService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new StudyNoticeService(
                studyRepository, memberRepository, studyMemberRepository, noticeRepository,
                notificationWriter);
        when(memberRepository.findById(any())).thenAnswer(invocation -> {
            Long memberId = invocation.getArgument(0);
            return Optional.of(member(memberId, MemberStatus.ACTIVE, null));
        });
    }

    @Test
    void leaderCreatesUpdatesAndDeletesNotice() {
        Study study = study(StudyStatus.RECRUITING);
        StudyNotice notice = notice(18L, 10L, "기존");
        when(studyRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(noticeRepository.save(any())).thenReturn(notice);
        when(noticeRepository.findByIdAndDeletedAtIsNull(18L)).thenReturn(Optional.of(notice));
        when(noticeRepository.findById(18L)).thenReturn(Optional.of(notice));

        assertThat(service.createNotice(7L, 10L, new StudyNoticeCreateRequest("공지")).noticeId())
                .isEqualTo(18L);
        verify(notificationWriter).notifyCreated(study, notice, 7L);
        assertThat(service.updateNotice(
                7L, 10L, 18L, new StudyNoticeUpdateRequest("수정")).content()).isEqualTo("수정");
        assertThat(service.deleteNotice(7L, 10L, 18L).deletedAt()).isNotBlank();
        assertThat(notice.getDeletedAt()).isNotNull();
        verify(noticeRepository, org.mockito.Mockito.times(2)).flush();
    }

    @Test
    void permissionRunsBeforeReadOnlyState() {
        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.COMPLETED)));

        assertCode(
                () -> service.createNotice(9L, 10L, new StudyNoticeCreateRequest("공지")),
                ErrorCode.STUDY_NOTICE_CREATE_FORBIDDEN);
        assertCode(
                () -> service.createNotice(7L, 10L, new StudyNoticeCreateRequest("공지")),
                ErrorCode.STUDY_NOTICE_CREATE_NOT_ALLOWED);
    }

    @Test
    void listUsesSizePlusOneCursorAndTruncatesPreview() {
        Study study = study(StudyStatus.RECRUITING);
        StudyNotice n3 = notice(3L, 10L, "가".repeat(151));
        StudyNotice n2 = notice(2L, 10L, "둘");
        StudyNotice n1 = notice(1L, 10L, "하나");
        when(studyRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(studyMemberRepository.findByStudyIdAndStatus(any(), any())).thenReturn(List.of());
        when(noticeRepository.findByStudyIdAndDeletedAtIsNullOrderByIdDesc(any(), any()))
                .thenReturn(List.of(n3, n2, n1));

        var result = service.getNotices(7L, 10L, null, 2);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).content()).hasSize(150);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(2L);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(noticeRepository).findByStudyIdAndDeletedAtIsNullOrderByIdDesc(
                org.mockito.ArgumentMatchers.eq(10L), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void activeMemberCanListCompletedStudyReadOnly() {
        Study study = study(StudyStatus.COMPLETED);
        StudyMember member = StudyMember.createLeader(10L, 9L);
        when(studyRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(studyMemberRepository.findByStudyIdAndStatus(any(), any()))
                .thenReturn(List.of(member));
        when(noticeRepository.findByStudyIdAndDeletedAtIsNullOrderByIdDesc(any(), any()))
                .thenReturn(List.of());

        var result = service.getNotices(9L, 10L, null, 20);

        assertThat(result.readOnly()).isTrue();
        assertThat(result.content()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void updateDistinguishesMissingMismatchAndEmpty() {
        Study study = study(StudyStatus.RECRUITING);
        when(studyRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(noticeRepository.findByIdAndDeletedAtIsNull(18L)).thenReturn(Optional.empty());
        assertCode(() -> service.updateNotice(
                7L, 10L, 18L, new StudyNoticeUpdateRequest("수정")),
                ErrorCode.STUDY_NOTICE_NOT_FOUND);

        StudyNotice other = notice(18L, 11L, "공지");
        when(noticeRepository.findByIdAndDeletedAtIsNull(18L)).thenReturn(Optional.of(other));
        assertCode(() -> service.updateNotice(
                7L, 10L, 18L, new StudyNoticeUpdateRequest("수정")),
                ErrorCode.STUDY_NOTICE_STUDY_MISMATCH);

        StudyNotice same = notice(18L, 10L, "공지");
        when(noticeRepository.findByIdAndDeletedAtIsNull(18L)).thenReturn(Optional.of(same));
        assertCode(() -> service.updateNotice(
                7L, 10L, 18L, new StudyNoticeUpdateRequest(null)),
                ErrorCode.STUDY_NOTICE_UPDATE_EMPTY);
    }

    @Test
    void createRejectsMissingOrSoftDeletedStudy() {
        when(studyRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());
        assertCode(() -> service.createNotice(
                7L, 10L, new StudyNoticeCreateRequest("공지")), ErrorCode.STUDY_NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(longs = {8L, 99L})
    void activeMemberOrOutsiderCannotCreate(Long requesterId) {
        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));
        assertCode(() -> service.createNotice(
                requesterId, 10L, new StudyNoticeCreateRequest("공지")),
                ErrorCode.STUDY_NOTICE_CREATE_FORBIDDEN);
    }

    @ParameterizedTest
    @EnumSource(value = StudyStatus.class, names = {"COMPLETED", "CANCELED"})
    void readOnlyStudyRejectsCreate(StudyStatus status) {
        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(status)));
        assertThatThrownBy(() -> service.createNotice(
                7L, 10L, new StudyNoticeCreateRequest("공지")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.STUDY_NOTICE_CREATE_NOT_ALLOWED);
                    assertThat(exception.getData()).containsEntry("studyStatus", status.name());
                });
    }

    @ParameterizedTest
    @EnumSource(value = StudyStatus.class, names = {"RECRUITING", "CLOSED", "IN_PROGRESS"})
    void writableStudyAllowsCreateWithoutTruncatingContent(StudyStatus status) {
        String content = "가".repeat(200);
        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(status)));
        when(noticeRepository.save(any())).thenReturn(notice(18L, 10L, content));
        assertThat(service.createNotice(
                7L, 10L, new StudyNoticeCreateRequest(content)).content()).hasSize(200);
    }

    @Test
    void createAndUpdateNormalizeMultilineContent() {
        Study study = study(StudyStatus.RECRUITING);
        StudyNotice existing = notice(18L, 10L, "기존");
        when(studyRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(noticeRepository.save(any())).thenAnswer(invocation -> {
            StudyNotice saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 19L);
            ReflectionTestUtils.setField(
                    saved, "createdAt", Instant.parse("2026-07-24T07:00:00Z"));
            ReflectionTestUtils.setField(
                    saved, "updatedAt", Instant.parse("2026-07-24T07:00:00Z"));
            return saved;
        });
        when(noticeRepository.findByIdAndDeletedAtIsNull(18L))
                .thenReturn(Optional.of(existing));

        assertThat(service.createNotice(
                7L, 10L, new StudyNoticeCreateRequest("  첫 줄\n둘째 줄  ")).content())
                .isEqualTo("첫 줄\n둘째 줄");
        assertThat(service.updateNotice(
                7L, 10L, 18L, new StudyNoticeUpdateRequest("  수정 첫 줄\n수정 둘째 줄  "))
                .content()).isEqualTo("수정 첫 줄\n수정 둘째 줄");
    }

    @Test
    void contentLongerThanTwoThousandCharactersIsRejected() {
        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));
        when(noticeRepository.findByIdAndDeletedAtIsNull(18L))
                .thenReturn(Optional.of(notice(18L, 10L, "기존")));
        String tooLong = "가".repeat(2001);

        assertCode(() -> service.createNotice(
                7L, 10L, new StudyNoticeCreateRequest(tooLong)),
                ErrorCode.INVALID_INPUT_VALUE);
        assertCode(() -> service.updateNotice(
                7L, 10L, 18L, new StudyNoticeUpdateRequest(tooLong)),
                ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void withdrawnMemberCannotAccessNotices() {
        Member withdrawn = member(7L, MemberStatus.WITHDRAWN, null);
        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));
        when(memberRepository.findById(7L)).thenReturn(Optional.of(withdrawn));

        assertCode(() -> service.getNotices(7L, 10L, null, 20),
                ErrorCode.MEMBER_NOT_FOUND);
        assertCode(() -> service.createNotice(
                7L, 10L, new StudyNoticeCreateRequest("공지")),
                ErrorCode.MEMBER_NOT_FOUND);
    }

    @ParameterizedTest(name = "{0} 요청자는 목록을 볼 수 없다")
    @ValueSource(strings = {"OUTSIDER", "PENDING", "LEFT"})
    void nonActiveRequesterCannotList(String requesterState) {
        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));
        when(studyMemberRepository.findByStudyIdAndStatus(any(), any())).thenReturn(List.of());
        assertThat(requesterState).isNotBlank();
        assertCode(() -> service.getNotices(99L, 10L, null, 20),
                ErrorCode.STUDY_NOTICE_LIST_FORBIDDEN);
    }

    @Test
    void cursorSecondPageAndExactSizeHaveNoNextCursor() {
        Study study = study(StudyStatus.RECRUITING);
        when(studyRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(study));
        when(studyMemberRepository.findByStudyIdAndStatus(any(), any())).thenReturn(List.of());
        when(noticeRepository.findByStudyIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(2L), any()))
                .thenReturn(List.of(notice(1L, 10L, "남은 공지")));

        var second = service.getNotices(7L, 10L, 2L, 2);

        assertThat(second.content()).extracting(
                com.ssafy.ssabangpalbang.study.dto.response.StudyNoticeListResponse.NoticeItem::noticeId)
                .containsExactly(1L);
        assertThat(second.hasNext()).isFalse();
        assertThat(second.nextCursor()).isNull();

        when(noticeRepository.findByStudyIdAndDeletedAtIsNullOrderByIdDesc(any(), any()))
                .thenReturn(List.of(notice(3L, 10L, "셋"), notice(2L, 10L, "둘")));
        var exact = service.getNotices(7L, 10L, null, 2);
        assertThat(exact.content()).extracting(
                com.ssafy.ssabangpalbang.study.dto.response.StudyNoticeListResponse.NoticeItem::noticeId)
                .containsExactly(3L, 2L);
        assertThat(exact.hasNext()).isFalse();
    }

    @Test
    void smallestCursorReturnsEmptyPage() {
        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));
        when(studyMemberRepository.findByStudyIdAndStatus(any(), any())).thenReturn(List.of());
        when(noticeRepository
                .findByStudyIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(
                        any(), any(), any()))
                .thenReturn(List.of());
        var result = service.getNotices(7L, 10L, 1L, 20);
        assertThat(result.content()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {149, 150, 151})
    void previewHonors149To151CharacterBoundary(int length) {
        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));
        when(studyMemberRepository.findByStudyIdAndStatus(any(), any())).thenReturn(List.of());
        when(noticeRepository.findByStudyIdAndDeletedAtIsNullOrderByIdDesc(any(), any()))
                .thenReturn(List.of(notice(18L, 10L, "가".repeat(length))));
        int expected = Math.min(length, 150);
        assertThat(service.getNotices(7L, 10L, null, 20).content().get(0).content())
                .hasSize(expected)
                .doesNotEndWith("...");
    }

    @Test
    void updateChecksPermissionStateBlankAndAllowsSameValue() {
        StudyNotice notice = notice(18L, 10L, "같은 내용");
        when(noticeRepository.findByIdAndDeletedAtIsNull(18L)).thenReturn(Optional.of(notice));

        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));
        assertCode(() -> service.updateNotice(
                9L, 10L, 18L, new StudyNoticeUpdateRequest("수정")),
                ErrorCode.STUDY_NOTICE_UPDATE_FORBIDDEN);

        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.COMPLETED)));
        assertCode(() -> service.updateNotice(
                7L, 10L, 18L, new StudyNoticeUpdateRequest("수정")),
                ErrorCode.STUDY_NOTICE_UPDATE_NOT_ALLOWED);

        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));
        assertCode(() -> service.updateNotice(
                7L, 10L, 18L, new StudyNoticeUpdateRequest("   ")),
                ErrorCode.INVALID_INPUT_VALUE);
        assertThat(service.updateNotice(
                7L, 10L, 18L, new StudyNoticeUpdateRequest("같은 내용")).content())
                .isEqualTo("같은 내용");
    }

    @Test
    void deleteDistinguishesPermissionStateMissingMismatchAndRedeletion() {
        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));
        assertCode(() -> service.deleteNotice(9L, 10L, 18L),
                ErrorCode.STUDY_NOTICE_DELETE_FORBIDDEN);

        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.CANCELED)));
        assertCode(() -> service.deleteNotice(7L, 10L, 18L),
                ErrorCode.STUDY_NOTICE_DELETE_NOT_ALLOWED);

        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));
        when(noticeRepository.findById(18L)).thenReturn(Optional.empty());
        assertCode(() -> service.deleteNotice(7L, 10L, 18L),
                ErrorCode.STUDY_NOTICE_NOT_FOUND);

        when(noticeRepository.findById(18L))
                .thenReturn(Optional.of(notice(18L, 11L, "다른 스터디")));
        assertCode(() -> service.deleteNotice(7L, 10L, 18L),
                ErrorCode.STUDY_NOTICE_STUDY_MISMATCH);

        StudyNotice deleted = notice(18L, 10L, "삭제");
        when(noticeRepository.findById(18L))
                .thenReturn(Optional.of(deleted));
        service.deleteNotice(7L, 10L, 18L);
        assertCode(() -> service.deleteNotice(7L, 10L, 18L),
                ErrorCode.STUDY_NOTICE_ALREADY_DELETED);
    }

    @Test
    void deletedNoticeIsAbsentFromSubsequentList() {
        StudyNotice notice = notice(18L, 10L, "삭제");
        when(studyRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(study(StudyStatus.RECRUITING)));
        when(noticeRepository.findById(18L)).thenReturn(Optional.of(notice));
        service.deleteNotice(7L, 10L, 18L);

        when(studyMemberRepository.findByStudyIdAndStatus(any(), any())).thenReturn(List.of());
        when(noticeRepository.findByStudyIdAndDeletedAtIsNullOrderByIdDesc(any(), any()))
                .thenReturn(List.of());
        assertThat(service.getNotices(7L, 10L, null, 20).content()).isEmpty();
        assertThat(notice.getDeletedAt()).isNotNull();
        verify(noticeRepository).flush();
    }

    private Study study(StudyStatus status) {
        Study study = Study.create(1L, 7L, "title", null, "goal", 6, null);
        ReflectionTestUtils.setField(study, "id", 10L);
        ReflectionTestUtils.setField(study, "status", status);
        return study;
    }

    private StudyNotice notice(Long id, Long studyId, String content) {
        StudyNotice notice = StudyNotice.create(studyId, content);
        ReflectionTestUtils.setField(notice, "id", id);
        ReflectionTestUtils.setField(notice, "createdAt", Instant.parse("2026-07-24T07:00:00Z"));
        ReflectionTestUtils.setField(notice, "updatedAt", Instant.parse("2026-07-24T07:00:00Z"));
        return notice;
    }

    private Member member(Long id, MemberStatus status, Instant deletedAt) {
        Member member = new Member(id + "@test.com", "hash", "회원" + id);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "status", status);
        ReflectionTestUtils.setField(member, "deletedAt", deletedAt);
        return member;
    }

    private void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}

package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.domain.ReportFavorite;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.response.ReportFavoriteResult;
import com.ssafy.ssabangpalbang.report.dto.response.ReportResponseCode;
import com.ssafy.ssabangpalbang.report.repository.ReportFavoriteRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportFavoriteServiceTest {

    private static final long MEMBER_ID = 7L;
    private static final long REPORT_ID = 48L;
    private static final long STUDY_ID = 12L;
    private static final Instant FIRST_FAVORITED_AT =
            Instant.parse("2026-07-28T06:30:00Z");
    private static final Instant UNFAVORITED_AT =
            Instant.parse("2026-07-30T01:15:00Z");

    private MemberRepository memberRepository;
    private ReportRepository reportRepository;
    private ReportFavoriteRepository reportFavoriteRepository;
    private StudyRepository studyRepository;
    private ReportFavoriteService service;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        reportRepository = mock(ReportRepository.class);
        reportFavoriteRepository = mock(ReportFavoriteRepository.class);
        studyRepository = mock(StudyRepository.class);
        PlatformTransactionManager transactionManager =
                mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));

        service = new ReportFavoriteService(
                memberRepository,
                reportRepository,
                reportFavoriteRepository,
                studyRepository,
                transactionManager,
                Clock.fixed(UNFAVORITED_AT, ZoneId.of("UTC"))
        );
        stubValidTarget();
    }

    @Test
    void 완료된_리포트를_처음_찜한다() {
        when(reportFavoriteRepository.findByMemberIdAndReportId(
                MEMBER_ID,
                REPORT_ID
        )).thenReturn(Optional.empty());
        when(reportFavoriteRepository.saveAndFlush(any(ReportFavorite.class)))
                .thenAnswer(invocation -> {
                    ReportFavorite favorite = invocation.getArgument(0);
                    ReflectionTestUtils.setField(
                            favorite,
                            "createdAt",
                            FIRST_FAVORITED_AT
                    );
                    return favorite;
                });
        when(reportFavoriteRepository.countByReportId(REPORT_ID))
                .thenReturn(3L);

        ReportFavoriteResult result = service.addFavorite(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(result.responseCode())
                .isEqualTo(ReportResponseCode.REPORT_FAVORITE_SUCCESS);
        assertThat(result.response().reportId()).isEqualTo(REPORT_ID);
        assertThat(result.response().favoritedByMe()).isTrue();
        assertThat(result.response().favoriteCount()).isEqualTo(3L);
        assertThat(result.response().favoritedAt()).isEqualTo(
                FIRST_FAVORITED_AT.atZone(ZoneId.of("Asia/Seoul"))
                        .toOffsetDateTime()
        );
    }

    @Test
    void 이미_찜한_리포트는_원래_찜한_시각을_유지한다() {
        ReportFavorite existing = favorite(FIRST_FAVORITED_AT);
        when(reportFavoriteRepository.findByMemberIdAndReportId(
                MEMBER_ID,
                REPORT_ID
        )).thenReturn(Optional.of(existing));
        when(reportFavoriteRepository.countByReportId(REPORT_ID))
                .thenReturn(3L);

        ReportFavoriteResult result = service.addFavorite(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(result.responseCode()).isEqualTo(
                ReportResponseCode.REPORT_FAVORITE_ALREADY_EXISTS
        );
        assertThat(result.response().favoritedAt()).isEqualTo(
                FIRST_FAVORITED_AT.atZone(ZoneId.of("Asia/Seoul"))
                        .toOffsetDateTime()
        );
        verify(reportFavoriteRepository, never())
                .saveAndFlush(any(ReportFavorite.class));
    }

    @Test
    void 동시_찜_중복은_이미_찜한_상태로_응답한다() {
        ReportFavorite committedByOtherRequest = favorite(FIRST_FAVORITED_AT);
        when(reportFavoriteRepository.findByMemberIdAndReportId(
                MEMBER_ID,
                REPORT_ID
        )).thenReturn(
                Optional.empty(),
                Optional.of(committedByOtherRequest)
        );
        when(reportFavoriteRepository.saveAndFlush(any(ReportFavorite.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(reportFavoriteRepository.countByReportId(REPORT_ID))
                .thenReturn(1L);

        ReportFavoriteResult result = service.addFavorite(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(result.responseCode()).isEqualTo(
                ReportResponseCode.REPORT_FAVORITE_ALREADY_EXISTS
        );
        assertThat(result.response().favoriteCount()).isEqualTo(1L);
    }

    @Test
    void 중복이_아닌_무결성_오류는_숨기지_않는다() {
        when(reportFavoriteRepository.findByMemberIdAndReportId(
                MEMBER_ID,
                REPORT_ID
        )).thenReturn(Optional.empty());
        when(reportFavoriteRepository.saveAndFlush(any(ReportFavorite.class)))
                .thenThrow(new DataIntegrityViolationException("foreign key"));

        assertThatThrownBy(() -> service.addFavorite(MEMBER_ID, REPORT_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 본인의_리포트_찜을_해제하고_현재_찜_수를_반환한다() {
        when(reportFavoriteRepository.deleteByMemberIdAndReportId(
                MEMBER_ID,
                REPORT_ID
        )).thenReturn(1);
        when(reportFavoriteRepository.countByReportId(REPORT_ID))
                .thenReturn(2L);

        var result = service.removeFavorite(MEMBER_ID, REPORT_ID);

        assertThat(result.responseCode()).isEqualTo(
                ReportResponseCode.REPORT_UNFAVORITE_SUCCESS
        );
        assertThat(result.response().reportId()).isEqualTo(REPORT_ID);
        assertThat(result.response().favoritedByMe()).isFalse();
        assertThat(result.response().favoriteCount()).isEqualTo(2L);
        assertThat(result.response().unfavoritedAt()).isEqualTo(
                UNFAVORITED_AT.atZone(ZoneId.of("Asia/Seoul"))
                        .toOffsetDateTime()
        );
        verify(reportFavoriteRepository)
                .deleteByMemberIdAndReportId(MEMBER_ID, REPORT_ID);
    }

    @Test
    void 이미_찜하지_않은_리포트도_멱등하게_성공한다() {
        when(reportFavoriteRepository.deleteByMemberIdAndReportId(
                MEMBER_ID,
                REPORT_ID
        )).thenReturn(0);
        when(reportFavoriteRepository.countByReportId(REPORT_ID))
                .thenReturn(3L);

        var result = service.removeFavorite(MEMBER_ID, REPORT_ID);

        assertThat(result.responseCode()).isEqualTo(
                ReportResponseCode.REPORT_ALREADY_UNFAVORITED
        );
        assertThat(result.response().favoritedByMe()).isFalse();
        assertThat(result.response().favoriteCount()).isEqualTo(3L);
    }

    @Test
    void 완료_상태가_아니어도_기존_찜은_해제할_수_있다() {
        Report inProgressReport = report(ReportStatus.IN_PROGRESS);
        when(reportRepository.findById(REPORT_ID))
                .thenReturn(Optional.of(inProgressReport));
        when(reportFavoriteRepository.deleteByMemberIdAndReportId(
                MEMBER_ID,
                REPORT_ID
        )).thenReturn(1);

        var result = service.removeFavorite(MEMBER_ID, REPORT_ID);

        assertThat(result.responseCode()).isEqualTo(
                ReportResponseCode.REPORT_UNFAVORITE_SUCCESS
        );
        verify(studyRepository, never())
                .findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void 찜을_해제할_리포트가_없으면_404를_반환한다() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.empty());

        assertError(
                ErrorCode.REPORT_NOT_FOUND,
                () -> service.removeFavorite(MEMBER_ID, REPORT_ID)
        );
        verify(reportFavoriteRepository, never())
                .deleteByMemberIdAndReportId(any(), any());
    }

    @Test
    void 탈퇴한_회원은_리포트_찜을_해제할_수_없다() {
        Member withdrawn = activeMember();
        ReflectionTestUtils.setField(
                withdrawn,
                "status",
                MemberStatus.WITHDRAWN
        );
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(withdrawn));

        assertError(
                ErrorCode.MEMBER_NOT_FOUND,
                () -> service.removeFavorite(MEMBER_ID, REPORT_ID)
        );
        verify(reportRepository, never()).findById(any());
        verify(reportFavoriteRepository, never())
                .deleteByMemberIdAndReportId(any(), any());
    }

    @Test
    void 존재하지_않는_리포트는_404를_반환한다() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.empty());

        assertError(
                ErrorCode.REPORT_NOT_FOUND,
                () -> service.addFavorite(MEMBER_ID, REPORT_ID)
        );
        verify(reportFavoriteRepository, never())
                .saveAndFlush(any(ReportFavorite.class));
    }

    @Test
    void 완료되지_않은_리포트는_409와_현재_상태를_반환한다() {
        Report report = report(ReportStatus.IN_PROGRESS);
        when(reportRepository.findById(REPORT_ID))
                .thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.addFavorite(MEMBER_ID, REPORT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode()).isEqualTo(
                                    ErrorCode.REPORT_FAVORITE_NOT_ALLOWED
                            );
                            assertThat(exception.getData())
                                    .containsEntry("status", "IN_PROGRESS");
                        }
                );
    }

    @Test
    void 삭제되거나_취소된_스터디의_리포트는_찜할_수_없다() {
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.empty());

        assertError(
                ErrorCode.REPORT_FAVORITE_ACCESS_DENIED,
                () -> service.addFavorite(MEMBER_ID, REPORT_ID)
        );
    }

    @Test
    void 탈퇴했거나_삭제된_회원은_숨긴다() {
        Member withdrawn = activeMember();
        ReflectionTestUtils.setField(
                withdrawn,
                "status",
                MemberStatus.WITHDRAWN
        );
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(withdrawn));

        assertError(
                ErrorCode.MEMBER_NOT_FOUND,
                () -> service.addFavorite(MEMBER_ID, REPORT_ID)
        );
        verify(reportRepository, never()).findById(any());
    }

    private void stubValidTarget() {
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(activeMember()));
        Report report = report(ReportStatus.DONE);
        when(reportRepository.findById(REPORT_ID))
                .thenReturn(Optional.of(report));
        Study study = mock(Study.class);
        when(study.getStatus()).thenReturn(StudyStatus.COMPLETED);
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
    }

    private Member activeMember() {
        return new Member("member@example.com", "hash", "member");
    }

    private Report report(ReportStatus status) {
        Report report = mock(Report.class);
        when(report.getStudyId()).thenReturn(STUDY_ID);
        when(report.getStatus()).thenReturn(status);
        return report;
    }

    private ReportFavorite favorite(Instant createdAt) {
        ReportFavorite favorite = ReportFavorite.of(MEMBER_ID, REPORT_ID);
        ReflectionTestUtils.setField(favorite, "createdAt", createdAt);
        return favorite;
    }

    private void assertError(
            ErrorCode expected,
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable
    ) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expected)
                );
    }
}

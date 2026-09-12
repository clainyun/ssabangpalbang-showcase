package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.domain.Report;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.response.ReportStatusResponse;
import com.ssafy.ssabangpalbang.report.repository.ReportRepository;
import com.ssafy.ssabangpalbang.study.domain.Study;
import com.ssafy.ssabangpalbang.study.domain.StudyMember;
import com.ssafy.ssabangpalbang.study.domain.StudyMemberStatus;
import com.ssafy.ssabangpalbang.study.repository.StudyMemberRepository;
import com.ssafy.ssabangpalbang.study.repository.StudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportStatusServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long REPORT_ID = 48L;
    private static final Long STUDY_ID = 13L;
    private static final Instant CREATED_AT =
            Instant.parse("2026-07-25T05:58:00Z");
    private static final Instant UPDATED_AT =
            Instant.parse("2026-07-25T06:00:02Z");
    private static final Instant COMPLETED_AT =
            Instant.parse("2026-07-25T06:01:30Z");

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private StudyRepository studyRepository;
    @Mock
    private StudyMemberRepository studyMemberRepository;

    private ReportStatusService service;

    @BeforeEach
    void setUp() {
        service = new ReportStatusService(
                reportRepository,
                memberRepository,
                studyRepository,
                studyMemberRepository
        );
        stubActiveMember();
    }

    @Test
    void 생성_대기_리포트의_null_단계를_RECORD_COLLECTION과_0퍼센트로_반환한다() {
        Report report = report(ReportStatus.PENDING, null, false, null);
        when(reportRepository.findById(REPORT_ID))
                .thenReturn(Optional.of(report));
        stubLeaderAccess();

        ReportStatusResponse response = service.getStatus(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(response.status()).isEqualTo(ReportStatus.PENDING);
        assertThat(response.progressStage()).isEqualTo("RECORD_COLLECTION");
        assertThat(response.progressRate()).isZero();
        assertThat(response.progressMessage())
                .isEqualTo("임장 기록을 수집할 준비를 하고 있습니다.");
        assertThat(response.detailAvailable()).isFalse();
        assertThat(response.retryAvailable()).isFalse();
        assertThat(response.failReason()).isNull();
        assertThat(response.createdAt().toString())
                .isEqualTo("2026-07-25T14:58+09:00");
        assertThat(response.updatedAt().toString())
                .isEqualTo("2026-07-25T15:00:02+09:00");
    }

    @ParameterizedTest
    @CsvSource({
            "RECORD_COLLECTION,20,임장 기록을 수집하고 있습니다.",
            "STT_VALIDATION,40,음성 기록의 변환 결과를 확인하고 있습니다.",
            "NORMALIZATION,55,참여자별 현장 기록을 정규화하고 있습니다.",
            "REPORT_GENERATION,70,참여자들의 현장 의견을 분석하고 있습니다.",
            "EVIDENCE_MAPPING,90,분석 결과와 현장 기록의 근거를 연결하고 있습니다.",
            "RESULT_SAVING,95,완성된 리포트 결과를 저장하고 있습니다."
    })
    void 생성_중_단계별_진행률과_문구를_반환한다(
            String stage,
            int progressRate,
            String progressMessage
    ) {
        Report report = report(
                ReportStatus.IN_PROGRESS,
                stage,
                false,
                null
        );
        when(reportRepository.findById(REPORT_ID))
                .thenReturn(Optional.of(report));
        stubLeaderAccess();

        ReportStatusResponse response = service.getStatus(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(response.progressStage()).isEqualTo(stage);
        assertThat(response.progressRate()).isEqualTo(progressRate);
        assertThat(response.progressMessage()).isEqualTo(progressMessage);
        assertThat(response.detailAvailable()).isFalse();
    }

    @Test
    void 완료_리포트는_참여_조회_없이_상세_조회_가능_상태를_반환한다() {
        Report report = report(
                ReportStatus.DONE,
                "REPORT_GENERATION",
                true,
                "이전 실패 사유"
        );
        when(reportRepository.findById(REPORT_ID))
                .thenReturn(Optional.of(report));

        ReportStatusResponse response = service.getStatus(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(response.progressStage()).isEqualTo("COMPLETED");
        assertThat(response.progressRate()).isEqualTo(100);
        assertThat(response.progressMessage())
                .isEqualTo("AI 임장 리포트가 완성되었습니다.");
        assertThat(response.detailAvailable()).isTrue();
        assertThat(response.retryAvailable()).isFalse();
        assertThat(response.failReason()).isNull();
        assertThat(response.completedAt().toString())
                .isEqualTo("2026-07-25T15:01:30+09:00");
        verify(studyRepository, never())
                .findByIdAndDeletedAtIsNull(STUDY_ID);
    }

    @Test
    void 실패_리포트는_마지막_단계와_재시도_가능_여부를_반환한다() {
        Report report = report(
                ReportStatus.FAILED,
                "REPORT_GENERATION",
                true,
                "AI 분석 결과를 처리하는 중 일시적인 오류가 발생했습니다."
        );
        when(reportRepository.findById(REPORT_ID))
                .thenReturn(Optional.of(report));
        stubLeaderAccess();

        ReportStatusResponse response = service.getStatus(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(response.progressRate()).isEqualTo(70);
        assertThat(response.progressStage()).isEqualTo("REPORT_GENERATION");
        assertThat(response.retryAvailable()).isTrue();
        assertThat(response.detailAvailable()).isFalse();
        assertThat(response.failReason()).isEqualTo(
                "AI 분석 결과를 처리하는 중 일시적인 오류가 발생했습니다."
        );
        assertThat(response.completedAt()).isNull();
    }

    @Test
    void 내부_예외_정보가_포함된_실패_사유는_일반_문구로_대체한다() {
        Report report = report(
                ReportStatus.FAILED,
                "STT_VALIDATION",
                false,
                "java.lang.IllegalStateException at com.ssafy.ReportWorker"
        );
        when(reportRepository.findById(REPORT_ID))
                .thenReturn(Optional.of(report));
        stubLeaderAccess();

        ReportStatusResponse response = service.getStatus(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(response.failReason())
                .isEqualTo("리포트 생성 중 일시적인 오류가 발생했습니다.");
        assertThat(response.retryAvailable()).isFalse();
    }

    @Test
    void 네트워크와_DB_정보가_포함된_실패_사유도_노출하지_않는다() {
        Report report = report(
                ReportStatus.FAILED,
                "REPORT_GENERATION",
                false,
                "Connection refused: 10.0.0.7:5432 / report_prod"
        );
        when(reportRepository.findById(REPORT_ID))
                .thenReturn(Optional.of(report));
        stubLeaderAccess();

        ReportStatusResponse response = service.getStatus(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(response.failReason())
                .isEqualTo("리포트 생성 중 일시적인 오류가 발생했습니다.");
    }

    @Test
    void ACTIVE_스터디원은_비완료_리포트를_조회할_수_있다() {
        Report report = report(
                ReportStatus.IN_PROGRESS,
                "RECORD_COLLECTION",
                false,
                null
        );
        when(reportRepository.findById(REPORT_ID))
                .thenReturn(Optional.of(report));
        Study study = study(99L);
        StudyMember studyMember = mock(StudyMember.class);
        when(study.getId()).thenReturn(STUDY_ID);
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        when(studyMember.getStatus())
                .thenReturn(StudyMemberStatus.ACTIVE);
        when(studyMemberRepository.findByStudyIdAndMemberId(
                STUDY_ID,
                MEMBER_ID
        )).thenReturn(Optional.of(studyMember));

        ReportStatusResponse response = service.getStatus(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(response.status()).isEqualTo(ReportStatus.IN_PROGRESS);
    }

    @Test
    void REMOVED_스터디원은_비완료_리포트_조회가_거부된다() {
        Report report = report(
                ReportStatus.IN_PROGRESS,
                "RECORD_COLLECTION",
                false,
                null
        );
        when(reportRepository.findById(REPORT_ID))
                .thenReturn(Optional.of(report));
        Study study = study(99L);
        StudyMember studyMember = mock(StudyMember.class);
        when(study.getId()).thenReturn(STUDY_ID);
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
        when(studyMember.getStatus())
                .thenReturn(StudyMemberStatus.REMOVED);
        when(studyMemberRepository.findByStudyIdAndMemberId(
                STUDY_ID,
                MEMBER_ID
        )).thenReturn(Optional.of(studyMember));

        assertThatThrownBy(() -> service.getStatus(MEMBER_ID, REPORT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REPORT_ACCESS_DENIED)
                );
    }

    @Test
    void 존재하지_않는_리포트는_REPORT_NOT_FOUND를_반환한다() {
        when(reportRepository.findById(REPORT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStatus(MEMBER_ID, REPORT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REPORT_NOT_FOUND)
                );
    }

    @Test
    void 탈퇴_회원은_리포트_상태를_조회할_수_없다() {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.WITHDRAWN);
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.getStatus(MEMBER_ID, REPORT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND)
                );
        verify(reportRepository, never()).findById(REPORT_ID);
    }

    private void stubActiveMember() {
        Member member = mock(Member.class);
        lenient().when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        lenient().when(member.getDeletedAt()).thenReturn(null);
        lenient().when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
    }

    private void stubLeaderAccess() {
        Study study = study(MEMBER_ID);
        when(studyRepository.findByIdAndDeletedAtIsNull(STUDY_ID))
                .thenReturn(Optional.of(study));
    }

    private Study study(Long leaderId) {
        Study study = mock(Study.class);
        lenient().when(study.getLeaderId()).thenReturn(leaderId);
        return study;
    }

    private Report report(
            ReportStatus status,
            String progressStage,
            boolean retryable,
            String failReason
    ) {
        Report report = mock(Report.class);
        lenient().when(report.getId()).thenReturn(REPORT_ID);
        lenient().when(report.getStudyId()).thenReturn(STUDY_ID);
        lenient().when(report.getStatus()).thenReturn(status);
        lenient().when(report.getProgressStage()).thenReturn(progressStage);
        lenient().when(report.isRetryable()).thenReturn(retryable);
        lenient().when(report.getFailReason()).thenReturn(failReason);
        lenient().when(report.getCreatedAt()).thenReturn(CREATED_AT);
        lenient().when(report.getUpdatedAt()).thenReturn(UPDATED_AT);
        lenient().when(report.getCompletedAt()).thenReturn(COMPLETED_AT);
        return report;
    }
}

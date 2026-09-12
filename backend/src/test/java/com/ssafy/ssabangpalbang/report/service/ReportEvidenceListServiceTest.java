package com.ssafy.ssabangpalbang.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrlProvider;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest;
import com.ssafy.ssabangpalbang.report.dto.response.ReportEvidenceListResponse;
import com.ssafy.ssabangpalbang.report.repository.ReportDetailQueryRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportEvidenceQueryRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportEvidenceListServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long REPORT_ID = 48L;
    private static final OffsetDateTime RECORDED_AT = OffsetDateTime.parse(
            "2026-07-20T14:30:00+09:00"
    );

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ReportDetailQueryRepository reportDetailQueryRepository;
    @Mock
    private ReportEvidenceQueryRepository reportEvidenceQueryRepository;
    @Mock
    private MediaAccessUrlProvider mediaAccessUrlProvider;
    @Mock
    private Validator validator;

    private ObjectMapper objectMapper;
    private ReportEvidenceListService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new ReportEvidenceListService(
                memberRepository,
                reportDetailQueryRepository,
                reportEvidenceQueryRepository,
                mediaAccessUrlProvider,
                objectMapper,
                validator
        );
        Member member = mock(Member.class);
        lenient().when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        lenient().when(member.getDeletedAt()).thenReturn(null);
        lenient().when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
        lenient().when(validator.validate(any(
                        ReportGenerationResultRequest.class
                )))
                .thenReturn(Set.of());
    }

    @Test
    void 필터와_커서를_적용해_직접_근거를_중복없이_반환한다()
            throws Exception {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.DONE,
                        true,
                        resultJson()
                )));
        when(reportEvidenceQueryRepository.countRequestedSources(
                REPORT_ID,
                List.of(201L, 205L)
        )).thenReturn(2L);
        when(reportEvidenceQueryRepository.findPage(
                REPORT_ID,
                "TEXT",
                "교통",
                List.of(201L, 205L),
                100L,
                3
        )).thenReturn(List.of(
                evidence(201L, "TEXT", 1, "첫 번째 근거"),
                evidence(205L, "TEXT", 2, "두 번째 근거"),
                evidence(209L, "TEXT", 3, "다음 페이지 근거")
        ));
        when(reportEvidenceQueryRepository.findLinks(
                REPORT_ID,
                List.of(201L, 205L)
        )).thenReturn(List.of(
                new ReportEvidenceQueryRepository.EvidenceLinkRow(
                        201L,
                        "feature.transport",
                        1
                ),
                new ReportEvidenceQueryRepository.EvidenceLinkRow(
                        201L,
                        "feature.transport",
                        1
                ),
                new ReportEvidenceQueryRepository.EvidenceLinkRow(
                        205L,
                        "common.transport",
                        2
                )
        ));
        when(reportEvidenceQueryRepository.findSummary(REPORT_ID))
                .thenReturn(new ReportEvidenceQueryRepository
                        .EvidenceSummaryRow(3, 2, 1));

        ReportEvidenceListResponse response = service.getEvidenceList(
                MEMBER_ID,
                REPORT_ID,
                "TEXT",
                " 교통 ",
                "201,205,201",
                "100",
                "2"
        );

        assertThat(response.content()).hasSize(2);
        assertThat(response.nextCursor()).isEqualTo(205L);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.filters().sourceIds())
                .containsExactly(201L, 205L);
        assertThat(response.content().get(0).participantLabel())
                .isEqualTo("참여자 1");
        assertThat(response.content().get(0).usedIn()).singleElement()
                .satisfies(usedIn -> {
                    assertThat(usedIn.claimKey())
                            .isEqualTo("feature.transport");
                    assertThat(usedIn.resultSection())
                            .isEqualTo("TOP_POSITIVE_FEATURE");
                    assertThat(usedIn.resultKey()).isEqualTo("역세권");
                });
        assertThat(response.content().get(1).usedIn()).singleElement()
                .satisfies(usedIn -> {
                    assertThat(usedIn.resultSection())
                            .isEqualTo("COMMON_OPINION");
                    assertThat(usedIn.resultKey()).isEqualTo("대중교통");
                });
        assertThat(response.summary().totalEvidenceCount()).isEqualTo(3);
    }

    @Test
    void 비참여자는_원문_근거_목록을_조회할_수_없다()
            throws Exception {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.DONE,
                        false,
                        resultJson()
                )));

        assertThatThrownBy(() -> service.getEvidenceList(
                MEMBER_ID, REPORT_ID, null, null, null, null, null
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.REPORT_EVIDENCE_ACCESS_DENIED)
        );

        verify(reportEvidenceQueryRepository, never()).findSummary(REPORT_ID);
    }

    @Test
    void 다른_리포트의_sourceId가_섞이면_400으로_거부한다()
            throws Exception {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.DONE,
                        true,
                        resultJson()
                )));
        when(reportEvidenceQueryRepository.countRequestedSources(
                REPORT_ID,
                List.of(201L, 999L)
        )).thenReturn(1L);

        assertThatThrownBy(() -> service.getEvidenceList(
                MEMBER_ID,
                REPORT_ID,
                null,
                null,
                "201,999",
                null,
                null
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                ErrorCode.REPORT_EVIDENCE_REPORT_MISMATCH
                        )
        );
    }

    @Test
    void 생성_중_리포트는_상태를_포함한_409를_반환한다()
            throws Exception {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.IN_PROGRESS,
                        true,
                        null
                )));

        assertThatThrownBy(() -> service.getEvidenceList(
                MEMBER_ID, REPORT_ID, null, null, null, null, null
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.REPORT_NOT_DONE);
                    assertThat(exception.getData()).containsEntry(
                            "status",
                            "IN_PROGRESS"
                    );
                }
        );
    }

    @Test
    void PHOTO_필터는_호환성을_유지하되_현재_직접_근거는_빈_목록이다()
            throws Exception {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.DONE,
                        true,
                        resultJson()
                )));
        when(reportEvidenceQueryRepository.findPage(
                REPORT_ID, "PHOTO", null, List.of(), null, 21
        )).thenReturn(List.of());
        when(reportEvidenceQueryRepository.findSummary(REPORT_ID))
                .thenReturn(new ReportEvidenceQueryRepository
                        .EvidenceSummaryRow(2, 1, 1));

        ReportEvidenceListResponse response = service.getEvidenceList(
                MEMBER_ID, REPORT_ID, "PHOTO", null, null, null, null
        );

        assertThat(response.content()).isEmpty();
        assertThat(response.filters().sourceType()).isEqualTo("PHOTO");
        assertThat(response.summary().photoCount()).isZero();
    }

    @Test
    void 긴_원문은_Unicode_기준_200자로_잘라_미리보기를_만든다()
            throws Exception {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.DONE,
                        true,
                        resultJson()
                )));
        when(reportEvidenceQueryRepository.findPage(
                REPORT_ID, null, null, List.of(), null, 21
        )).thenReturn(List.of(evidence(
                201L,
                "TEXT",
                1,
                "😀".repeat(205)
        )));
        when(reportEvidenceQueryRepository.findLinks(
                REPORT_ID,
                List.of(201L)
        )).thenReturn(List.of(new ReportEvidenceQueryRepository
                .EvidenceLinkRow(201L, "feature.transport", 1)));
        when(reportEvidenceQueryRepository.findSummary(REPORT_ID))
                .thenReturn(new ReportEvidenceQueryRepository
                        .EvidenceSummaryRow(1, 1, 0));

        ReportEvidenceListResponse response = service.getEvidenceList(
                MEMBER_ID, REPORT_ID, null, null, null, null, null
        );

        String preview = response.content().get(0).preview();
        assertThat(preview.codePointCount(0, preview.length())).isEqualTo(200);
        assertThat(preview).endsWith("…");
    }

    private ReportEvidenceQueryRepository.EvidenceRow evidence(
            Long sourceId,
            String sourceType,
            int participantNumber,
            String text
    ) {
        return new ReportEvidenceQueryRepository.EvidenceRow(
                sourceId,
                sourceType,
                "교통",
                501L,
                "지하철역 접근성",
                null,
                participantNumber,
                text,
                RECORDED_AT
        );
    }

    private String resultJson() throws Exception {
        return objectMapper.writeValueAsString(new ReportGenerationResultRequest(
                "리포트 제목",
                "리포트 요약",
                new ReportGenerationResultRequest.Metrics(2, 2, 100.0, 2),
                List.of(new ReportGenerationResultRequest.Feature(
                        1,
                        "역세권",
                        "역이 가깝습니다.",
                        1,
                        List.of("P1")
                )),
                List.of(),
                List.of(new ReportGenerationResultRequest.CommonOpinion(
                        "교통",
                        "대중교통",
                        ReportGenerationResultRequest.OpinionType.POSITIVE,
                        "대중교통이 편리하다는 공통 의견입니다.",
                        2,
                        List.of("P1", "P2")
                )),
                List.of(),
                List.of()
        ));
    }

    private ReportDetailQueryRepository.DetailRow detailRow(
            ReportStatus status,
            boolean participant,
            String resultJson
    ) {
        return new ReportDetailQueryRepository.DetailRow(
                REPORT_ID,
                88L,
                status,
                status == ReportStatus.DONE ? "COMPLETED" : "NORMALIZATION",
                resultJson,
                false,
                status == ReportStatus.DONE
                        ? OffsetDateTime.parse(
                                "2026-07-25T15:01:30+09:00"
                        )
                        : null,
                OffsetDateTime.parse("2026-07-25T15:01:31+09:00"),
                100L,
                "반포 자이아파트",
                "서울특별시 서초구 반포동",
                3410,
                "200812",
                4200,
                13L,
                "반포 임장 스터디",
                "실거주 관점 비교",
                OffsetDateTime.parse("2026-07-25T14:00:00+09:00"),
                2,
                participant,
                participant,
                false,
                0,
                null,
                true
        );
    }
}

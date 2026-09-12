package com.ssafy.ssabangpalbang.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrl;
import com.ssafy.ssabangpalbang.media.service.MediaAccessUrlProvider;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest;
import com.ssafy.ssabangpalbang.report.dto.response.ReportEvidenceDetailResponse;
import com.ssafy.ssabangpalbang.report.repository.ReportDetailQueryRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportEvidenceQueryRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportEvidenceDetailServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long REPORT_ID = 48L;
    private static final Long SOURCE_ID = 201L;
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
    void setUp() throws Exception {
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
        lenient().when(reportDetailQueryRepository.findDetail(
                MEMBER_ID,
                REPORT_ID
        )).thenReturn(Optional.of(completedReport(true)));
    }

    @Test
    void 인용된_TEXT의_전체_원문과_사용_위치를_반환한다()
            throws Exception {
        String fullText = "첫 줄\n" + "긴 원문".repeat(120);
        when(reportEvidenceQueryRepository.findDetail(REPORT_ID, SOURCE_ID))
                .thenReturn(Optional.of(textEvidence(fullText, "TEXT", null)));
        when(reportEvidenceQueryRepository.findLinks(
                REPORT_ID,
                List.of(SOURCE_ID)
        )).thenReturn(List.of(new ReportEvidenceQueryRepository
                .EvidenceLinkRow(SOURCE_ID, "feature.transport", 1)));

        ReportEvidenceDetailResponse response = service.getEvidenceDetail(
                MEMBER_ID,
                REPORT_ID,
                SOURCE_ID
        );

        assertThat(response.textContent()).isEqualTo(fullText);
        assertThat(response.media()).isNull();
        assertThat(response.usedIn()).singleElement().satisfies(usedIn -> {
            assertThat(usedIn.claimKey()).isEqualTo("feature.transport");
            assertThat(usedIn.resultSection())
                    .isEqualTo("TOP_POSITIVE_FEATURE");
            assertThat(usedIn.resultKey()).isEqualTo("역세권");
        });
        verifyNoInteractions(mediaAccessUrlProvider);
    }

    @Test
    void AI가_인용하지_않은_DONE_STT도_빈_usedIn으로_반환한다() {
        when(reportEvidenceQueryRepository.findDetail(REPORT_ID, SOURCE_ID))
                .thenReturn(Optional.of(textEvidence(
                        "변환 완료된 음성 원문",
                        "STT",
                        "DONE"
                )));

        ReportEvidenceDetailResponse response = service.getEvidenceDetail(
                MEMBER_ID,
                REPORT_ID,
                SOURCE_ID
        );

        assertThat(response.sourceType()).isEqualTo("STT");
        assertThat(response.sttStatus()).isEqualTo("DONE");
        assertThat(response.usedIn()).isEmpty();
        assertThat(response.media()).isNull();
    }

    @Test
    void PHOTO는_인용없이_검증된_접근_URL과_메타를_반환한다() {
        Instant expiresAt = Instant.parse("2026-07-20T07:40:00Z");
        when(reportEvidenceQueryRepository.findDetail(REPORT_ID, SOURCE_ID))
                .thenReturn(Optional.of(photoEvidence(true)));
        when(mediaAccessUrlProvider.issue(301L)).thenReturn(
                new MediaAccessUrl(
                        "https://media.example.com/evidence-301",
                        expiresAt
                )
        );

        ReportEvidenceDetailResponse response = service.getEvidenceDetail(
                MEMBER_ID,
                REPORT_ID,
                SOURCE_ID
        );

        assertThat(response.sourceType()).isEqualTo("PHOTO");
        assertThat(response.usedIn()).isEmpty();
        assertThat(response.textContent()).isNull();
        assertThat(response.media()).isNotNull();
        assertThat(response.media().available()).isTrue();
        assertThat(response.media().fileId()).isEqualTo(301L);
        assertThat(response.media().originalName()).isEqualTo("field.jpg");
        assertThat(response.media().contentType()).isEqualTo("image/jpeg");
        assertThat(response.media().sizeBytes()).isEqualTo(1024L);
        assertThat(response.media().accessUrl())
                .isEqualTo("https://media.example.com/evidence-301");
        assertThat(response.media().expiresAt().getOffset().toString())
                .isEqualTo("+09:00");
        verify(reportEvidenceQueryRepository, never()).findLinks(
                REPORT_ID,
                List.of(SOURCE_ID)
        );
    }

    @Test
    void 삭제되거나_만료된_PHOTO는_410으로_처리한다() {
        when(reportEvidenceQueryRepository.findDetail(REPORT_ID, SOURCE_ID))
                .thenReturn(Optional.of(photoEvidence(false)));

        assertThatThrownBy(() -> service.getEvidenceDetail(
                MEMBER_ID,
                REPORT_ID,
                SOURCE_ID
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(
                            ErrorCode.REPORT_EVIDENCE_MEDIA_UNAVAILABLE
                    );
                    assertThat(exception.getData())
                            .containsEntry("sourceId", SOURCE_ID)
                            .containsEntry("sourceType", "PHOTO");
                }
        );
        verifyNoInteractions(mediaAccessUrlProvider);
    }

    @Test
    void 저장소에서_PHOTO_실물이_없으면_410으로_변환한다() {
        when(reportEvidenceQueryRepository.findDetail(REPORT_ID, SOURCE_ID))
                .thenReturn(Optional.of(photoEvidence(true)));
        when(mediaAccessUrlProvider.issue(301L)).thenThrow(
                new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND)
        );

        assertThatThrownBy(() -> service.getEvidenceDetail(
                MEMBER_ID,
                REPORT_ID,
                SOURCE_ID
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                ErrorCode.REPORT_EVIDENCE_MEDIA_UNAVAILABLE
                        )
        );
    }

    @Test
    void 파일_저장소_일시_장애는_503을_유지한다() {
        when(reportEvidenceQueryRepository.findDetail(REPORT_ID, SOURCE_ID))
                .thenReturn(Optional.of(photoEvidence(true)));
        when(mediaAccessUrlProvider.issue(301L)).thenThrow(
                new BusinessException(ErrorCode.MEDIA_GATEWAY_UNAVAILABLE)
        );

        assertThatThrownBy(() -> service.getEvidenceDetail(
                MEMBER_ID,
                REPORT_ID,
                SOURCE_ID
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.MEDIA_GATEWAY_UNAVAILABLE)
        );
    }

    @Test
    void 다른_임장_세션의_sourceId는_400으로_거부한다() {
        when(reportEvidenceQueryRepository.findDetail(REPORT_ID, SOURCE_ID))
                .thenReturn(Optional.empty());
        when(reportEvidenceQueryRepository.findSourceRelation(
                REPORT_ID,
                SOURCE_ID
        )).thenReturn(new ReportEvidenceQueryRepository.SourceRelationRow(
                true,
                false
        ));

        assertThatThrownBy(() -> service.getEvidenceDetail(
                MEMBER_ID,
                REPORT_ID,
                SOURCE_ID
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.REPORT_EVIDENCE_REPORT_MISMATCH)
        );
    }

    @Test
    void 같은_세션의_삭제_기록이나_미완료_STT는_404로_숨긴다() {
        when(reportEvidenceQueryRepository.findDetail(REPORT_ID, SOURCE_ID))
                .thenReturn(Optional.empty());
        when(reportEvidenceQueryRepository.findSourceRelation(
                REPORT_ID,
                SOURCE_ID
        )).thenReturn(new ReportEvidenceQueryRepository.SourceRelationRow(
                true,
                true
        ));

        assertThatThrownBy(() -> service.getEvidenceDetail(
                MEMBER_ID,
                REPORT_ID,
                SOURCE_ID
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.REPORT_EVIDENCE_NOT_FOUND)
        );
    }

    @Test
    void 비참여자는_sourceId_존재_여부를_조회하기_전에_차단한다()
            throws Exception {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(completedReport(false)));

        assertThatThrownBy(() -> service.getEvidenceDetail(
                MEMBER_ID,
                REPORT_ID,
                SOURCE_ID
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.REPORT_EVIDENCE_ACCESS_DENIED)
        );
        verifyNoInteractions(reportEvidenceQueryRepository);
    }

    private ReportEvidenceQueryRepository.EvidenceDetailRow textEvidence(
            String text,
            String sourceType,
            String sttStatus
    ) {
        return new ReportEvidenceQueryRepository.EvidenceDetailRow(
                SOURCE_ID,
                sourceType,
                "교통",
                501L,
                "지하철역 접근성",
                null,
                1,
                text,
                sttStatus,
                null,
                null,
                null,
                null,
                false,
                RECORDED_AT
        );
    }

    private ReportEvidenceQueryRepository.EvidenceDetailRow photoEvidence(
            boolean available
    ) {
        return new ReportEvidenceQueryRepository.EvidenceDetailRow(
                SOURCE_ID,
                "PHOTO",
                "단지환경",
                503L,
                "단지 진입 경사",
                null,
                3,
                null,
                null,
                301L,
                "field.jpg",
                "image/jpeg",
                1024L,
                available,
                RECORDED_AT
        );
    }

    private ReportDetailQueryRepository.DetailRow completedReport(
            boolean participant
    ) throws Exception {
        return new ReportDetailQueryRepository.DetailRow(
                REPORT_ID,
                88L,
                ReportStatus.DONE,
                "COMPLETED",
                resultJson(),
                false,
                OffsetDateTime.parse("2026-07-25T15:01:30+09:00"),
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
                3,
                participant,
                participant,
                false,
                0,
                null,
                true
        );
    }

    private String resultJson() throws Exception {
        return objectMapper.writeValueAsString(new ReportGenerationResultRequest(
                "리포트 제목",
                "리포트 요약",
                new ReportGenerationResultRequest.Metrics(3, 3, 100.0, 1),
                List.of(new ReportGenerationResultRequest.Feature(
                        1,
                        "역세권",
                        "역이 가깝습니다.",
                        1,
                        List.of("P1")
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));
    }
}

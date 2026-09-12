package com.ssafy.ssabangpalbang.report.service;

import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldParticipantStatus;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldRecordSourceType;
import com.ssafy.ssabangpalbang.fieldvisit.domain.FieldSessionStatus;
import com.ssafy.ssabangpalbang.fieldvisit.stt.domain.SttStatus;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.media.domain.UploadStatus;
import com.ssafy.ssabangpalbang.report.dto.request.ReportCompleteRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportEvidenceResultRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest;
import com.ssafy.ssabangpalbang.report.repository.ReportEvidenceWriteRepository;
import com.ssafy.ssabangpalbang.report.repository.ReportInputQueryRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportCompleteValidatorTest {

    private static final OffsetDateTime RECORDED_AT = OffsetDateTime.of(
            2026,
            8,
            2,
            12,
            30,
            0,
            0,
            ZoneOffset.UTC
    );

    private final ReportCompleteValidator validator =
            new ReportCompleteValidator();

    @Test
    void TEXT와_DONE_STT만_저장하고_무효_근거와_중복을_제거한다() {
        ReportCompleteRequest request = validRequest(3);

        List<ReportEvidenceWriteRepository.EvidenceRow> rows =
                validator.validate(request, snapshot());

        assertThat(rows).containsExactly(
                new ReportEvidenceWriteRepository.EvidenceRow(
                        101L,
                        "feature.transport",
                        1
                ),
                new ReportEvidenceWriteRepository.EvidenceRow(
                        102L,
                        "feature.transport",
                        1
                )
        );
    }

    @Test
    void AI_집계가_DB_원본과_다르면_입력값_오류다() {
        ReportCompleteRequest request = validRequest(2);

        assertThatThrownBy(() -> validator.validate(request, snapshot()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                );
    }

    @Test
    void 데이터가_부족한_카테고리도_참여자_의견은_허용한다() {
        ReportCompleteRequest base = validRequest(3);
        ReportGenerationResultRequest generation = base.generationResult();
        ReportGenerationResultRequest.Category category =
                new ReportGenerationResultRequest.Category(
                        "TRANSPORT",
                        "의견 수가 적어 대표 특징을 확정하기 어렵습니다.",
                        1,
                        0,
                        false,
                        List.of(new ReportGenerationResultRequest
                                .ParticipantOpinion(
                                "P1",
                                "참여자 1",
                                ReportGenerationResultRequest.OpinionType
                                        .POSITIVE,
                                "역이 가까워 이동이 편리합니다."
                        ))
                );
        ReportCompleteRequest request = new ReportCompleteRequest(
                base.processingToken(),
                base.processingAttempt(),
                new ReportGenerationResultRequest(
                        generation.title(),
                        generation.summary(),
                        generation.metrics(),
                        generation.topPositiveFeatures(),
                        generation.topCautionFeatures(),
                        generation.commonOpinions(),
                        generation.conflictingOpinions(),
                        List.of(category)
                ),
                base.evidenceResult()
        );

        List<ReportEvidenceWriteRepository.EvidenceRow> rows =
                validator.validate(request, snapshot());

        assertThat(rows).containsExactly(
                new ReportEvidenceWriteRepository.EvidenceRow(
                        101L,
                        "feature.transport",
                        1
                ),
                new ReportEvidenceWriteRepository.EvidenceRow(
                        102L,
                        "feature.transport",
                        1
                )
        );
    }

    @Test
    void 생성_결과와_다른_순서의_claim은_거부한다() {
        ReportCompleteRequest base = validRequest(3);
        ReportGenerationResultRequest generation = base.generationResult();
        ReportGenerationResultRequest.Feature cautionFeature =
                new ReportGenerationResultRequest.Feature(
                        1,
                        "소음",
                        "대로변 소음을 우려했습니다.",
                        1,
                        List.of("P1")
                );
        ReportGenerationResultRequest reorderedGeneration =
                new ReportGenerationResultRequest(
                        generation.title(),
                        generation.summary(),
                        generation.metrics(),
                        generation.topPositiveFeatures(),
                        List.of(cautionFeature),
                        generation.commonOpinions(),
                        generation.conflictingOpinions(),
                        generation.categories()
                );

        ReportEvidenceResultRequest.Claim positiveClaim =
                base.evidenceResult().claims().get(0);
        ReportEvidenceResultRequest.Claim cautionClaim =
                new ReportEvidenceResultRequest.Claim(
                        "feature.noise",
                        ReportEvidenceResultRequest.ClaimType.FEATURE_CAUTION,
                        null,
                        "소음",
                        ReportGenerationResultRequest.OpinionType.CAUTION,
                        List.of("P1"),
                        positiveClaim.evidences(),
                        1
                );
        ReportEvidenceResultRequest.Claim movedPositiveClaim =
                new ReportEvidenceResultRequest.Claim(
                        positiveClaim.claimKey(),
                        positiveClaim.claimType(),
                        positiveClaim.category(),
                        positiveClaim.label(),
                        positiveClaim.opinionType(),
                        positiveClaim.participantRefs(),
                        positiveClaim.evidences(),
                        2
                );
        ReportCompleteRequest request = new ReportCompleteRequest(
                base.processingToken(),
                base.processingAttempt(),
                reorderedGeneration,
                new ReportEvidenceResultRequest(List.of(
                        cautionClaim,
                        movedPositiveClaim
                ))
        );

        assertThatThrownBy(() -> validator.validate(request, snapshot()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
                );
    }

    private ReportCompleteRequest validRequest(int fieldRecordCount) {
        List<ReportEvidenceResultRequest.Evidence> evidences =
                new ArrayList<>();
        evidences.add(evidence(
                ReportEvidenceResultRequest.SourceType.TEXT,
                101L,
                RECORDED_AT
        ));
        evidences.add(evidence(
                ReportEvidenceResultRequest.SourceType.TEXT,
                101L,
                RECORDED_AT
        ));
        evidences.add(evidence(
                ReportEvidenceResultRequest.SourceType.STT,
                102L,
                RECORDED_AT.plusMinutes(1)
        ));
        evidences.add(evidence(
                ReportEvidenceResultRequest.SourceType.STT,
                103L,
                RECORDED_AT.plusMinutes(2)
        ));
        evidences.add(evidence(
                ReportEvidenceResultRequest.SourceType.TEXT,
                104L,
                RECORDED_AT.plusMinutes(3)
        ));
        evidences.add(evidence(
                ReportEvidenceResultRequest.SourceType.TEXT,
                105L,
                RECORDED_AT.plusMinutes(4)
        ));
        evidences.add(evidence(
                ReportEvidenceResultRequest.SourceType.TEXT,
                999L,
                RECORDED_AT.plusMinutes(5)
        ));

        ReportGenerationResultRequest generation =
                new ReportGenerationResultRequest(
                        "교통 리포트",
                        "교통 접근성이 좋습니다.",
                        new ReportGenerationResultRequest.Metrics(
                                1,
                                1,
                                100.0,
                                fieldRecordCount
                        ),
                        List.of(new ReportGenerationResultRequest.Feature(
                                1,
                                "교통",
                                "교통 접근성이 좋습니다.",
                                1,
                                List.of("P1")
                        )),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new ReportGenerationResultRequest.Category(
                                "TRANSPORT",
                                "교통 관련 의견이 부족합니다.",
                                0,
                                0,
                                false,
                                List.of()
                        ))
                );
        ReportEvidenceResultRequest evidenceResult =
                new ReportEvidenceResultRequest(List.of(
                        new ReportEvidenceResultRequest.Claim(
                                "feature.transport",
                                ReportEvidenceResultRequest.ClaimType
                                        .FEATURE_POSITIVE,
                                null,
                                "교통",
                                ReportGenerationResultRequest.OpinionType
                                        .POSITIVE,
                                List.of("P1"),
                                List.copyOf(evidences),
                                1
                        )
                ));
        return new ReportCompleteRequest(
                "opaque-token",
                1,
                generation,
                evidenceResult
        );
    }

    private ReportEvidenceResultRequest.Evidence evidence(
            ReportEvidenceResultRequest.SourceType sourceType,
            long sourceId,
            OffsetDateTime recordedAt
    ) {
        return new ReportEvidenceResultRequest.Evidence(
                sourceType,
                sourceId,
                "P1",
                32L,
                "TRANSPORT",
                recordedAt,
                ReportEvidenceResultRequest.EvidenceRole.SUPPORT
        );
    }

    private ReportInputQueryRepository.Snapshot snapshot() {
        ReportInputQueryRepository.ContextRow context =
                new ReportInputQueryRepository.ContextRow(
                        48L,
                        7L,
                        100L,
                        3L,
                        FieldSessionStatus.ENDED,
                        RECORDED_AT.minusHours(1),
                        RECORDED_AT.minusMinutes(1),
                        RECORDED_AT,
                        true
                );
        List<ReportInputQueryRepository.ParticipantRow> participants =
                List.of(new ReportInputQueryRepository.ParticipantRow(
                        11L,
                        21L,
                        FieldParticipantStatus.ENDED,
                        RECORDED_AT.minusHours(1),
                        RECORDED_AT.minusMinutes(1)
                ));
        List<ReportInputQueryRepository.ChecklistItemRow> checklistItems =
                List.of(new ReportInputQueryRepository.ChecklistItemRow(
                        31L,
                        32L,
                        21L,
                        false,
                        "TRANSPORT",
                        "대중교통 접근성을 확인했나요?",
                        null,
                        1,
                        true,
                        RECORDED_AT.minusMinutes(1)
                ));
        List<ReportInputQueryRepository.FieldRecordRow> records = List.of(
                record(
                        101L,
                        FieldRecordSourceType.TEXT,
                        "도보 5분 거리입니다.",
                        null,
                        null,
                        RECORDED_AT
                ),
                record(
                        102L,
                        FieldRecordSourceType.STT,
                        "역이 가깝습니다.",
                        SttStatus.DONE,
                        null,
                        RECORDED_AT.plusMinutes(1)
                ),
                record(
                        103L,
                        FieldRecordSourceType.STT,
                        "아직 변환 중입니다.",
                        SttStatus.PROCESSING,
                        null,
                        RECORDED_AT.plusMinutes(2)
                ),
                photoRecord(),
                record(
                        105L,
                        FieldRecordSourceType.TEXT,
                        "삭제된 메모입니다.",
                        null,
                        RECORDED_AT.plusMinutes(5),
                        RECORDED_AT.plusMinutes(4)
                )
        );
        return new ReportInputQueryRepository.Snapshot(
                context,
                participants,
                checklistItems,
                records,
                List.of()
        );
    }

    private ReportInputQueryRepository.FieldRecordRow record(
            long sourceId,
            FieldRecordSourceType sourceType,
            String text,
            SttStatus sttStatus,
            OffsetDateTime deletedAt,
            OffsetDateTime recordedAt
    ) {
        return new ReportInputQueryRepository.FieldRecordRow(
                sourceId,
                3L,
                32L,
                21L,
                sourceType,
                text,
                sttStatus,
                null,
                deletedAt,
                recordedAt,
                recordedAt
        );
    }

    private ReportInputQueryRepository.FieldRecordRow photoRecord() {
        return new ReportInputQueryRepository.FieldRecordRow(
                104L,
                3L,
                32L,
                21L,
                FieldRecordSourceType.PHOTO,
                null,
                null,
                new ReportInputQueryRepository.PhotoFileRow(
                        77L,
                        "image/jpeg",
                        1024L,
                        UploadStatus.COMPLETED,
                        RECORDED_AT.plusDays(1),
                        null
                ),
                null,
                RECORDED_AT.plusMinutes(3),
                RECORDED_AT.plusMinutes(3)
        );
    }
}

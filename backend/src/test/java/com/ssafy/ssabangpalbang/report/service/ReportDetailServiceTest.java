package com.ssafy.ssabangpalbang.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import com.ssafy.ssabangpalbang.member.domain.Member;
import com.ssafy.ssabangpalbang.member.domain.MemberStatus;
import com.ssafy.ssabangpalbang.member.repository.MemberRepository;
import com.ssafy.ssabangpalbang.report.domain.ReportStatus;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest;
import com.ssafy.ssabangpalbang.report.dto.response.ReportDetailResponse;
import com.ssafy.ssabangpalbang.report.repository.ReportDetailQueryRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
class ReportDetailServiceTest {

    private static final Long MEMBER_ID = 7L;
    private static final Long REPORT_ID = 48L;
    private static final Long FIELD_SESSION_ID = 88L;
    private static final OffsetDateTime VISITED_AT = OffsetDateTime.parse(
            "2026-07-25T14:00:00+09:00"
    );
    private static final OffsetDateTime COMPLETED_AT = OffsetDateTime.parse(
            "2026-07-25T15:01:30+09:00"
    );
    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse(
            "2026-07-25T15:01:31+09:00"
    );

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ReportDetailQueryRepository reportDetailQueryRepository;
    @Mock
    private Validator validator;

    private ObjectMapper objectMapper;
    private ReportDetailService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new ReportDetailService(
                memberRepository,
                reportDetailQueryRepository,
                objectMapper,
                validator
        );
        stubActiveMember();
        lenient().when(validator.validate(any(
                        ReportGenerationResultRequest.class
                )))
                .thenReturn(Set.of());
    }

    @Test
    void 완료_리포트의_스토리_집계와_근거를_반환한다() throws Exception {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.DONE,
                        true,
                        true,
                        false,
                        resultJson()
                )));
        when(reportDetailQueryRepository.findEvidenceRows(REPORT_ID))
                .thenReturn(evidenceRows());

        ReportDetailResponse response = service.getDetail(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(response.reportId()).isEqualTo(REPORT_ID);
        assertThat(response.status()).isEqualTo(ReportStatus.DONE);
        assertThat(response.progressStage()).isEqualTo("COMPLETED");
        assertThat(response.study().participantCount()).isEqualTo(3);
        assertThat(response.metrics().evidenceCount()).isEqualTo(7);
        assertThat(response.metrics().hasAiEvidence()).isTrue();
        assertThat(response.topPositiveFeatures()).singleElement()
                .satisfies(feature -> {
                    assertThat(feature.mentionCount()).isEqualTo(2);
                    assertThat(feature.mentionRate()).isEqualTo(66.7);
                    assertThat(feature.sourceIds())
                            .containsExactly(102L, 101L);
                });
        assertThat(response.topCautionFeatures()).singleElement()
                .satisfies(feature -> {
                    assertThat(feature.mentionRate()).isEqualTo(33.3);
                    assertThat(feature.sourceIds()).containsExactly(103L);
                });
        assertThat(response.commonOpinions()).singleElement()
                .satisfies(opinion -> {
                    assertThat(opinion.participantCount()).isEqualTo(2);
                    assertThat(opinion.participantRate()).isEqualTo(66.7);
                    assertThat(opinion.sourceIds()).containsExactly(101L);
                });
        assertThat(response.conflictingOpinions()).singleElement()
                .satisfies(opinion -> {
                    assertThat(opinion.positiveParticipantRate())
                            .isEqualTo(33.3);
                    assertThat(opinion.cautionParticipantRate())
                            .isEqualTo(33.3);
                    assertThat(opinion.sourceIds())
                            .containsExactly(104L, 105L);
                });
        assertThat(response.categories()).singleElement()
                .satisfies(category -> {
                    assertThat(category.positiveOpinionCount()).isEqualTo(2);
                    assertThat(category.positiveOpinionRate())
                            .isEqualTo(66.7);
                    assertThat(category.cautionOpinionCount()).isEqualTo(1);
                    assertThat(category.cautionOpinionRate())
                            .isEqualTo(33.3);
                    assertThat(category.unrecordedOpinionCount()).isZero();
                    assertThat(category.unrecordedOpinionRate()).isZero();
                    assertThat(category.participantOpinions())
                            .extracting(ReportDetailResponse
                                    .DetailParticipantOpinion
                                    ::sourceIds)
                            .containsExactly(
                                    List.of(101L),
                                    List.of(106L),
                                    List.of(107L)
                            );
                });
        assertThat(response.viewer().isParticipant()).isTrue();
        assertThat(response.viewer().canViewEvidenceList()).isTrue();
        assertThat(response.viewer().canViewEvidenceOriginal()).isTrue();
        assertThat(response.viewer().canFavorite()).isTrue();
        assertThat(response.favoritedByMe()).isTrue();
        assertThat(response.favoriteCount()).isEqualTo(4);
        assertThat(response.postId()).isEqualTo(700L);
    }

    @Test
    void 비참여자도_완료_요약을_조회하지만_원문_권한은_없다()
            throws Exception {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.DONE,
                        false,
                        true,
                        false,
                        resultJson()
                )));
        when(reportDetailQueryRepository.findEvidenceRows(REPORT_ID))
                .thenReturn(evidenceRows());

        ReportDetailResponse response = service.getDetail(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(response.summary()).isEqualTo("교통 접근성이 좋습니다.");
        assertThat(response.viewer().isParticipant()).isFalse();
        assertThat(response.viewer().canViewEvidenceList()).isFalse();
        assertThat(response.viewer().canViewEvidenceOriginal()).isFalse();
        assertThat(response.viewer().canFavorite()).isTrue();
    }

    @Test
    void 생성_중_리포트는_상태_API_경로와_함께_409를_반환한다() {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.IN_PROGRESS,
                        true,
                        true,
                        false,
                        null
                )));

        assertThatThrownBy(() -> service.getDetail(MEMBER_ID, REPORT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.REPORT_NOT_DONE);
                            assertThat(exception.getData()).containsAllEntriesOf(
                                    Map.of(
                                            "reportId", REPORT_ID,
                                            "status", "IN_PROGRESS",
                                            "statusApi",
                                            "/api/v1/reports/48/status"
                                    )
                            );
                        }
                );
        verify(reportDetailQueryRepository, never())
                .findEvidenceRows(REPORT_ID);
    }

    @Test
    void 생성_실패_리포트는_재시도_가능_여부와_함께_409를_반환한다() {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.FAILED,
                        true,
                        true,
                        true,
                        null
                )));

        assertThatThrownBy(() -> service.getDetail(MEMBER_ID, REPORT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            ErrorCode.REPORT_GENERATION_FAILED
                                    );
                            assertThat(exception.getData())
                                    .containsEntry("reportId", REPORT_ID)
                                    .containsEntry("retryAvailable", true);
                        }
                );
    }

    @Test
    void 삭제되거나_취소된_원본_스터디의_리포트는_403이다()
            throws Exception {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.DONE,
                        true,
                        false,
                        false,
                        resultJson()
                )));

        assertThatThrownBy(() -> service.getDetail(MEMBER_ID, REPORT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REPORT_ACCESS_DENIED)
                );
    }

    @Test
    void 리포트가_없으면_404를_반환한다() {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(MEMBER_ID, REPORT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REPORT_NOT_FOUND)
                );
        verify(reportDetailQueryRepository, never())
                .findEvidenceRows(REPORT_ID);
    }

    @Test
    void 탈퇴한_회원은_상세_조회_전에_차단한다() {
        Member withdrawn = mock(Member.class);
        when(withdrawn.getStatus()).thenReturn(MemberStatus.WITHDRAWN);
        when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.getDetail(MEMBER_ID, REPORT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND)
                );
        verify(reportDetailQueryRepository, never())
                .findDetail(MEMBER_ID, REPORT_ID);
    }

    @Test
    void 완료_리포트의_결과_JSON이_손상되면_빈_상세로_보정하지_않는다() {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.DONE,
                        true,
                        true,
                        false,
                        "{"
                )));

        assertThatThrownBy(() -> service.getDetail(MEMBER_ID, REPORT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid completed report detail");
        verify(reportDetailQueryRepository, never())
                .findEvidenceRows(REPORT_ID);
    }

    @Test
    void 데이터가_부족한_카테고리의_실제_의견은_유지하되_직접_근거는_비운다()
            throws Exception {
        ReportGenerationResultRequest result = insufficientResult();
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.DONE,
                        true,
                        true,
                        false,
                        objectMapper.writeValueAsString(result)
                )));
        when(reportDetailQueryRepository.findEvidenceRows(REPORT_ID))
                .thenReturn(List.of());

        ReportDetailResponse response = service.getDetail(MEMBER_ID, REPORT_ID);

        assertThat(response.categories()).singleElement()
                .satisfies(category -> {
                    assertThat(category.dataSufficient()).isFalse();
                    assertThat(category.positiveOpinionCount()).isEqualTo(1);
                    assertThat(category.positiveOpinionRate()).isEqualTo(33.3);
                    assertThat(category.unrecordedOpinionCount()).isEqualTo(2);
                    assertThat(category.participantOpinions()).singleElement()
                            .satisfies(opinion -> assertThat(opinion.sourceIds())
                                    .isEmpty());
                });
    }

    @Test
    void 상세_응답에_내부_회원_ID와_참여자_ref를_노출하지_않는다()
            throws Exception {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.DONE,
                        true,
                        true,
                        false,
                        resultJson()
                )));
        when(reportDetailQueryRepository.findEvidenceRows(REPORT_ID))
                .thenReturn(evidenceRows());

        ReportDetailResponse response = service.getDetail(
                MEMBER_ID,
                REPORT_ID
        );
        String json = objectMapper.writeValueAsString(response);

        assertThat(json)
                .doesNotContain("memberId")
                .doesNotContain("participantRef")
                .doesNotContain("resultJson")
                .doesNotContain("\"P1\"")
                .doesNotContain("\"P2\"")
                .doesNotContain("\"P3\"");
        assertThat(json).contains("참여자 1", "참여자 2", "참여자 3");
    }

    @Test
    void 카테고리별_체크리스트_항목_수를_채운다() throws Exception {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.DONE,
                        true,
                        true,
                        false,
                        resultJson()
                )));
        when(reportDetailQueryRepository.findEvidenceRows(REPORT_ID))
                .thenReturn(evidenceRows());
        when(reportDetailQueryRepository
                .countChecklistItemsByCategory(FIELD_SESSION_ID))
                .thenReturn(List.of(
                        new ReportDetailQueryRepository
                                .CategoryChecklistCount("TRANSPORT", 6)
                ));

        ReportDetailResponse response = service.getDetail(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(response.categories()).singleElement()
                .satisfies(category -> assertThat(category.checklistItemCount())
                        .isEqualTo(6));
    }

    @Test
    void 매칭되지_않는_카테고리의_체크리스트_항목_수는_0이다()
            throws Exception {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.DONE,
                        true,
                        true,
                        false,
                        resultJson()
                )));
        when(reportDetailQueryRepository.findEvidenceRows(REPORT_ID))
                .thenReturn(evidenceRows());
        when(reportDetailQueryRepository
                .countChecklistItemsByCategory(FIELD_SESSION_ID))
                .thenReturn(List.of(
                        new ReportDetailQueryRepository
                                .CategoryChecklistCount("PARKING", 4)
                ));

        ReportDetailResponse response = service.getDetail(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(response.categories()).singleElement()
                .satisfies(category -> assertThat(category.checklistItemCount())
                        .isZero());
    }

    @Test
    void 세션에_체크리스트_항목이_없으면_체크리스트_항목_수는_0이다()
            throws Exception {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.DONE,
                        true,
                        true,
                        false,
                        resultJson()
                )));
        when(reportDetailQueryRepository.findEvidenceRows(REPORT_ID))
                .thenReturn(evidenceRows());
        when(reportDetailQueryRepository
                .countChecklistItemsByCategory(FIELD_SESSION_ID))
                .thenReturn(List.of());

        ReportDetailResponse response = service.getDetail(
                MEMBER_ID,
                REPORT_ID
        );

        assertThat(response.categories()).singleElement()
                .satisfies(category -> assertThat(category.checklistItemCount())
                        .isZero());
    }

    @Test
    void 상세_응답_JSON에_checklistItemCount를_노출한다()
            throws Exception {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.DONE,
                        true,
                        true,
                        false,
                        resultJson()
                )));
        when(reportDetailQueryRepository.findEvidenceRows(REPORT_ID))
                .thenReturn(evidenceRows());
        when(reportDetailQueryRepository
                .countChecklistItemsByCategory(FIELD_SESSION_ID))
                .thenReturn(List.of(
                        new ReportDetailQueryRepository
                                .CategoryChecklistCount("TRANSPORT", 6)
                ));

        ReportDetailResponse response = service.getDetail(
                MEMBER_ID,
                REPORT_ID
        );
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"checklistItemCount\":6");
    }

    private void stubActiveMember() {
        Member member = mock(Member.class);
        lenient().when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        lenient().when(member.getDeletedAt()).thenReturn(null);
        lenient().when(memberRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(member));
    }

    private String resultJson() throws Exception {
        return objectMapper.writeValueAsString(generationResult());
    }

    private ReportGenerationResultRequest generationResult() {
        return new ReportGenerationResultRequest(
                "반포 자이아파트 임장 리포트",
                "교통 접근성이 좋습니다.",
                new ReportGenerationResultRequest.Metrics(
                        6,
                        5,
                        83.3,
                        7
                ),
                List.of(new ReportGenerationResultRequest.Feature(
                        1,
                        "교통",
                        "역과 가깝다는 의견이 많았습니다.",
                        2,
                        List.of("P1", "P2")
                )),
                List.of(new ReportGenerationResultRequest.Feature(
                        1,
                        "소음",
                        "도로 소음을 우려했습니다.",
                        1,
                        List.of("P3")
                )),
                List.of(new ReportGenerationResultRequest.CommonOpinion(
                        "TRANSPORT",
                        "역세권",
                        ReportGenerationResultRequest.OpinionType.POSITIVE,
                        "역이 가깝다는 공통 의견입니다.",
                        2,
                        List.of("P1", "P2")
                )),
                List.of(new ReportGenerationResultRequest.ConflictingOpinion(
                        "TRANSPORT",
                        "도로 접근성",
                        "이동은 편하지만 소음 우려가 함께 있습니다.",
                        1,
                        1,
                        List.of("P1"),
                        List.of("P3")
                )),
                List.of(new ReportGenerationResultRequest.Category(
                        "TRANSPORT",
                        "교통에 대한 다양한 의견입니다.",
                        2,
                        1,
                        true,
                        List.of(
                                new ReportGenerationResultRequest
                                        .ParticipantOpinion(
                                        "P1",
                                        "참여자 1",
                                        ReportGenerationResultRequest
                                                .OpinionType.POSITIVE,
                                        "지하철 접근성이 좋습니다."
                                ),
                                new ReportGenerationResultRequest
                                        .ParticipantOpinion(
                                        "P2",
                                        "참여자 2",
                                        ReportGenerationResultRequest
                                                .OpinionType.POSITIVE,
                                        "버스 정류장이 가깝습니다."
                                ),
                                new ReportGenerationResultRequest
                                        .ParticipantOpinion(
                                        "P3",
                                        "참여자 3",
                                        ReportGenerationResultRequest
                                                .OpinionType.CAUTION,
                                        "도로 소음이 걱정됩니다."
                                )
                        )
                ))
        );
    }

    @Test
    void 비참여자는_미완료_리포트의_상태를_열람할_수_없다() {
        when(reportDetailQueryRepository.findDetail(MEMBER_ID, REPORT_ID))
                .thenReturn(Optional.of(detailRow(
                        ReportStatus.IN_PROGRESS,
                        false,
                        true,
                        false,
                        null
                )));

        assertThatThrownBy(() -> service.getDetail(MEMBER_ID, REPORT_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REPORT_ACCESS_DENIED)
                );
        verify(reportDetailQueryRepository, never())
                .findEvidenceRows(REPORT_ID);
    }

    private ReportGenerationResultRequest insufficientResult() {
        return new ReportGenerationResultRequest(
                "데이터 부족 리포트",
                "확인된 데이터가 충분하지 않습니다.",
                new ReportGenerationResultRequest.Metrics(3, 1, 33.3, 1),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ReportGenerationResultRequest.Category(
                        "NOISE",
                        "추가 확인이 필요합니다.",
                        1,
                        0,
                        false,
                        List.of(new ReportGenerationResultRequest
                                .ParticipantOpinion(
                                "P1",
                                "참여자 1",
                                ReportGenerationResultRequest.OpinionType
                                        .POSITIVE,
                                "현장에서는 조용했습니다."
                        ))
                ))
        );
    }

    private List<ReportDetailQueryRepository.EvidenceRow> evidenceRows() {
        return List.of(
                evidence("feature.positive", 1, 102L),
                evidence("feature.positive", 1, 101L),
                evidence("feature.caution", 2, 103L),
                evidence("common.transport", 3, 101L),
                evidence("conflict.transport", 4, 104L),
                evidence("conflict.transport", 4, 105L),
                evidence("category.transport.p1", 5, 101L),
                evidence("category.transport.p2", 6, 106L),
                evidence("category.transport.p3", 7, 107L)
        );
    }

    private ReportDetailQueryRepository.EvidenceRow evidence(
            String claimKey,
            int displayOrder,
            Long sourceId
    ) {
        return new ReportDetailQueryRepository.EvidenceRow(
                claimKey,
                displayOrder,
                sourceId
        );
    }

    private ReportDetailQueryRepository.DetailRow detailRow(
            ReportStatus status,
            boolean participant,
            boolean sourceValid,
            boolean retryable,
            String resultJson
    ) {
        return new ReportDetailQueryRepository.DetailRow(
                REPORT_ID,
                FIELD_SESSION_ID,
                status,
                status == ReportStatus.DONE ? "COMPLETED" : "NORMALIZATION",
                resultJson,
                retryable,
                status == ReportStatus.DONE ? COMPLETED_AT : null,
                UPDATED_AT,
                100L,
                "반포 자이아파트",
                "서울특별시 서초구 반포동",
                3410,
                "200812",
                4200,
                13L,
                "반포 실거주 임장 스터디",
                "실거주 관점 비교",
                VISITED_AT,
                 3,
                 participant,
                 participant,
                 true,
                4L,
                700L,
                sourceValid
        );
    }
}

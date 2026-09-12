package com.ssafy.ssabangpalbang.demoseed;

import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest.Category;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest.CommonOpinion;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest.ConflictingOpinion;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest.Feature;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest.Metrics;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest.OpinionType;
import com.ssafy.ssabangpalbang.report.dto.request.ReportGenerationResultRequest.ParticipantOpinion;

import java.util.List;

/**
 * 더미 리포트 본문을 만든다.
 *
 * <p>{@code report.result_json}은 AI 서비스가 보내는 {@link ReportGenerationResultRequest}가
 * 그대로 저장된 값이다. 그래서 임의 JSON을 짜지 않고 <b>그 record 를 그대로 조립</b>한다 —
 * 필드명·중첩 구조가 틀어지면 리포트 상세 화면이 깨지는데, record 를 쓰면 컴파일 단계에서 막힌다.</p>
 *
 * <p>participantRef 는 {@code ^P[1-9][0-9]*$} 형식이어야 하고, commonOpinions 는
 * participantCount 가 2 이상이며 participantRef 도 2개 이상이어야 한다(Bean Validation 제약).</p>
 */
final class DemoSeedReportPayload {

    private DemoSeedReportPayload() {
    }

    static ReportGenerationResultRequest build(
            DemoSeedCatalog.ReportContent content,
            int participantCount
    ) {
        int participants = Math.max(2, participantCount);
        List<String> allRefs = participantRefs(participants);
        List<String> firstTwo = allRefs.subList(0, 2);

        return new ReportGenerationResultRequest(
                content.title(),
                content.summary().strip(),
                new Metrics(12, 10, 83.3, 8),
                List.of(
                        new Feature(
                                1,
                                content.positiveLabel(),
                                "참가자 대부분이 만족스럽다고 평가한 항목입니다.",
                                participants,
                                allRefs
                        )
                ),
                List.of(
                        new Feature(
                                1,
                                content.cautionLabel(),
                                "확인이 더 필요하다는 의견이 나온 항목입니다.",
                                2,
                                firstTwo
                        )
                ),
                List.of(
                        new CommonOpinion(
                                content.category(),
                                content.commonLabel(),
                                OpinionType.POSITIVE,
                                "참가자들이 공통적으로 긍정적으로 본 부분입니다.",
                                participants,
                                allRefs
                        )
                ),
                List.of(
                        new ConflictingOpinion(
                                content.category(),
                                content.cautionLabel(),
                                "같은 항목을 두고 평가가 갈렸습니다. 동 위치와 방문 시간대 차이로 보입니다.",
                                participants - 1,
                                1,
                                allRefs.subList(0, participants - 1),
                                allRefs.subList(participants - 1, participants)
                        )
                ),
                List.of(
                        new Category(
                                content.category(),
                                "현장에서 확인한 내용을 항목별로 정리했습니다.",
                                participants - 1,
                                1,
                                true,
                                participantOpinions(allRefs)
                        )
                )
        );
    }

    private static List<String> participantRefs(int participants) {
        return java.util.stream.IntStream.rangeClosed(1, participants)
                .mapToObj(index -> "P" + index)
                .toList();
    }

    private static List<ParticipantOpinion> participantOpinions(List<String> refs) {
        return java.util.stream.IntStream.range(0, refs.size())
                .mapToObj(index -> new ParticipantOpinion(
                        refs.get(index),
                        "참가자 " + (index + 1),
                        index == refs.size() - 1 ? OpinionType.CAUTION : OpinionType.POSITIVE,
                        index == refs.size() - 1
                                ? "이 항목은 방문 시간대에 따라 다르게 느껴질 수 있어 재확인이 필요합니다."
                                : "직접 확인해 보니 기대한 수준에 부합했습니다."
                ))
                .toList();
    }
}

package com.ssafy.ssabangpalbang.fieldvisit.dto.response;

import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistGenerationProgress;
import com.ssafy.ssabangpalbang.fieldvisit.domain.ChecklistGenerationProgressStatus;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record ChecklistGenerationStatusResponse(
        String attemptId,
        String status,
        int progressRate,
        String progressStage,
        String progressMessage,
        OffsetDateTime updatedAt
) {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static ChecklistGenerationStatusResponse pending(String attemptId) {
        return new ChecklistGenerationStatusResponse(
                attemptId,
                "PENDING",
                0,
                "PENDING",
                "체크리스트 생성 요청을 기다리고 있어요.",
                null
        );
    }

    public static ChecklistGenerationStatusResponse from(
            ChecklistGenerationProgress progress
    ) {
        String message = progress.getStatus() == ChecklistGenerationProgressStatus.FAILED
                ? progress.getFailureMessage()
                : progress.getMessage();
        return new ChecklistGenerationStatusResponse(
                progress.getAttemptId(),
                progress.getStatus().name(),
                progress.getProgressRate(),
                progress.getStage().name(),
                message,
                progress.getUpdatedAt().atZone(SEOUL).toOffsetDateTime()
        );
    }
}

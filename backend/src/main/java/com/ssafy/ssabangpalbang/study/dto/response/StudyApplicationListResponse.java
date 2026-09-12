package com.ssafy.ssabangpalbang.study.dto.response;

import java.util.List;

public record StudyApplicationListResponse(
        List<ApplicationItem> content,
        Summary summary,
        Long nextCursor,
        boolean hasNext
) {
    public record ApplicationItem(
            Long applicationId,
            Applicant applicant,
            String intro,
            String purpose,
            String status,
            boolean canApprove,
            boolean canReject,
            String createdAt,
            String decidedAt
    ) {
    }

    public record Applicant(
            Long memberId,
            String nickname,
            String selectedCharacterId
    ) {
    }

    public record Summary(
            long pendingCount,
            long approvedCount,
            long rejectedCount,
            long currentMemberCount,
            int capacity
    ) {
    }
}

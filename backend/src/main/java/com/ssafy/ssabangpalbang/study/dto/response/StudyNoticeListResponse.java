package com.ssafy.ssabangpalbang.study.dto.response;

import com.ssafy.ssabangpalbang.study.domain.StudyNotice;

import java.util.List;

public record StudyNoticeListResponse(
        Long studyId,
        boolean isLeader,
        boolean readOnly,
        List<NoticeItem> content,
        Long nextCursor,
        boolean hasNext
) {
    public record NoticeItem(
            Long noticeId,
            String content,
            boolean canEdit,
            boolean canDelete,
            String createdAt,
            String updatedAt
    ) {
        public static NoticeItem from(
                StudyNotice notice, boolean canManage, String preview
        ) {
            return new NoticeItem(
                    notice.getId(), preview, canManage, canManage,
                    StudyNoticeResponse.format(notice.getCreatedAt()),
                    StudyNoticeResponse.format(notice.getUpdatedAt()));
        }
    }
}

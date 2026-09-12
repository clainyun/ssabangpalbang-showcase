package com.ssafy.ssabangpalbang.study.dto.response;

import java.time.Instant;

public record StudyNoticeDeleteResponse(Long studyId, Long noticeId, String deletedAt) {
    public static StudyNoticeDeleteResponse from(Long studyId, Long noticeId, Instant deletedAt) {
        return new StudyNoticeDeleteResponse(
                studyId, noticeId, StudyNoticeResponse.format(deletedAt));
    }
}

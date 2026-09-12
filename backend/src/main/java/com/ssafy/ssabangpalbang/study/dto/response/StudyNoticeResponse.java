package com.ssafy.ssabangpalbang.study.dto.response;

import com.ssafy.ssabangpalbang.study.domain.StudyNotice;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record StudyNoticeResponse(
        Long noticeId, Long studyId, String content, String createdAt, String updatedAt
) {
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    public static StudyNoticeResponse from(StudyNotice notice) {
        return new StudyNoticeResponse(
                notice.getId(), notice.getStudyId(), notice.getContent(),
                format(notice.getCreatedAt()), format(notice.getUpdatedAt()));
    }

    static String format(java.time.Instant instant) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(SEOUL_ZONE_ID));
    }
}

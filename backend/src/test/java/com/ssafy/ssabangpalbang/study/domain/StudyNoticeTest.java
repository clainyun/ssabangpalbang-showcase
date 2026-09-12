package com.ssafy.ssabangpalbang.study.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class StudyNoticeTest {
    @Test
    void createsAndUpdatesNotice() {
        StudyNotice notice = StudyNotice.create(10L, "공지");
        assertThat(notice.getStudyId()).isEqualTo(10L);
        assertThat(notice.getContent()).isEqualTo("공지");
        notice.updateContent("새 공지");
        assertThat(notice.getContent()).isEqualTo("새 공지");
        Instant deletedAt = Instant.parse("2026-07-30T00:00:00Z");
        notice.delete(deletedAt);
        assertThat(notice.getDeletedAt()).isEqualTo(deletedAt);
    }

    @Test
    void rejectsNullFields() {
        assertThatNullPointerException().isThrownBy(() -> StudyNotice.create(null, "공지"));
        assertThatNullPointerException().isThrownBy(() -> StudyNotice.create(10L, null));
        StudyNotice notice = StudyNotice.create(10L, "공지");
        assertThatNullPointerException().isThrownBy(() -> notice.updateContent(null));
        assertThatNullPointerException().isThrownBy(() -> notice.delete(null));
    }
}

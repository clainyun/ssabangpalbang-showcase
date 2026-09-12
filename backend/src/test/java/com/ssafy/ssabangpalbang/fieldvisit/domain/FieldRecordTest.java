package com.ssafy.ssabangpalbang.fieldvisit.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldRecordTest {

    @Test
    void softDelete는_deletedAt을_설정한다() {
        Instant now = Instant.parse("2026-07-30T01:00:00Z");
        FieldRecord record = FieldRecord.createText(
                1L, 2L, 3L, "memo", "11111111-1111-1111-1111-111111111111",
                "fp", now
        );

        Instant deletedAt = Instant.parse("2026-07-30T02:00:00Z");
        record.softDelete(deletedAt);

        assertThat(record.isDeleted()).isTrue();
        assertThat(record.getDeletedAt()).isEqualTo(deletedAt);
    }

    @Test
    void STT_진행중이면_isSttInProgress가_true이다() {
        FieldRecord pending = FieldRecord.reconstructStt(
                1L, 2L, 3L, null, FieldRecord.STT_STATUS_PENDING,
                "STT:1", Instant.now()
        );
        FieldRecord done = FieldRecord.reconstructStt(
                1L, 2L, 3L, "done", FieldRecord.STT_STATUS_DONE,
                "STT:2", Instant.now()
        );

        assertThat(pending.isSttInProgress()).isTrue();
        assertThat(done.isSttInProgress()).isFalse();
        assertThat(done.isSttDone()).isTrue();
    }

    @Test
    void textContent_공백은_생성_실패() {
        assertThatThrownBy(() -> FieldRecord.createText(
                1L, 2L, 3L, "   ", "11111111-1111-1111-1111-111111111111", "fp", Instant.now()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fingerprint_일치_여부를_확인한다() {
        FieldRecord record = FieldRecord.createPhoto(
                1L, 2L, 3L, 9L, "11111111-1111-1111-1111-111111111111", "abc", Instant.now()
        );
        assertThat(record.matchesFingerprint("abc")).isTrue();
        assertThat(record.matchesFingerprint("zzz")).isFalse();
    }
}

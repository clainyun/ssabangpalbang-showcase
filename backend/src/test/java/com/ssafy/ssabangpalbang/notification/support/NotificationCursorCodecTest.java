package com.ssafy.ssabangpalbang.notification.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.auth.token.JwtProperties;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationCursorCodecTest {

    private NotificationCursorCodec codec;

    @BeforeEach
    void setUp() {
        codec = new NotificationCursorCodec(
                new ObjectMapper(),
                new JwtProperties(
                        "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
                        Duration.ofMinutes(30),
                        Duration.ofDays(30)
                )
        );
    }

    @Test
    void 스냅샷_커서를_URL_safe_문자열로_왕복한다() {
        NotificationCursor cursor = cursor(false);

        String encoded = codec.encode(cursor);

        assertThat(encoded).doesNotContain("+", "/", "=");
        assertThat(codec.decode(encoded, 7L, false)).isEqualTo(cursor);
    }

    @Test
    void 변조되거나_형식이_잘못된_커서를_거부한다() {
        String encoded = codec.encode(cursor(false));
        String tampered = encoded.substring(0, encoded.length() - 1)
                + (encoded.endsWith("A") ? "B" : "A");

        assertInvalid(tampered, 7L, false);
        assertInvalid("not-a-signed-cursor", 7L, false);
        assertInvalid("a".repeat(2049), 7L, false);
    }

    @Test
    void 다른_수신자나_필터의_커서를_재사용할_수_없다() {
        String encoded = codec.encode(cursor(false));

        assertInvalid(encoded, 8L, false);
        assertInvalid(encoded, 7L, true);
    }

    private NotificationCursor cursor(boolean unreadOnly) {
        return NotificationCursor.of(
                7L,
                120L,
                OffsetDateTime.parse("2026-08-03T10:00:00+09:00"),
                unreadOnly,
                true,
                OffsetDateTime.parse("2026-08-02T09:00:00+09:00"),
                81L
        );
    }

    private void assertInvalid(
            String encoded,
            long recipientId,
            boolean unreadOnly
    ) {
        assertThatThrownBy(() -> codec.decode(encoded, recipientId, unreadOnly))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOTIFICATION_CURSOR_INVALID);
    }
}

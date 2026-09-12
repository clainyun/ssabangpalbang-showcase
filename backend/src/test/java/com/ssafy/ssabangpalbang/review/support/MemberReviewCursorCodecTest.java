package com.ssafy.ssabangpalbang.review.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.auth.token.JwtProperties;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class MemberReviewCursorCodecTest {

    private MemberReviewCursorCodec codec;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(
                "member-review-cursor-signing-key-32"
                        .getBytes(StandardCharsets.UTF_8)
        );
        codec = new MemberReviewCursorCodec(
                new ObjectMapper(),
                new JwtProperties(
                        secret,
                        Duration.ofMinutes(30),
                        Duration.ofDays(30)
                )
        );
    }

    @Test
    void 커서를_서명해_왕복하고_조회_회원에_바인딩한다() {
        MemberReviewCursor original = MemberReviewCursor.of(
                12L,
                Instant.parse("2026-08-03T13:10:00Z"),
                120L
        );

        String encoded = codec.encode(original);
        MemberReviewCursor decoded = codec.decode(encoded, 12L);

        assertThat(decoded).isEqualTo(original);

        BusinessException exception = catchThrowableOfType(
                () -> codec.decode(encoded, 13L),
                BusinessException.class
        );
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }

    @Test
    void 위변조되거나_형식이_잘못된_커서를_거절한다() {
        String encoded = codec.encode(MemberReviewCursor.of(
                12L,
                Instant.parse("2026-08-03T13:10:00Z"),
                120L
        ));

        for (String invalid : new String[]{
                encoded + "a",
                "not-a-cursor",
                "",
                " ".repeat(10)
        }) {
            BusinessException exception = catchThrowableOfType(
                    () -> codec.decode(invalid, 12L),
                    BusinessException.class
            );
            assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CURSOR);
        }
    }
}

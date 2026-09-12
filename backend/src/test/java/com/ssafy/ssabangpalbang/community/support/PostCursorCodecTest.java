package com.ssafy.ssabangpalbang.community.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.community.dto.request.PostSort;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostCursorCodecTest {

    private PostCursorCodec codec;

    @BeforeEach
    void setUp() {
        codec = new PostCursorCodec(new ObjectMapper());
    }

    @Test
    void roundTripsLatestAndHotCursors() {
        Instant createdAt = Instant.parse("2026-07-25T02:30:00Z");
        String latestHash = codec.filterHash(
                BoardType.INFORMATION,
                PostSort.LATEST,
                "옥수동"
        );
        PostCursor latest = PostCursor.latest(
                createdAt,
                152L,
                latestHash
        );

        assertThat(codec.decode(
                codec.encode(latest),
                PostSort.LATEST,
                latestHash
        )).isEqualTo(latest);

        String hotHash = codec.filterHash(
                null,
                PostSort.HOT,
                null
        );
        PostCursor hot = PostCursor.hot(
                new BigDecimal("38.20"),
                createdAt,
                152L,
                hotHash
        );

        assertThat(codec.decode(
                codec.encode(hot),
                PostSort.HOT,
                hotHash
        )).isEqualTo(hot);
    }

    @Test
    void bindsCursorToExactNormalizedFiltersAndSort() {
        String hash = codec.filterHash(
                BoardType.FREE,
                PostSort.LATEST,
                "검색어"
        );
        String cursor = codec.encode(PostCursor.latest(
                Instant.parse("2026-07-25T02:30:00Z"),
                12L,
                hash
        ));

        assertInvalid(() -> codec.decode(
                cursor,
                PostSort.HOT,
                hash
        ));
        assertInvalid(() -> codec.decode(
                cursor,
                PostSort.LATEST,
                codec.filterHash(
                        BoardType.INFORMATION,
                        PostSort.LATEST,
                        "검색어"
                )
        ));
        assertInvalid(() -> codec.decode(
                cursor,
                PostSort.LATEST,
                codec.filterHash(
                        BoardType.FREE,
                        PostSort.LATEST,
                        "다른 검색어"
                )
        ));
    }

    @Test
    void rejectsMalformedUnsupportedAndIncompletePayloads() {
        assertInvalid(() -> codec.decode(
                "a".repeat(2049),
                PostSort.LATEST,
                "hash"
        ));
        assertInvalid(() -> codec.decode(
                "not-base64!",
                PostSort.LATEST,
                "hash"
        ));
        assertInvalid(() -> codec.decode(
                encodeJson("""
                        {
                          "v": 2,
                          "sort": "LATEST",
                          "createdAt": "2026-07-25T02:30:00Z",
                          "postId": 12,
                          "filterHash": "hash"
                        }
                        """),
                PostSort.LATEST,
                "hash"
        ));
        assertInvalid(() -> codec.decode(
                encodeJson("""
                        {
                          "v": 1,
                          "sort": "LATEST",
                          "createdAt": "+300000-01-01T00:00:00Z",
                          "postId": 12,
                          "filterHash": "hash"
                        }
                        """),
                PostSort.LATEST,
                "hash"
        ));
        assertInvalid(() -> codec.decode(
                encodeJson("""
                        {
                          "v": 1,
                          "sort": "HOT",
                          "hotScore": "1E+100000",
                          "createdAt": "2026-07-25T02:30:00Z",
                          "postId": 12,
                          "filterHash": "hash"
                        }
                        """),
                PostSort.HOT,
                "hash"
        ));
        assertInvalid(() -> codec.decode(
                encodeJson("""
                        {
                          "v": 1,
                          "sort": "HOT",
                          "createdAt": "2026-07-25T02:30:00Z",
                          "postId": 12,
                          "filterHash": "hash"
                        }
                        """),
                PostSort.HOT,
                "hash"
        ));
        assertInvalid(() -> codec.decode(
                encodeJson("""
                        {
                          "v": 1,
                          "sort": "LATEST",
                          "createdAt": "invalid",
                          "postId": 0,
                          "filterHash": "hash"
                        }
                        """),
                PostSort.LATEST,
                "hash"
        ));
    }

    private String encodeJson(String json) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private void assertInvalid(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_CURSOR)
                );
    }
}

package com.ssafy.ssabangpalbang.community.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.ssabangpalbang.community.domain.BoardType;
import com.ssafy.ssabangpalbang.community.dto.request.PostSort;
import com.ssafy.ssabangpalbang.global.error.BusinessException;
import com.ssafy.ssabangpalbang.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class PostCursorCodec {

    private static final Instant MIN_CURSOR_INSTANT =
            Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX_CURSOR_INSTANT =
            Instant.parse("9999-12-31T23:59:59.999999Z");
    private static final int MAX_HOT_SCORE_PRECISION = 30;
    private static final int MAX_HOT_SCORE_SCALE = 2;
    private static final int MAX_ENCODED_CURSOR_LENGTH = 2048;

    private final ObjectMapper objectMapper;

    public String encode(PostCursor cursor) {
        CursorPayload payload = new CursorPayload(
                cursor.version(),
                cursor.sort().name(),
                cursor.hotScore() == null
                        ? null
                        : cursor.hotScore().toPlainString(),
                cursor.createdAt().toString(),
                cursor.postId(),
                cursor.filterHash()
        );

        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(json);
        } catch (JsonProcessingException exception) {
            throw invalidCursor();
        }
    }

    public PostCursor decode(
            String encoded,
            PostSort expectedSort,
            String expectedFilterHash
    ) {
        if (encoded == null
                || encoded.isBlank()
                || encoded.length() > MAX_ENCODED_CURSOR_LENGTH) {
            throw invalidCursor();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            CursorPayload payload = objectMapper.readValue(
                    decoded,
                    CursorPayload.class
            );
            return validate(payload, expectedSort, expectedFilterHash);
        } catch (IllegalArgumentException
                 | IOException
                 | DateTimeParseException exception) {
            throw invalidCursor();
        }
    }

    public String filterHash(
            BoardType boardType,
            PostSort sort,
            String keyword
    ) {
        String source = (boardType == null ? "*" : boardType.name())
                + '\u001f'
                + sort.name()
                + '\u001f'
                + (keyword == null ? "" : keyword);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    exception
            );
        }
    }

    private PostCursor validate(
            CursorPayload payload,
            PostSort expectedSort,
            String expectedFilterHash
    ) {
        if (payload == null
                || payload.v() == null
                || payload.v() != PostCursor.CURRENT_VERSION
                || payload.sort() == null
                || payload.createdAt() == null
                || payload.postId() == null
                || payload.postId() <= 0
                || payload.filterHash() == null) {
            throw invalidCursor();
        }

        PostSort sort;
        try {
            sort = PostSort.valueOf(payload.sort());
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
        if (sort != expectedSort
                || !Objects.equals(
                payload.filterHash(),
                expectedFilterHash
        )) {
            throw invalidCursor();
        }

        BigDecimal hotScore = null;
        if (sort == PostSort.HOT) {
            if (payload.hotScore() == null) {
                throw invalidCursor();
            }
            try {
                hotScore = new BigDecimal(payload.hotScore());
            } catch (NumberFormatException exception) {
                throw invalidCursor();
            }
            if (hotScore.signum() < 0
                    || hotScore.precision()
                    > MAX_HOT_SCORE_PRECISION
                    || hotScore.scale() < 0
                    || hotScore.scale() > MAX_HOT_SCORE_SCALE) {
                throw invalidCursor();
            }
        } else if (payload.hotScore() != null) {
            throw invalidCursor();
        }

        Instant createdAt = Instant.parse(payload.createdAt());
        if (createdAt.isBefore(MIN_CURSOR_INSTANT)
                || createdAt.isAfter(MAX_CURSOR_INSTANT)) {
            throw invalidCursor();
        }

        return new PostCursor(
                payload.v(),
                sort,
                hotScore,
                createdAt,
                payload.postId(),
                payload.filterHash()
        );
    }

    private BusinessException invalidCursor() {
        return new BusinessException(ErrorCode.INVALID_CURSOR);
    }

    private record CursorPayload(
            Integer v,
            String sort,
            String hotScore,
            String createdAt,
            Long postId,
            String filterHash
    ) {
    }
}

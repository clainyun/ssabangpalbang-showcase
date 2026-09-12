package com.ssafy.ssabangpalbang.community.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PostListCondition(
        String boardType,
        String sort,
        String keyword,
        String cursor,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
        Integer size
) {

    private static final int DEFAULT_SIZE = 20;

    public PostListCondition normalize() {
        return new PostListCondition(
                boardType,
                sort == null ? PostSort.LATEST.name() : sort,
                normalizeKeyword(keyword),
                cursor,
                size == null ? DEFAULT_SIZE : size
        );
    }

    private String normalizeKeyword(String value) {
        if (value == null) {
            return null;
        }

        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isSpace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = value.codePointBefore(end);
            if (!isSpace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }

        return start == end ? null : value.substring(start, end);
    }

    private boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint);
    }
}

package com.ssafy.ssabangpalbang.community.support;

import org.springframework.stereotype.Component;

@Component
public class PostContentPreviewer {

    private static final int MAX_CODE_POINTS = 150;

    public String preview(String content) {
        if (content == null) {
            return "";
        }

        String normalized = content
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\p{Z}\\s]+", " ")
                .trim();
        int codePointCount = normalized.codePointCount(
                0,
                normalized.length()
        );
        if (codePointCount <= MAX_CODE_POINTS) {
            return normalized;
        }

        int endIndex = normalized.offsetByCodePoints(
                0,
                MAX_CODE_POINTS
        );
        return normalized.substring(0, endIndex);
    }
}

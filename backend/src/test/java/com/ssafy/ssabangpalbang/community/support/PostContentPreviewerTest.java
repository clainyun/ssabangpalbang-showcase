package com.ssafy.ssabangpalbang.community.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostContentPreviewerTest {

    private final PostContentPreviewer previewer =
            new PostContentPreviewer();

    @Test
    void normalizesWhitespaceAndHandlesNull() {
        assertThat(previewer.preview(
                "  첫 줄\r\n\r\n둘째\t\t줄  "
        )).isEqualTo("첫 줄 둘째 줄");
        assertThat(previewer.preview(null)).isEmpty();
    }

    @Test
    void truncatesByUnicodeCodePointWithoutSplittingEmoji() {
        String content = "가".repeat(149) + "😀" + "마지막";

        String preview = previewer.preview(content);

        assertThat(preview.codePointCount(0, preview.length()))
                .isEqualTo(150);
        assertThat(preview).endsWith("😀");
    }
}

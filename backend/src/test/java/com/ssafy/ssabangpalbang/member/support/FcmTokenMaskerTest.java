package com.ssafy.ssabangpalbang.member.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FcmTokenMaskerTest {

    @Test
    void 긴_토큰은_앞_여섯_글자와_뒤_네_글자만_남긴다() {
        assertThat(FcmTokenMasker.mask("abcdefghijklmnopqrst"))
                .isEqualTo("abcdef***qrst");
    }

    @Test
    void 짧은_토큰은_전체를_마스킹한다() {
        assertThat(FcmTokenMasker.mask("abcdefghij")).isEqualTo("***");
    }

    @Test
    void null과_빈_토큰도_예외_없이_마스킹한다() {
        assertThat(FcmTokenMasker.mask(null)).isEqualTo("***");
        assertThat(FcmTokenMasker.mask("")).isEqualTo("***");
    }
}

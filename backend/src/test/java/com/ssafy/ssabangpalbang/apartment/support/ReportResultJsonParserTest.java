package com.ssafy.ssabangpalbang.apartment.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportResultJsonParserTest {

    private final ReportResultJsonParser parser =
            new ReportResultJsonParser(new ObjectMapper());

    @Test
    void 정상_JSON을_매핑한다() {
        var parsed = parser.parse(1L, """
                {"title":"제목","summary":"요약","analysisTags":["교통",3,"경사"],"isAiGenerated":false}
                """);

        assertThat(parsed.title()).isEqualTo("제목");
        assertThat(parsed.summary()).isEqualTo("요약");
        assertThat(parsed.analysisTags()).containsExactly("교통", "경사");
        assertThat(parsed.aiGenerated()).isFalse();
    }

    @Test
    void 비어있거나_깨진_JSON은_기본값을_반환한다() {
        for (String value : new String[]{null, " ", "{not json"}) {
            var parsed = parser.parse(1L, value);
            assertThat(parsed.title()).isNull();
            assertThat(parsed.summary()).isNull();
            assertThat(parsed.analysisTags()).isEmpty();
            assertThat(parsed.aiGenerated()).isTrue();
        }
    }

    @Test
    void 타입이_다르면_안전한_기본값을_쓴다() {
        var parsed = parser.parse(1L, """
                {"title":3,"analysisTags":"tag"}
                """);
        assertThat(parsed.title()).isNull();
        assertThat(parsed.analysisTags()).isEmpty();
        assertThat(parsed.aiGenerated()).isTrue();
    }
}

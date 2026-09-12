package com.ssafy.ssabangpalbang.community.dto.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentUpdateRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsContentWithoutCoercingItsValue() throws Exception {
        CommentUpdateRequest request = objectMapper.readValue(
                """
                        {"content":"수정할 댓글"}
                        """,
                CommentUpdateRequest.class
        );

        assertThat(request.content()).isEqualTo("수정할 댓글");

        CommentUpdateRequest nonString = objectMapper.readValue(
                """
                        {"content":123}
                        """,
                CommentUpdateRequest.class
        );
        assertThat(nonString.content()).isNull();
    }

    @Test
    void rejectsUnknownDuplicateAndNonObjectPayloads() {
        assertInvalid("""
                {"content":"댓글","authorId":12}
                """);
        assertInvalid("""
                {"content":"첫 번째","content":"두 번째"}
                """);
        assertInvalid("""
                ["댓글"]
                """);
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> objectMapper.readValue(
                json,
                CommentUpdateRequest.class
        )).isInstanceOf(JsonProcessingException.class);
    }
}

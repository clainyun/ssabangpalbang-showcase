package com.ssafy.ssabangpalbang.community.dto.request;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostUpdateRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void distinguishesMissingNullAndEmptyFields() throws Exception {
        PostUpdateRequest empty = objectMapper.readValue(
                "{}",
                PostUpdateRequest.class
        );
        PostUpdateRequest explicit = objectMapper.readValue(
                """
                        {
                          "title": null,
                          "apartmentId": null,
                          "fileIds": []
                        }
                        """,
                PostUpdateRequest.class
        );

        assertThat(empty.hasAnyField()).isFalse();
        assertThat(empty.isApartmentIdPresent()).isFalse();
        assertThat(empty.isFileIdsPresent()).isFalse();
        assertThat(explicit.isTitlePresent()).isTrue();
        assertThat(explicit.title()).isNull();
        assertThat(explicit.isApartmentIdPresent()).isTrue();
        assertThat(explicit.apartmentId()).isNull();
        assertThat(explicit.isFileIdsPresent()).isTrue();
        assertThat(explicit.fileIds()).isEmpty();
    }

    @Test
    void rejectsUnknownFieldsAndScalarCoercion() {
        assertThatThrownBy(() -> objectMapper.readValue(
                "{\"authorId\": 7}",
                PostUpdateRequest.class
        )).isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> objectMapper.readValue(
                "{\"title\": 123}",
                PostUpdateRequest.class
        )).isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> objectMapper.readValue(
                "{\"apartmentId\": \"15\"}",
                PostUpdateRequest.class
        )).isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> objectMapper.readValue(
                "{\"fileIds\": [\"401\"]}",
                PostUpdateRequest.class
        )).isInstanceOf(JsonMappingException.class);
    }
}

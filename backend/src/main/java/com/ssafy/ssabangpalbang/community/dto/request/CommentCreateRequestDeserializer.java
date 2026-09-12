package com.ssafy.ssabangpalbang.community.dto.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

public final class CommentCreateRequestDeserializer
        extends StdDeserializer<CommentCreateRequest> {

    public CommentCreateRequestDeserializer() {
        super(CommentCreateRequest.class);
    }

    @Override
    public CommentCreateRequest deserialize(
            JsonParser parser,
            DeserializationContext context
    ) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == null) {
            token = parser.nextToken();
        }
        if (token != JsonToken.START_OBJECT) {
            return context.reportInputMismatch(
                    CommentCreateRequest.class,
                    "요청 본문은 JSON 객체여야 합니다."
            );
        }

        String content = null;
        boolean contentSeen = false;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                return context.reportInputMismatch(
                        CommentCreateRequest.class,
                        "요청 본문의 JSON 형식이 올바르지 않습니다."
                );
            }

            String fieldName = parser.currentName();
            if (!"content".equals(fieldName)) {
                return context.reportInputMismatch(
                        CommentCreateRequest.class,
                        "허용되지 않은 요청 필드입니다: %s",
                        fieldName
                );
            }
            if (contentSeen) {
                return context.reportInputMismatch(
                        CommentCreateRequest.class,
                        "중복된 요청 필드입니다: content"
                );
            }
            contentSeen = true;

            JsonToken valueToken = parser.nextToken();
            if (valueToken == JsonToken.VALUE_STRING) {
                content = parser.getText();
            } else if (valueToken != JsonToken.VALUE_NULL) {
                parser.skipChildren();
                content = null;
            }
        }
        return new CommentCreateRequest(content);
    }
}

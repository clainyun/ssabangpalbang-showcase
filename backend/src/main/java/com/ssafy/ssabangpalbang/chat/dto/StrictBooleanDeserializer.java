package com.ssafy.ssabangpalbang.chat.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/** JSON의 true/false 토큰만 허용하고 문자열·숫자 coercion은 거절한다. */
public class StrictBooleanDeserializer extends JsonDeserializer<Boolean> {

    @Override
    public Boolean deserialize(
            JsonParser parser,
            DeserializationContext context
    ) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_TRUE)) {
            return Boolean.TRUE;
        }
        if (parser.hasToken(JsonToken.VALUE_FALSE)) {
            return Boolean.FALSE;
        }
        return (Boolean) context.handleUnexpectedToken(Boolean.class, parser);
    }
}

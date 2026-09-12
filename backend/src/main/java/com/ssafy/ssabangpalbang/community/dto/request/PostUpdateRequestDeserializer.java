package com.ssafy.ssabangpalbang.community.dto.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class PostUpdateRequestDeserializer
        extends StdDeserializer<PostUpdateRequest> {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "boardType",
            "title",
            "content",
            "apartmentId",
            "fileIds"
    );

    public PostUpdateRequestDeserializer() {
        super(PostUpdateRequest.class);
    }

    @Override
    public PostUpdateRequest deserialize(
            JsonParser parser,
            DeserializationContext context
    ) throws IOException {
        JsonNode root = parser.getCodec().readTree(parser);
        if (!(root instanceof ObjectNode object)) {
            return context.reportInputMismatch(
                    PostUpdateRequest.class,
                    "요청 본문은 JSON 객체여야 합니다."
            );
        }

        rejectUnknownFields(object, context);
        PostUpdateRequest request = new PostUpdateRequest();
        if (object.has("boardType")) {
            request.setBoardType(nullableText(
                    object.get("boardType"),
                    "boardType",
                    context
            ));
        }
        if (object.has("title")) {
            request.setTitle(nullableText(
                    object.get("title"),
                    "title",
                    context
            ));
        }
        if (object.has("content")) {
            request.setContent(nullableText(
                    object.get("content"),
                    "content",
                    context
            ));
        }
        if (object.has("apartmentId")) {
            request.setApartmentId(nullableLong(
                    object.get("apartmentId"),
                    "apartmentId",
                    context
            ));
        }
        if (object.has("fileIds")) {
            request.setFileIds(nullableLongList(
                    object.get("fileIds"),
                    context
            ));
        }
        return request;
    }

    private void rejectUnknownFields(
            ObjectNode object,
            DeserializationContext context
    ) throws IOException {
        Iterator<String> names = object.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!ALLOWED_FIELDS.contains(name)) {
                context.reportInputMismatch(
                        PostUpdateRequest.class,
                        "허용되지 않은 요청 필드입니다: %s",
                        name
                );
            }
        }
    }

    private String nullableText(
            JsonNode node,
            String field,
            DeserializationContext context
    ) throws IOException {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            return context.reportInputMismatch(
                    PostUpdateRequest.class,
                    "%s은(는) 문자열이어야 합니다.",
                    field
            );
        }
        return node.textValue();
    }

    private Long nullableLong(
            JsonNode node,
            String field,
            DeserializationContext context
    ) throws IOException {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            return context.reportInputMismatch(
                    PostUpdateRequest.class,
                    "%s은(는) 정수여야 합니다.",
                    field
            );
        }
        return node.longValue();
    }

    private List<Long> nullableLongList(
            JsonNode node,
            DeserializationContext context
    ) throws IOException {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!(node instanceof ArrayNode array)) {
            return context.reportInputMismatch(
                    PostUpdateRequest.class,
                    "fileIds는 정수 배열이어야 합니다."
            );
        }

        List<Long> fileIds = new ArrayList<>(array.size());
        for (JsonNode element : array) {
            fileIds.add(nullableLong(element, "fileIds", context));
        }
        return fileIds;
    }
}

package com.ssafy.ssabangpalbang.community.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonDeserialize(using = PostCreateRequestDeserializer.class)
public final class PostCreateRequest {

    @NotNull
    private final String boardType;

    @NotNull
    private final String title;

    @NotNull
    private final String content;

    @Positive
    private final Long apartmentId;

    @Size(max = 10)
    private final List<@NotNull @Positive Long> fileIds;

    @JsonCreator
    public PostCreateRequest(
            @JsonProperty("boardType") String boardType,
            @JsonProperty("title") String title,
            @JsonProperty("content") String content,
            @JsonProperty("apartmentId") Long apartmentId,
            @JsonProperty("fileIds") List<Long> fileIds
    ) {
        this.boardType = boardType;
        this.title = title;
        this.content = content;
        this.apartmentId = apartmentId;
        this.fileIds = fileIds == null ? null : new ArrayList<>(fileIds);
    }

    public String boardType() {
        return boardType;
    }

    public String title() {
        return title;
    }

    public String content() {
        return content;
    }

    public Long apartmentId() {
        return apartmentId;
    }

    public List<Long> fileIds() {
        return fileIds == null
                ? null
                : Collections.unmodifiableList(fileIds);
    }
}

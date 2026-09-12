package com.ssafy.ssabangpalbang.community.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonDeserialize(using = PostUpdateRequestDeserializer.class)
@Schema(description = "게시글 부분 수정 요청")
public final class PostUpdateRequest {

    @Schema(description = "변경할 게시판 유형", example = "FREE")
    private String boardType;

    @Schema(description = "변경할 제목", example = "성동구 임장 후기")
    private String title;

    @Schema(description = "변경할 본문")
    private String content;

    @Schema(
            description = "변경할 아파트 ID. 명시적 null은 연결 해제",
            nullable = true
    )
    private Long apartmentId;

    @ArraySchema(
            arraySchema = @Schema(
                    description = "수정 후 유지할 전체 첨부 파일 ID 목록"
            ),
            schema = @Schema(implementation = Long.class),
            maxItems = 10
    )
    private List<Long> fileIds;

    private boolean boardTypePresent;
    private boolean titlePresent;
    private boolean contentPresent;
    private boolean apartmentIdPresent;
    private boolean fileIdsPresent;

    public PostUpdateRequest() {
    }

    @JsonSetter("boardType")
    public void setBoardType(String boardType) {
        this.boardTypePresent = true;
        this.boardType = boardType;
    }

    @JsonSetter("title")
    public void setTitle(String title) {
        this.titlePresent = true;
        this.title = title;
    }

    @JsonSetter("content")
    public void setContent(String content) {
        this.contentPresent = true;
        this.content = content;
    }

    @JsonSetter("apartmentId")
    public void setApartmentId(Long apartmentId) {
        this.apartmentIdPresent = true;
        this.apartmentId = apartmentId;
    }

    @JsonSetter("fileIds")
    public void setFileIds(List<Long> fileIds) {
        this.fileIdsPresent = true;
        this.fileIds = fileIds == null
                ? null
                : new ArrayList<>(fileIds);
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

    @JsonIgnore
    @Schema(hidden = true)
    public boolean isBoardTypePresent() {
        return boardTypePresent;
    }

    @JsonIgnore
    @Schema(hidden = true)
    public boolean isTitlePresent() {
        return titlePresent;
    }

    @JsonIgnore
    @Schema(hidden = true)
    public boolean isContentPresent() {
        return contentPresent;
    }

    @JsonIgnore
    @Schema(hidden = true)
    public boolean isApartmentIdPresent() {
        return apartmentIdPresent;
    }

    @JsonIgnore
    @Schema(hidden = true)
    public boolean isFileIdsPresent() {
        return fileIdsPresent;
    }

    @JsonIgnore
    @Schema(hidden = true)
    public boolean hasAnyField() {
        return boardTypePresent
                || titlePresent
                || contentPresent
                || apartmentIdPresent
                || fileIdsPresent;
    }
}

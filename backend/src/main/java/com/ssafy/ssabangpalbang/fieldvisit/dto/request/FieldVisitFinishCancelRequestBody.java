package com.ssafy.ssabangpalbang.fieldvisit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 개인 임장 종료 취소(다시 진행 중으로) 요청 본문이다.
 *
 * <p>개인 임장 종료(finish)와 동일한 멱등 컨벤션을 따른다.
 * {@code clientRequestId}는 중복 취소 요청 방지용 UUID다.</p>
 */
public record FieldVisitFinishCancelRequestBody(
        @NotBlank
        @Size(max = 100)
        String clientRequestId
) {
}

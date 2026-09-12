package com.ssafy.ssabangpalbang.fieldvisit.client;

/**
 * FastAPI {@code POST /internal/v1/checklists/select} Port다.
 * 기존 {@link ChecklistAiClient#generate}와 분리한다.
 */
public interface ChecklistSelectAiClient {

    ChecklistAiSelectResponse select(ChecklistAiSelectRequest request);
}

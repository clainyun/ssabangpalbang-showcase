package com.ssafy.ssabangpalbang.fieldvisit.domain;

public enum ChecklistGenerationStage {
    PREPARING(10, "체크리스트 생성을 준비하고 있어요."),
    PERSONALIZATION(30, "관심 조건을 체크 항목에 반영하고 있어요."),
    AI_GENERATION(55, "맞춤 체크 항목을 만들고 있어요."),
    CONTENT_READY(80, "생성 결과를 정리하고 있어요."),
    RESULT_SAVING(90, "체크리스트를 저장하고 있어요."),
    COMPLETED(100, "체크리스트가 완성됐어요.");

    private final int progressRate;
    private final String message;

    ChecklistGenerationStage(int progressRate, String message) {
        this.progressRate = progressRate;
        this.message = message;
    }

    public int progressRate() {
        return progressRate;
    }

    public String message() {
        return message;
    }
}

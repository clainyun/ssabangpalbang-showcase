package com.ssafy.ssabangpalbang.review.domain;

/**
 * 평가 태그의 분류다. 태그(ReviewTag)를 사람·협업/임장·활동 두 갈래로 묶는다.
 */
public enum ReviewTagCategory {

    PERSON("사람·협업"),
    VISIT("임장·활동");

    private final String label;

    ReviewTagCategory(String label) {
        this.label = label;
    }

    public String getCode() {
        return name();
    }

    public String getLabel() {
        return label;
    }
}

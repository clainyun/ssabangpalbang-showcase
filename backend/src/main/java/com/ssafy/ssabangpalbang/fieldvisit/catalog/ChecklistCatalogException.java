package com.ssafy.ssabangpalbang.fieldvisit.catalog;

/**
 * 카탈로그 JSON 로딩·검증 실패다. 애플리케이션 기동을 막지 않고
 * 호출 측에서 기존 생성/하드코딩 fallback으로 전환한다.
 */
public class ChecklistCatalogException extends RuntimeException {

    public ChecklistCatalogException(String message) {
        super(message);
    }

    public ChecklistCatalogException(String message, Throwable cause) {
        super(message, cause);
    }
}

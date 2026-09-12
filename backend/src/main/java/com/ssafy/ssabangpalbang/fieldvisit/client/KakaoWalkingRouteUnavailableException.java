package com.ssafy.ssabangpalbang.fieldvisit.client;

/** 카카오 보행 경로의 설정·통신·응답 계약 실패를 route 서비스까지 전달한다. */
public class KakaoWalkingRouteUnavailableException extends RuntimeException {

    public KakaoWalkingRouteUnavailableException(String message) {
        super(message);
    }

    public KakaoWalkingRouteUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

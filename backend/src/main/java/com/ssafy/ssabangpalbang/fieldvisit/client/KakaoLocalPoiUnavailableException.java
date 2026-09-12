package com.ssafy.ssabangpalbang.fieldvisit.client;

/** 카카오 로컬 API의 설정·통신·응답 계약 실패를 route 서비스까지 전달한다. */
public class KakaoLocalPoiUnavailableException extends RuntimeException {

    public KakaoLocalPoiUnavailableException(String message) {
        super(message);
    }

    public KakaoLocalPoiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.ssafy.ssabangpalbang.fieldvisit.client;

/**
 * 체크리스트 생성을 위해 외부 AI(FastAPI)를 호출하는 Port다.
 *
 * <p>구현체는 production에서는 {@link RestChecklistAiClient}이고, 테스트에서는
 * Fake/Mock으로 교체된다. 오케스트레이션은 이 Port에만 의존한다.</p>
 *
 * <p>기술적 실패는 반환값이 아니라 예외로 표현한다. 호출자는
 * {@link ChecklistAiConnectionException}, {@link ChecklistAiTimeoutException},
 * {@link ChecklistAiServerErrorException}을 구분해서 잡아 로그·테스트에서
 * 원인을 남길 수 있다.</p>
 */
public interface ChecklistAiClient {

    /**
     * @throws ChecklistAiConnectionException FastAPI에 연결할 수 없는 경우
     * @throws ChecklistAiTimeoutException connect 또는 read timeout이 발생한 경우
     * @throws ChecklistAiServerErrorException FastAPI가 5xx를 반환한 경우
     * @throws ChecklistAiInvalidResponseException 응답이 비어 있거나 유효한 JSON이
     *         아니거나 필수 필드가 없는 등 파싱 자체가 불가능한 경우
     */
    ChecklistAiGenerateResponse generate(ChecklistAiGenerateRequest request);
}

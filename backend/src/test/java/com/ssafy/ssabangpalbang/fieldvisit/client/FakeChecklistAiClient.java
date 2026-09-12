package com.ssafy.ssabangpalbang.fieldvisit.client;

/**
 * 테스트 전용 {@link ChecklistAiClient} Fake 구현체다(AI-002 Stage 3).
 *
 * <p>production RestClient 구현체가 아직 없는 상태에서도 오케스트레이션·검증
 * 로직을 테스트할 수 있도록, 성공 응답 또는 지정한 예외를 그대로 반환/던지는
 * 단순 구현이다. 마지막으로 전달받은 요청을 보관해 personalization 조립 결과를
 * 검증하는 데에도 사용할 수 있다.</p>
 */
public class FakeChecklistAiClient implements ChecklistAiClient {

    private final ChecklistAiGenerateResponse response;
    private final RuntimeException exceptionToThrow;
    private ChecklistAiGenerateRequest lastRequest;

    private FakeChecklistAiClient(
            ChecklistAiGenerateResponse response,
            RuntimeException exceptionToThrow
    ) {
        this.response = response;
        this.exceptionToThrow = exceptionToThrow;
    }

    public static FakeChecklistAiClient returning(ChecklistAiGenerateResponse response) {
        return new FakeChecklistAiClient(response, null);
    }

    public static FakeChecklistAiClient throwing(RuntimeException exception) {
        return new FakeChecklistAiClient(null, exception);
    }

    @Override
    public ChecklistAiGenerateResponse generate(ChecklistAiGenerateRequest request) {
        this.lastRequest = request;
        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }
        return response;
    }

    public ChecklistAiGenerateRequest getLastRequest() {
        return lastRequest;
    }
}

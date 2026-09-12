package com.ssafy.ssabangpalbang.fieldvisit.client;

/**
 * 테스트용 {@link ChecklistSelectAiClient}.
 */
public class FakeChecklistSelectAiClient implements ChecklistSelectAiClient {

    private final ChecklistAiSelectResponse response;
    private final RuntimeException exceptionToThrow;
    private ChecklistAiSelectRequest lastRequest;
    private int callCount;

    private FakeChecklistSelectAiClient(
            ChecklistAiSelectResponse response,
            RuntimeException exceptionToThrow
    ) {
        this.response = response;
        this.exceptionToThrow = exceptionToThrow;
    }

    public static FakeChecklistSelectAiClient returning(ChecklistAiSelectResponse response) {
        return new FakeChecklistSelectAiClient(response, null);
    }

    public static FakeChecklistSelectAiClient throwing(RuntimeException exception) {
        return new FakeChecklistSelectAiClient(null, exception);
    }

    @Override
    public ChecklistAiSelectResponse select(ChecklistAiSelectRequest request) {
        this.callCount++;
        this.lastRequest = request;
        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }
        return response;
    }

    public ChecklistAiSelectRequest getLastRequest() {
        return lastRequest;
    }

    public int getCallCount() {
        return callCount;
    }
}

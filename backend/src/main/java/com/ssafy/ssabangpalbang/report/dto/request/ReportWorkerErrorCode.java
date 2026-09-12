package com.ssafy.ssabangpalbang.report.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "AI Report Worker가 전달할 수 있는 안전한 실패 코드",
        type = "string",
        maxLength = 100,
        allowableValues = {
                "INVALID_EVENT",
                "CONTRACT_CONFLICT",
                "GENERATION_RESULT_INVALID",
                "NORMALIZATION_FAILED",
                "SOURCE_LOAD_FAILED",
                "PROVIDER_FAILED",
                "MISSING_EVIDENCE",
                "INPUT_TOO_LARGE",
                "INVALID_REFERENCE",
                "SCHEMA_VALIDATION_FAILED",
                "PYDANTIC_VALIDATION_FAILED",
                "BACKEND_TRANSIENT",
                "BACKEND_CONTRACT_ERROR",
                "BACKEND_AUTH_ERROR",
                "UNKNOWN_FAILURE"
        }
)
public enum ReportWorkerErrorCode {
    INVALID_EVENT("리포트 요청 이벤트가 올바르지 않습니다."),
    CONTRACT_CONFLICT("리포트 요청 계약이 충돌합니다."),
    GENERATION_RESULT_INVALID("리포트 생성 결과가 유효하지 않습니다."),
    NORMALIZATION_FAILED("리포트 입력 정규화에 실패했습니다."),
    SOURCE_LOAD_FAILED("리포트 원본 데이터 조회에 실패했습니다."),
    PROVIDER_FAILED("AI 제공자 호출에 실패했습니다."),
    MISSING_EVIDENCE("리포트 근거 연결에 실패했습니다."),
    INPUT_TOO_LARGE("리포트 입력 크기가 제한을 초과했습니다."),
    INVALID_REFERENCE("리포트 참조가 올바르지 않습니다."),
    SCHEMA_VALIDATION_FAILED("AI 응답 스키마 검증에 실패했습니다."),
    PYDANTIC_VALIDATION_FAILED("AI 응답 데이터 검증에 실패했습니다."),
    BACKEND_TRANSIENT("Backend 일시 오류가 발생했습니다."),
    BACKEND_CONTRACT_ERROR("Backend 계약 오류가 발생했습니다."),
    BACKEND_AUTH_ERROR("Backend 인증에 실패했습니다."),
    UNKNOWN_FAILURE("리포트 생성 중 오류가 발생했습니다.");

    private final String safeMessage;

    ReportWorkerErrorCode(String safeMessage) {
        this.safeMessage = safeMessage;
    }

    public String safeMessage() {
        return safeMessage;
    }

    public boolean matchesSafeMessage(String message) {
        return safeMessage.equals(message);
    }
}

package com.ssafy.ssabangpalbang.global.error;

import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.global.response.ResponseCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/test")
class GlobalExceptionHandlerTestController {

    @PostMapping("/success")
    ApiResponse<Map<String, String>> success() {
        return ApiResponse.success(
                TestResponseCode.SUCCESS,
                Map.of("message", "ok")
        );
    }

    @PostMapping("/success-without-data")
    ApiResponse<Void> successWithoutPayload() {
        return ApiResponse.success(TestResponseCode.SUCCESS);
    }

    @GetMapping("/business")
    ApiResponse<Void> businessException() {
        throw new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "테스트 리소스를 찾을 수 없습니다."
        );
    }

    @GetMapping("/business-with-data")
    ApiResponse<Void> businessExceptionWithData() {
        throw new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND,
                Map.of("fileId", 401)
        );
    }

    @GetMapping("/business-without-data")
    ApiResponse<Void> businessExceptionWithoutData() {
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @PostMapping("/validation")
    ApiResponse<TestRequest> validation(
            @Valid @RequestBody TestRequest request
    ) {
        return ApiResponse.success(TestResponseCode.SUCCESS, request);
    }

    @PostMapping("/validation/multiple")
    ApiResponse<MultipleFieldTestRequest> multipleValidation(
            @Valid @RequestBody MultipleFieldTestRequest request
    ) {
        return ApiResponse.success(TestResponseCode.SUCCESS, request);
    }

    @PostMapping("/body")
    ApiResponse<Void> requestBody(
            @RequestBody Map<String, Object> body
    ) {
        return ApiResponse.success(TestResponseCode.SUCCESS);
    }

    @GetMapping("/query-validation")
    ApiResponse<Void> queryValidation(
            @RequestParam(name = "page")
            @Min(value = 0, message = "페이지는 0 이상이어야 합니다.")
            int page
    ) {
        return ApiResponse.success(TestResponseCode.SUCCESS);
    }

    @GetMapping("/path-validation/{memberId}")
    ApiResponse<Void> pathValidation(
            @PathVariable("memberId") Long memberId
    ) {
        return ApiResponse.success(TestResponseCode.SUCCESS);
    }

    record TestRequest(
            @NotBlank(message = "이름은 필수입니다.")
            String name
    ) {
    }

    record MultipleFieldTestRequest(
            @NotBlank(message = "제목은 필수입니다.")
            String title,
            @NotBlank(message = "본문은 필수입니다.")
            String content
    ) {
    }

    private enum TestResponseCode implements ResponseCode {

        SUCCESS("TEST_SUCCESS", "테스트 요청이 성공했습니다.");

        private final String code;
        private final String message;

        TestResponseCode(String code, String message) {
            this.code = code;
            this.message = message;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }
}

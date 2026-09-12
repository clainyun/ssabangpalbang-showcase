package com.ssafy.ssabangpalbang.global.error;

import com.ssafy.ssabangpalbang.community.dto.request.PostCreateRequest;
import com.ssafy.ssabangpalbang.fieldvisit.dto.request.FieldVisitStartRequestBody;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.global.response.ResponseCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(
            BusinessException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(
                        withMessage(errorCode, exception.getMessage()),
                        exception.getData()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        if (isPostAttachmentLimitExceeded(exception)) {
            ErrorCode errorCode = ErrorCode.POST_ATTACHMENT_LIMIT_EXCEEDED;
            return ResponseEntity
                    .status(errorCode.getStatus())
                    .body(ApiResponse.failure(errorCode));
        }

        ValidationError validationError = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .min(Comparator.comparing(FieldError::getField))
                .map(fieldError -> new ValidationError(
                        fieldError.getField(),
                        Objects.requireNonNullElse(
                                fieldError.getDefaultMessage(),
                                "잘못된 값입니다."
                        )
                ))
                .orElse(null);

        if (isFieldVisitLocationInvalid(exception, validationError)) {
            ErrorCode errorCode = ErrorCode.FIELD_VISIT_LOCATION_INVALID;
            return ResponseEntity
                    .status(errorCode.getStatus())
                    .body(ApiResponse.failure(errorCode, validationError));
        }

        return invalidInputValue(validationError);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception
    ) {
        if (exception.isForReturnValue()) {
            return handleUnexpectedException(exception);
        }

        ValidationError validationError = exception.getParameterValidationResults()
                .stream()
                .min(Comparator.comparing(result -> parameterName(
                        result.getMethodParameter()
                )))
                .map(result -> new ValidationError(
                        parameterName(result.getMethodParameter()),
                        result.getResolvableErrors()
                                .stream()
                                .map(MessageSourceResolvable::getDefaultMessage)
                                .filter(Objects::nonNull)
                                .findFirst()
                                .orElse("요청 값이 올바르지 않습니다.")
                ))
                .orElse(null);

        return invalidInputValue(validationError);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        return invalidInputValue(new ValidationError(
                exception.getName(),
                "요청 파라미터 값이 올바르지 않습니다."
        ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingRequestParameter(
            MissingServletRequestParameterException exception
    ) {
        return invalidInputValue(new ValidationError(
                exception.getParameterName(),
                "필수 요청 파라미터입니다."
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        ValidationError validationError = exception.getConstraintViolations()
                .stream()
                .min(Comparator.comparing(violation -> violation.getPropertyPath()
                        .toString()))
                .map(violation -> new ValidationError(
                        lastPathSegment(violation),
                        violation.getMessage()
                ))
                .orElse(null);

        return invalidInputValue(validationError);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnreadableMessage(
            HttpMessageNotReadableException exception
    ) {
        return invalidInputValue(null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception
    ) {
        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(errorCode));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFound(
            NoResourceFoundException exception
    ) {
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpectedException(
            Exception exception
    ) {
        log.error("처리되지 않은 서버 오류가 발생했습니다.", exception);

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(errorCode));
    }

    private ResponseCode withMessage(
            ResponseCode responseCode,
            String message
    ) {
        return new ResponseCode() {
            @Override
            public String getCode() {
                return responseCode.getCode();
            }

            @Override
            public String getMessage() {
                return message;
            }
        };
    }

    private ResponseEntity<ApiResponse<Object>> invalidInputValue(
            ValidationError validationError
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(errorCode, validationError));
    }

    private String parameterName(MethodParameter methodParameter) {
        RequestParam requestParam = methodParameter.getParameterAnnotation(
                RequestParam.class
        );
        if (requestParam != null) {
            return annotationValue(
                    requestParam.name(),
                    requestParam.value(),
                    methodParameter.getParameterName()
            );
        }

        PathVariable pathVariable = methodParameter.getParameterAnnotation(
                PathVariable.class
        );
        if (pathVariable != null) {
            return annotationValue(
                    pathVariable.name(),
                    pathVariable.value(),
                    methodParameter.getParameterName()
            );
        }

        return Objects.requireNonNullElse(methodParameter.getParameterName(), "");
    }

    private String annotationValue(
            String name,
            String value,
            String fallback
    ) {
        if (!name.isBlank()) {
            return name;
        }
        if (!value.isBlank()) {
            return value;
        }

        return Objects.requireNonNullElse(fallback, "");
    }

    private String lastPathSegment(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        int lastDotIndex = propertyPath.lastIndexOf('.');

        return lastDotIndex >= 0
                ? propertyPath.substring(lastDotIndex + 1)
                : propertyPath;
    }

    private boolean isPostAttachmentLimitExceeded(
            MethodArgumentNotValidException exception
    ) {
        return exception.getBindingResult().getTarget()
                instanceof PostCreateRequest
                && exception.getBindingResult()
                .getFieldErrors("fileIds")
                .stream()
                .anyMatch(error -> "Size".equals(error.getCode()));
    }

    private static final Set<String> FIELD_VISIT_LOCATION_FIELDS =
            Set.of("latitude", "longitude");

    private boolean isFieldVisitLocationInvalid(
            MethodArgumentNotValidException exception,
            ValidationError validationError
    ) {
        return exception.getBindingResult().getTarget()
                instanceof FieldVisitStartRequestBody
                && validationError != null
                && FIELD_VISIT_LOCATION_FIELDS.contains(validationError.field());
    }
}

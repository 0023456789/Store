package org.example.storevn.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ConstraintViolation;
import lombok.extern.slf4j.Slf4j;
import org.example.storevn.dto.response.ApiResponse;
import org.example.storevn.dto.response.FieldErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.FieldError;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Object>> handleAppException(AppException exception, HttpServletRequest request) {
        ErrorCode error = exception.getErrorCode();
        logApiFailure(request, error, error.getMessage());
        ApiResponse<Object> body = ApiResponse.<Object>builder()
                .status(error.getStatusCode().value())
                .code(error.getCode())
                .message(exception.getMessage())
                .result(exception.getDetails())
                .build();
        return ResponseEntity.status(error.getStatusCode()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorResponse>>> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::fieldErrorResponse).toList();
        log.warn("API validation failed: method={} path={} fields={}", request.getMethod(), request.getRequestURI(),
                fieldErrors.stream().map(FieldErrorResponse::getField).distinct().toList());
        FieldErrorResponse first = fieldErrors.isEmpty()
                ? FieldErrorResponse.builder().field("request").code(ErrorCode.INVALID_KEY.getCode())
                    .message(ErrorCode.INVALID_KEY.getMessage()).build()
                : fieldErrors.getFirst();
        ApiResponse<List<FieldErrorResponse>> body = ApiResponse.<List<FieldErrorResponse>>builder()
                .status(400).code(first.getCode()).message(first.getMessage()).result(fieldErrors).build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidInput(Exception exception, HttpServletRequest request) {
        log.warn("API input could not be parsed: method={} path={} exception={}",
                request.getMethod(), request.getRequestURI(), exception.getClass().getSimpleName());
        return error(ErrorCode.INVALID_KEY, ErrorCode.INVALID_KEY.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled API error: method={} path={} exception={} message={}", request.getMethod(),
                request.getRequestURI(), exception.getClass().getName(), exception.getMessage(), exception);
        ErrorCode error = ErrorCode.UNCATEGORIZED_EXCEPTION;
        return error(error, error.getMessage());
    }

    private ErrorCode resolve(String key) {
        if (key == null) return ErrorCode.INVALID_KEY;
        try { return ErrorCode.valueOf(key); }
        catch (IllegalArgumentException ignored) { return ErrorCode.INVALID_KEY; }
    }

    private FieldErrorResponse fieldErrorResponse(FieldError fieldError) {
        ErrorCode errorCode = resolve(fieldError.getDefaultMessage());
        String message = errorCode.getMessage();
        try {
            ConstraintViolation<?> violation = fieldError.unwrap(ConstraintViolation.class);
            Map<String, Object> attributes = violation.getConstraintDescriptor().getAttributes();
            for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
                message = message.replace("{" + attribute.getKey() + "}", String.valueOf(attribute.getValue()));
            }
        } catch (RuntimeException ignored) {}
        return FieldErrorResponse.builder().field(fieldError.getField()).code(errorCode.getCode())
                .message(message).build();
    }

    private ResponseEntity<ApiResponse<Void>> error(ErrorCode errorCode, String message) {
        ApiResponse<Void> body = ApiResponse.<Void>builder().status(errorCode.getStatusCode().value())
                .code(errorCode.getCode()).message(message).build();
        return ResponseEntity.status(errorCode.getStatusCode()).body(body);
    }

    private void logApiFailure(HttpServletRequest request, ErrorCode error, String message) {
        if (error.getStatusCode().is5xxServerError()) {
            log.error("API request failed: method={} path={} status={} errorCode={} message={}",
                    request.getMethod(), request.getRequestURI(), error.getStatusCode().value(), error.getCode(), message);
            return;
        }
        log.warn("API request rejected: method={} path={} status={} errorCode={} message={}",
                request.getMethod(), request.getRequestURI(), error.getStatusCode().value(), error.getCode(), message);
    }
}

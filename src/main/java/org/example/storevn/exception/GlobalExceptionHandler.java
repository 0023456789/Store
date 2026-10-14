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

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Object>> handleAppException(AppException exception) {
        ErrorCode error = exception.getErrorCode();
        ApiResponse<Object> body = ApiResponse.<Object>builder()
                .code(error.getCode())
                .message(exception.getMessage())
                .result(exception.getDetails())
                .build();
        return ResponseEntity.status(error.getStatusCode()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<FieldErrorResponse>>> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::fieldErrorResponse).toList();
        FieldErrorResponse first = fieldErrors.isEmpty()
                ? FieldErrorResponse.builder().field("request").code(ErrorCode.INVALID_KEY.getCode())
                    .message(ErrorCode.INVALID_KEY.getMessage()).build()
                : fieldErrors.getFirst();
        ApiResponse<List<FieldErrorResponse>> body = ApiResponse.<List<FieldErrorResponse>>builder()
                .code(first.getCode()).message(first.getMessage()).result(fieldErrors).build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidInput(Exception exception) {
        log.debug("Invalid request: {}", exception.getMessage());
        return error(ErrorCode.INVALID_KEY, ErrorCode.INVALID_KEY.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled {}: {}", exception.getClass().getName(), exception.getMessage(), exception);
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
        ApiResponse<Void> body = ApiResponse.<Void>builder().code(errorCode.getCode()).message(message).build();
        return ResponseEntity.status(errorCode.getStatusCode()).body(body);
    }
}

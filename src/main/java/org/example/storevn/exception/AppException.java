package org.example.storevn.exception;

import java.util.Map;

public class AppException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, ?> details;

    public AppException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null);
    }

    public AppException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public AppException(ErrorCode errorCode, Map<String, ?> details) {
        this(errorCode, errorCode.getMessage(), details);
    }

    private AppException(ErrorCode errorCode, String message, Map<String, ?> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public Map<String, ?> getDetails() { return details; }
}

package org.example.storevn.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "Invalid request", HttpStatus.BAD_REQUEST),

    // ── Catalog ──
    PLAN_NOT_FOUND(2001, "Plan not found", HttpStatus.NOT_FOUND),
    PLAN_INACTIVE(2002, "Plan is not available for purchase", HttpStatus.UNPROCESSABLE_ENTITY),
    CATALOG_UNAVAILABLE(2003, "Plan catalog is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),

    // ── Order ──
    ORDER_NOT_FOUND(2101, "Order not found", HttpStatus.NOT_FOUND),
    ORDER_EXPIRED(2102, "Order has expired", HttpStatus.UNPROCESSABLE_ENTITY),
    ORDER_ALREADY_PAID(2103, "Order is already paid", HttpStatus.CONFLICT),
    ORDER_PHONE_MISMATCH(2104, "Phone number does not match this order", HttpStatus.FORBIDDEN),
    ORDER_CANNOT_PAY(2105, "Order is not in a payable state", HttpStatus.UNPROCESSABLE_ENTITY),
    MSISDN_INVALID(2106, "Subscriber number is invalid", HttpStatus.BAD_REQUEST),
    CUSTOMER_NAME_INVALID(2107, "Customer name is required", HttpStatus.BAD_REQUEST),
    CUSTOMER_PHONE_INVALID(2108, "Customer phone is invalid", HttpStatus.BAD_REQUEST),

    // ── Payment / VNPAY ──
    PAYMENT_SIGNATURE_INVALID(2201, "Payment signature verification failed", HttpStatus.BAD_REQUEST),
    PAYMENT_AMOUNT_MISMATCH(2202, "Payment amount does not match", HttpStatus.BAD_REQUEST),
    PAYMENT_ALREADY_CONFIRMED(2203, "Payment has already been confirmed", HttpStatus.CONFLICT),
    PAYMENT_CREATE_FAILED(2204, "Failed to create payment", HttpStatus.INTERNAL_SERVER_ERROR),
    VNPAY_CONFIG_MISSING(2205, "VNPAY configuration is incomplete", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus statusCode;

    ErrorCode(int code, String message, HttpStatus statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
